package vn.edu.nhom7.quiz.server.network;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.auth.*;
import vn.edu.nhom7.quiz.server.match.MatchGateway;
import vn.edu.nhom7.quiz.server.session.*;

public final class MessageRouter {
  @FunctionalInterface
  public interface Queries {
    Object execute(SessionContext caller, Envelope request);
  }

  private final ConnectionManager connections;
  private final SessionRegistry sessions;
  private final OnlinePresenceService presence;
  private final AuthService auth;
  private final MatchGateway matches;
  private final BiConsumer<SessionContext, Envelope> challenges;
  private final Consumer<SessionContext> logout;
  private final Queries queries;
  private final Executor services;
  private final ScheduledExecutorService timer;
  private final ProtocolCodec codec = new ProtocolCodec();
  private final HandshakeValidator handshake = new HandshakeValidator();
  private final ConcurrentHashMap<String, RateLimiter> ipFailures = new ConcurrentHashMap<>();

  public MessageRouter(
      ConnectionManager connections,
      SessionRegistry sessions,
      OnlinePresenceService presence,
      AuthService auth,
      MatchGateway matches,
      BiConsumer<SessionContext, Envelope> challenges,
      Consumer<SessionContext> logout,
      Queries queries,
      Executor services,
      ScheduledExecutorService timer) {
    this.connections = connections;
    this.sessions = sessions;
    this.presence = presence;
    this.auth = auth;
    this.matches = matches;
    this.challenges = challenges;
    this.logout = logout;
    this.queries = queries;
    this.services = services;
    this.timer = timer;
  }

  public void route(Connection c, Envelope e) {
    try {
      if (PayloadRegistry.direction(e.type()) != PayloadRegistry.Direction.CLIENT_TO_SERVER)
        throw new ProtocolException("INVALID_MESSAGE", "Server event cannot be sent as a command");
      if (!c.handshaken) {
        handshake.validateFirst(e, c.connectedAt, System.nanoTime());
        c.handshaken = true;
        reply(
            c,
            e,
            MessageType.HELLO_ACK,
            new Payloads.HelloAck(c.id, 1, System.currentTimeMillis(), 10000, "1"));
        return;
      }
      if (e.type() == MessageType.HELLO)
        throw new ProtocolException("INVALID_STATE", "Handshake already completed");
      byte[] fingerprint = fingerprint(c, e);
      var cached = c.requests.find(e.requestId(), fingerprint, System.nanoTime());
      if (cached.isPresent()) {
        connections.tryEnqueue(c.id, cached.get());
        return;
      }
      if (!c.requests.begin(e.requestId(), fingerprint, System.nanoTime())) return;
      if (e.type() == MessageType.PING) {
        reply(
            c,
            e,
            MessageType.PONG,
            new Payloads.Pong(
                e.payload().path("clientTimeMs").asLong(), System.currentTimeMillis()));
        return;
      }
      if (e.type() == MessageType.LOGIN || e.type() == MessageType.REGISTER) {
        if (sessions.authenticated(c.id).isPresent())
          throw new ProtocolException("INVALID_STATE", "Already authenticated");
        submit(c, e, () -> authenticate(c, e));
        return;
      }
      var caller =
          sessions
              .authenticated(c.id)
              .orElseThrow(() -> new ProtocolException("UNAUTHENTICATED", "Login required"));
      switch (e.type()) {
        case LOGOUT -> {
          logout.accept(caller);
          sessions.removeConnection(c.id);
          presence.broadcast();
          reply(c, e, MessageType.LOGOUT_ACK, new Payloads.LogoutAck());
          timer.schedule(() -> connections.close(c.id), 100, TimeUnit.MILLISECONDS);
        }
        case ONLINE_LIST_REQUEST -> {
          if (!c.queries.allow(System.nanoTime()))
            throw new ProtocolException("RATE_LIMITED", "Too many queries");
          reply(c, e, MessageType.ONLINE_LIST, presence.snapshot());
        }
        case CHALLENGE, CHALLENGE_ACCEPT, CHALLENGE_REJECT, CHALLENGE_CANCEL ->
            challenges.accept(caller, e);
        case MATCH_READY,
            QUESTION_READY,
            ANSWER,
            CHAT,
            REMATCH_REQUEST,
            REMATCH_RESPONSE,
            EXIT_MATCH,
            MATCH_SNAPSHOT_REQUEST -> {
          matches.handle(caller, e);
          presence.broadcast();
        }
        default -> {
          if (!c.queries.allow(System.nanoTime()))
            throw new ProtocolException("RATE_LIMITED", "Too many queries");
          submit(
              c,
              e,
              () -> {
                Object payload = queries.execute(caller, e);
                if (connections.isAlive(c.id)
                    && sessions.authenticated(c.id).filter(s -> s.equals(caller)).isPresent())
                  reply(c, e, responseType(e.type()), payload);
              });
        }
      }
    } catch (ProtocolException ex) {
      error(c, e, ex.code(), ex.getMessage(), false, ex.details());
    } catch (ServiceException ex) {
      error(
          c,
          e,
          ex.code(),
          ex.getMessage(),
          ex.code().equals("DB_UNAVAILABLE") || ex.code().equals("SERVER_BUSY"));
    } catch (Exception ex) {
      error(c, e, "INTERNAL_ERROR", "Command could not be completed", true);
    }
  }

