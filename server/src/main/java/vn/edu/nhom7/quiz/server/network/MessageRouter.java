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
  private Executor mediaServices;
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
    this.mediaServices = services;
    this.timer = timer;
  }

  public void configureMediaExecutor(Executor executor) {
    this.mediaServices = executor;
  }

  public void route(Connection connection, Envelope request) {
    try {
      if (PayloadRegistry.direction(request.type()) != PayloadRegistry.Direction.CLIENT_TO_SERVER)
        throw new ProtocolException("INVALID_MESSAGE", "Server event cannot be sent as a command");
      if (!connection.handshaken) {
        handshake.validateFirst(request, connection.connectedAt, System.nanoTime());
        connection.handshaken = true;
        reply(
            connection,
            request,
            MessageType.HELLO_ACK,
            new Payloads.HelloAck(connection.id, 1, System.currentTimeMillis(), 10000, "1"));
        return;
      }
      if (request.type() == MessageType.HELLO)
        throw new ProtocolException("INVALID_STATE", "Handshake already completed");
      byte[] fingerprint = fingerprint(connection, request);
      var cached = connection.requests.find(request.requestId(), fingerprint, System.nanoTime());
      if (cached.isPresent()) {
        connections.tryEnqueue(connection.id, cached.get());
        return;
      }
      if (!connection.requests.begin(request.requestId(), fingerprint, System.nanoTime())) return;
      if (request.type() == MessageType.PING) {
        reply(
            connection,
            request,
            MessageType.PONG,
            new Payloads.Pong(
                request.payload().path("clientTimeMs").asLong(), System.currentTimeMillis()));
        return;
      }
      if (request.type() == MessageType.LOGIN || request.type() == MessageType.REGISTER) {
        if (sessions.authenticated(connection.id).isPresent())
          throw new ProtocolException("INVALID_STATE", "Already authenticated");
        submit(connection, request, () -> authenticate(connection, request));
        return;
      }
      var caller =
          sessions
              .authenticated(connection.id)
              .orElseThrow(() -> new ProtocolException("UNAUTHENTICATED", "Login required"));
      routeAuthenticated(connection, request, caller);
    } catch (ProtocolException ex) {
      error(connection, request, ex.code(), ex.getMessage(), false, ex.details());
    } catch (ServiceException ex) {
      error(connection, request, ex.code(), ex.getMessage(), retryable(ex));
    } catch (Exception ex) {
      error(connection, request, "INTERNAL_ERROR", "Command could not be completed", true);
    }
  }

  private void routeAuthenticated(Connection connection, Envelope request, SessionContext caller) {
    switch (request.type()) {
      case LOGOUT -> {
        logout.accept(caller);
        sessions.removeConnection(connection.id);
        presence.broadcast();
        reply(connection, request, MessageType.LOGOUT_ACK, new Payloads.LogoutAck());
        timer.schedule(() -> connections.close(connection.id), 100, TimeUnit.MILLISECONDS);
      }
      case ONLINE_LIST_REQUEST -> {
        if (!connection.queries.allow(System.nanoTime()))
          throw new ProtocolException("RATE_LIMITED", "Too many queries");
        reply(connection, request, MessageType.ONLINE_LIST, presence.snapshot());
      }
      case CHALLENGE, CHALLENGE_ACCEPT, CHALLENGE_REJECT, CHALLENGE_CANCEL ->
          challenges.accept(caller, request);
      case MATCH_READY,
          QUESTION_READY,
          ANSWER,
          LIFELINE_USE,
          CHAT,
          REMATCH_REQUEST,
          REMATCH_RESPONSE,
          EXIT_MATCH,
          LIVE_REVIEW_REQUEST,
          MATCH_SNAPSHOT_REQUEST -> {
        matches.handle(caller, request);
        presence.broadcast();
      }
      default -> {
        routeQuery(connection, request, caller);
      }
    }
  }

  private void routeQuery(Connection connection, Envelope request, SessionContext caller) {
    boolean media =
        request.type() == MessageType.MEDIA_UPLOAD_START
            || request.type() == MessageType.MEDIA_UPLOAD_CHUNK
            || request.type() == MessageType.MEDIA_REQUEST;
    if (!(media ? connection.mediaRequests : connection.queries).allow(System.nanoTime()))
      throw new ProtocolException("RATE_LIMITED", "Too many queries");
    submit(
        connection,
        request,
        media ? mediaServices : services,
        () -> {
          Object payload = queries.execute(caller, request);
          if (connections.isAlive(connection.id)
              && sessions.authenticated(connection.id).filter(s -> s.equals(caller)).isPresent())
            reply(connection, request, responseType(request.type()), payload);
        });
  }

  private void authenticate(Connection connection, Envelope request) {
    JsonNode payload = request.payload();
    String ip = connection.socket.getInetAddress().getHostAddress();
    var ipLimit = ipFailures.computeIfAbsent(ip, k -> new RateLimiter(20, 60_000_000_000L));
    if (!connection.authFailures.available(System.nanoTime())
        || !ipLimit.available(System.nanoTime()))
      throw new ProtocolException("RATE_LIMITED", "Too many failed authentication attempts");
    try {
      if (request.type() == MessageType.REGISTER) {
        var user =
            auth.register(
                payload.path("username").asText(),
                payload.path("displayName").asText(),
                payload.path("password").asText(),
                payload.path("avatarId").asText());
        if (connections.isAlive(connection.id))
          reply(
              connection,
              request,
              MessageType.REGISTER_RESULT,
              new Payloads.RegisterResult(user.userId(), user.username()));
      } else {
        var profile =
            auth.login(payload.path("username").asText(), payload.path("password").asText());
        if (!connections.isAlive(connection.id)) return;
        if (!sessions.tryAuthenticate(connection.id, profile.userId()))
          throw new ProtocolException("ALREADY_LOGGED_IN", "Account already online");
        if (!connections.isAlive(connection.id)) {
          sessions.removeConnection(connection.id);
          return;
        }
        presence.cache(profile);
        reply(connection, request, MessageType.LOGIN_RESULT, new Payloads.LoginResult(profile));
        presence.broadcast();
      }
    } catch (ServiceException failure) {
      boolean allow =
          connection.authFailures.allow(System.nanoTime())
              && ipFailures
                  .computeIfAbsent(ip, k -> new RateLimiter(20, 60_000_000_000L))
                  .allow(System.nanoTime());
      if (!allow)
        throw new ProtocolException("RATE_LIMITED", "Too many failed authentication attempts");
      throw failure;
    }
  }

  private void submit(Connection connection, Envelope request, Runnable task) {
    submit(connection, request, services, task);
  }

  private void submit(Connection connection, Envelope request, Executor executor, Runnable task) {
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } catch (ServiceException ex) {
              error(connection, request, ex.code(), ex.getMessage(), retryable(ex));
            } catch (ProtocolException ex) {
              error(connection, request, ex.code(), ex.getMessage(), false, ex.details());
            } catch (Exception ex) {
              error(connection, request, "DB_UNAVAILABLE", "Service unavailable", true);
            }
          });
    } catch (RejectedExecutionException ex) {
      throw new ProtocolException("SERVER_BUSY", "Service queue is full");
    }
  }

  private static boolean retryable(ServiceException failure) {
    return failure.code().equals("DB_UNAVAILABLE") || failure.code().equals("SERVER_BUSY");
  }

  public void malformed(Connection connection, ProtocolException ex) {
    error(connection, null, ex.code(), ex.getMessage(), false);
    if (ex.code().equals("UNSUPPORTED_PROTOCOL"))
      timer.schedule(() -> connections.close(connection.id), 100, TimeUnit.MILLISECONDS);
  }

  private void error(
      Connection connection, Envelope request, String code, String message, boolean retry) {
    error(connection, request, code, message, retry, codec.mapper().createObjectNode());
  }

  private void error(
      Connection connection,
      Envelope request,
      String code,
      String message,
      boolean retry,
      JsonNode details) {
    reply(
        connection, request, MessageType.ERROR, new Payloads.Error(code, message, retry, details));
    if (code.equals("ASSET_VERSION_MISMATCH") || code.equals("UNSUPPORTED_PROTOCOL"))
      timer.schedule(() -> connections.close(connection.id), 100, TimeUnit.MILLISECONDS);
  }

  private void reply(Connection connection, Envelope request, MessageType type, Object payload) {
    connections.tryEnqueue(
        connection.id,
        codec.envelope(
            type,
            request == null ? null : request.requestId(),
            request == null ? null : request.matchId(),
            request == null ? null : request.roundId(),
            null,
            payload));
  }

  private byte[] fingerprint(Connection connection, Envelope request) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(connection.fingerprintKey, "HmacSHA256"));
      var normalized =
          codec.envelope(
              request.type(),
              request.requestId(),
              request.matchId(),
              request.roundId(),
              null,
              codec.payload(request, PayloadRegistry.payloadClass(request.type())));
      return mac.doFinal(codec.encode(normalized));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static MessageType responseType(MessageType type) {
    return switch (type) {
      case AUTHOR_REQUEST -> MessageType.AUTHOR_RESULT;
      case MEDIA_UPLOAD_START -> MessageType.MEDIA_UPLOAD_STARTED;
      case MEDIA_UPLOAD_CHUNK -> MessageType.MEDIA_UPLOAD_ACK;
      case MEDIA_REQUEST -> MessageType.MEDIA_CHUNK;
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