  private void authenticate(Connection c, Envelope e) {
    JsonNode p = e.payload();
    String ip = c.socket.getInetAddress().getHostAddress();
    var ipLimit = ipFailures.computeIfAbsent(ip, k -> new RateLimiter(20, 60_000_000_000L));
    if (!c.authFailures.available(System.nanoTime()) || !ipLimit.available(System.nanoTime()))
      throw new ProtocolException("RATE_LIMITED", "Too many failed authentication attempts");
    try {
      if (e.type() == MessageType.REGISTER) {
        var user =
            auth.register(
                p.path("username").asText(),
                p.path("displayName").asText(),
                p.path("password").asText(),
                p.path("avatarId").asText());
        if (connections.isAlive(c.id))
          reply(
              c,
              e,
              MessageType.REGISTER_RESULT,
              new Payloads.RegisterResult(user.userId(), user.username()));
      } else {
        var profile = auth.login(p.path("username").asText(), p.path("password").asText());
        if (!connections.isAlive(c.id)) return;
        if (!sessions.tryAuthenticate(c.id, profile.userId()))
          throw new ProtocolException("ALREADY_LOGGED_IN", "Account already online");
        if (!connections.isAlive(c.id)) {
          sessions.removeConnection(c.id);
          return;
        }
        presence.cache(profile);
        reply(c, e, MessageType.LOGIN_RESULT, new Payloads.LoginResult(profile));
        presence.broadcast();
      }
    } catch (ServiceException failure) {
      boolean allow =
          c.authFailures.allow(System.nanoTime())
              && ipFailures
                  .computeIfAbsent(ip, k -> new RateLimiter(20, 60_000_000_000L))
                  .allow(System.nanoTime());
      if (!allow)
        throw new ProtocolException("RATE_LIMITED", "Too many failed authentication attempts");
      throw failure;
    }
  }

  private void submit(Connection c, Envelope e, Runnable task) {
    try {
      services.execute(
          () -> {
            try {
              task.run();
            } catch (ServiceException ex) {
              error(
                  c,
                  e,
                  ex.code(),
                  ex.getMessage(),
                  ex.code().equals("DB_UNAVAILABLE") || ex.code().equals("SERVER_BUSY"));
            } catch (ProtocolException ex) {
              error(c, e, ex.code(), ex.getMessage(), false, ex.details());
            } catch (Exception ex) {
              error(c, e, "DB_UNAVAILABLE", "Service unavailable", true);
            }
          });
    } catch (RejectedExecutionException ex) {
      throw new ProtocolException("SERVER_BUSY", "Service queue is full");
    }
  }

  public void malformed(Connection c, ProtocolException ex) {
    error(c, null, ex.code(), ex.getMessage(), false);
    if (ex.code().equals("UNSUPPORTED_PROTOCOL"))
      timer.schedule(() -> connections.close(c.id), 100, TimeUnit.MILLISECONDS);
  }

  private void error(Connection c, Envelope e, String code, String message, boolean retry) {
    error(c, e, code, message, retry, codec.mapper().createObjectNode());
  }

  private void error(
      Connection c, Envelope e, String code, String message, boolean retry, JsonNode details) {
    reply(c, e, MessageType.ERROR, new Payloads.Error(code, message, retry, details));
    if (code.equals("ASSET_VERSION_MISMATCH") || code.equals("UNSUPPORTED_PROTOCOL"))
      timer.schedule(() -> connections.close(c.id), 100, TimeUnit.MILLISECONDS);
  }

  private void reply(Connection c, Envelope request, MessageType type, Object payload) {
    connections.tryEnqueue(
        c.id,
        codec.envelope(
            type,
            request == null ? null : request.requestId(),
            request == null ? null : request.matchId(),
            request == null ? null : request.roundId(),
            null,
            payload));
  }

  private byte[] fingerprint(Connection c, Envelope e) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(c.fingerprintKey, "HmacSHA256"));
      var normalized =
          codec.envelope(
              e.type(),
              e.requestId(),
              e.matchId(),
              e.roundId(),
              null,
              codec.payload(e, PayloadRegistry.payloadClass(e.type())));
      return mac.doFinal(codec.encode(normalized));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static MessageType responseType(MessageType t) {
    return switch (t) {
      case QUIZ_LIST_REQUEST -> MessageType.QUIZ_LIST;
      case QUIZ_DETAIL_REQUEST -> MessageType.QUIZ_DETAIL;
      case PROFILE_REQUEST -> MessageType.PROFILE;
      case RANKING_REQUEST -> MessageType.RANKING;
      case HISTORY_REQUEST -> MessageType.HISTORY;
      case MATCH_DETAIL_REQUEST -> MessageType.MATCH_DETAIL;
      default -> throw new ProtocolException("INVALID_MESSAGE", "Unsupported command");
    };
  }
}
