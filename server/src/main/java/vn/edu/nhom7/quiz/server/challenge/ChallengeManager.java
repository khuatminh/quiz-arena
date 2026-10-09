package vn.edu.nhom7.quiz.server.challenge;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.match.*;
import vn.edu.nhom7.quiz.server.network.*;
import vn.edu.nhom7.quiz.server.persistence.ResultSink;
import vn.edu.nhom7.quiz.server.quiz.QuizRepository;
import vn.edu.nhom7.quiz.server.session.*;

public final class ChallengeManager {
  private final Map<UUID, Challenge> challenges = new HashMap<>();
  private final SessionRegistry sessions;
  private final OnlinePresenceService presence;
  private final MatchManager matches;
  private final QuizRepository quizzes;
  private final ResultSink sink;
  private final OutboundTransport transport;
  private final GameClock clock;
  private final GameScheduler scheduler;
  private final Executor executor;
  private final LongFunction<Payloads.QuizSummary> summaries;
  private final ProtocolCodec codec = new ProtocolCodec();
  private boolean closed;

  public ChallengeManager(
      SessionRegistry sessions,
      OnlinePresenceService presence,
      MatchManager matches,
      QuizRepository quizzes,
      ResultSink sink,
      OutboundTransport transport,
      GameClock clock,
      GameScheduler scheduler,
      Executor executor,
      LongFunction<Payloads.QuizSummary> summaries) {
    this.sessions = sessions;
    this.presence = presence;
    this.matches = matches;
    this.quizzes = quizzes;
    this.sink = sink;
    this.transport = transport;
    this.clock = clock;
    this.scheduler = scheduler;
    this.executor = executor;
    this.summaries = summaries;
  }

  public void handle(SessionContext caller, Envelope request) {
    if (request.type() == MessageType.CHALLENGE) {
      submit(caller, request, () -> invite(caller, request));
      return;
    }
    UUID id = UUID.fromString(request.payload().path("challengeId").asText());
    Challenge c;
    synchronized (this) {
      c = challenges.get(id);
      if (c == null)
        throw new ProtocolException("CHALLENGE_NOT_FOUND", "Invitation no longer exists");
      if (c.state != Challenge.State.PENDING)
        throw new ProtocolException("INVALID_STATE", "Invitation already handled");
      if (clock.nanoTime() >= c.expiresNano) {
        finish(c, Challenge.State.EXPIRED, "EXPIRED", request.requestId());
        return;
      }
      if (request.type() == MessageType.CHALLENGE_CANCEL) {
        if (!caller.equals(c.sender))
          throw new ProtocolException("INVALID_STATE", "Only sender may cancel");
        finish(c, Challenge.State.CANCELLED, "CANCELLED", request.requestId());
        return;
      }
      if (!caller.equals(c.recipient))
        throw new ProtocolException("INVALID_STATE", "Only recipient may respond");
      if (request.type() == MessageType.CHALLENGE_REJECT) {
        finish(c, Challenge.State.REJECTED, "REJECTED", request.requestId());
        return;
      }
      if (closed || !sink.canCreateMatch())
        throw new ProtocolException("DB_UNAVAILABLE", "New matches unavailable");
      c.state = Challenge.State.CREATING_MATCH;
      c.newMatchId = UUID.randomUUID();
      if (!sink.reserveMatch(c.newMatchId)) {
        finish(c, Challenge.State.INVALIDATED, "SERVER_BUSY", request.requestId());
        return;
      }
    }
    scheduler.schedule(
        Duration.ofSeconds(5),
        () -> {
          synchronized (this) {
            if (c.state == Challenge.State.CREATING_MATCH)
              finish(c, Challenge.State.INVALIDATED, "LOAD_TIMEOUT", request.requestId());
          }
        });
    submit(caller, request, () -> create(c, request));
  }

  private void invite(SessionContext caller, Envelope request) {
    long target = request.payload().path("targetUserId").asLong(),
        quizId = request.payload().path("quizId").asLong();
    if (target == caller.userId())
      throw new ProtocolException("INVALID_INPUT", "Choose another player");
    var summary = summaries.apply(quizId);
    if (!summary.availability().equals("AVAILABLE"))
      throw new ProtocolException("QUIZ_UNAVAILABLE", "Quiz is unavailable");
    Challenge c;
    synchronized (this) {
      if (closed || !sink.canCreateMatch())
        throw new ProtocolException("DB_UNAVAILABLE", "New matches unavailable");
      if (sessions.authenticated(caller.connectionId()).filter(caller::equals).isEmpty()) return;
      var recipient =
          sessions
              .user(target)
              .orElseThrow(() -> new ProtocolException("USER_OFFLINE", "Opponent is offline"));
      c =
          new Challenge(
              caller,
              recipient,
              summary,
              clock.instant().toEpochMilli() + 20000,
              clock.nanoTime() + 20_000_000_000L);
      if (!sessions.reservePair(caller.userId(), target, c.id))
        throw new ProtocolException("USER_BUSY", "Player is busy");
      challenges.put(c.id, c);
    }
    synchronized (this) {
      if (c.state != Challenge.State.PENDING) return;
      scheduler.schedule(
          Duration.ofSeconds(20),
          () -> {
            synchronized (this) {
              if (c.state == Challenge.State.PENDING)
                finish(c, Challenge.State.EXPIRED, "EXPIRED", null);
            }
          });
      scheduler.schedule(
          Duration.ofSeconds(60),
          () -> {
            synchronized (this) {
              if (c.state != Challenge.State.PENDING && c.state != Challenge.State.CREATING_MATCH)
                challenges.remove(c.id, c);
            }
          });
      send(
          c.sender,
          MessageType.CHALLENGE_ACK,
          request.requestId(),
          new Payloads.ChallengeAck(c.id, target, summary, c.expiresAt));
      send(
          c.recipient,
          MessageType.CHALLENGE_RECEIVED,
          null,
          new Payloads.ChallengeReceived(
              c.id, presence.profile(caller.userId()), summary, c.expiresAt));
      presence.broadcast();
    }
  }

  private void create(Challenge c, Envelope request) {
    try {
      var questions =
          quizzes.loadMatchQuestions(
              c.quiz.quizId(), java.util.random.RandomGenerator.getDefault());
      var match = matches.prepare(c.newMatchId, c.quiz, List.of(c.sender, c.recipient), questions);
      matches.registerPrepared(match);
      synchronized (this) {
        if (c.state != Challenge.State.CREATING_MATCH) {
          matches.discardPrepared(match);
          return;
        }
        if (sessions.authenticated(c.sender.connectionId()).filter(c.sender::equals).isEmpty()
            || sessions
                .authenticated(c.recipient.connectionId())
                .filter(c.recipient::equals)
                .isEmpty()
            || !sessions.attachReservedPair(c.id, c.newMatchId)) {
          matches.discardPrepared(match);
          finish(c, Challenge.State.INVALIDATED, "DISCONNECTED", request.requestId());
          return;
        }
        c.state = Challenge.State.ACCEPTED;
      }
      try {
        matches.activate(match);
        sendClosed(c, "ACCEPTED", "ACCEPTED", request.requestId());
        presence.broadcast();
      } catch (Exception e) {
        matches.discardPrepared(match);
        sessions.detach(c.sender.userId(), c.newMatchId);
        sessions.detach(c.recipient.userId(), c.newMatchId);
        sink.cancelReservation(c.newMatchId);
        sendClosed(c, "INVALIDATED", "INTERNAL_ERROR", request.requestId());
        presence.broadcast();
      }
    } catch (Exception e) {
      synchronized (this) {
        if (c.state == Challenge.State.CREATING_MATCH)
          finish(
              c,
              Challenge.State.INVALIDATED,
              e instanceof ServiceException s ? s.code() : "LOAD_FAILED",
              request.requestId());
      }
    }
  }

  private void submit(SessionContext caller, Envelope request, Runnable task) {
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } catch (ProtocolException e) {
              sendError(caller, request, e.code(), e.getMessage());
            } catch (ServiceException e) {
              sendError(caller, request, e.code(), e.getMessage());
            } catch (Exception e) {
              sendError(caller, request, "INTERNAL_ERROR", "Invitation could not be completed");
            }
          });
    } catch (RejectedExecutionException e) {
      synchronized (this) {
        for (var c : challenges.values())
          if (c.state == Challenge.State.CREATING_MATCH && c.recipient.equals(caller))
            finish(c, Challenge.State.INVALIDATED, "SERVER_BUSY", request.requestId());
      }
      throw new ProtocolException("SERVER_BUSY", "Service queue full");
    }
  }

  private void finish(Challenge c, Challenge.State terminal, String reason, UUID requestId) {
    c.state = terminal;
    sessions.releaseChallenge(c.id);
    if (c.newMatchId != null) sink.cancelReservation(c.newMatchId);
    sendClosed(c, terminal.name(), reason, requestId);
    presence.broadcast();
  }

  private void sendClosed(Challenge c, String status, String reason, UUID request) {
    var payload =
        new Payloads.ChallengeClosed(
            c.id, status, reason, status.equals("ACCEPTED") ? c.newMatchId : null);
    send(c.sender, MessageType.CHALLENGE_CLOSED, request, payload);
    send(c.recipient, MessageType.CHALLENGE_CLOSED, request, payload);
  }

  private void send(SessionContext s, MessageType type, UUID request, Object payload) {
    transport.tryEnqueue(
        s.connectionId(), codec.envelope(type, request, null, null, null, payload));
  }

  private void sendError(SessionContext caller, Envelope request, String code, String message) {
    send(
        caller,
        MessageType.ERROR,
        request.requestId(),
        new Payloads.Error(code, message, true, codec.mapper().createObjectNode()));
  }

  public synchronized void onDisconnected(SessionContext caller) {
    for (var c : challenges.values())
      if ((c.state == Challenge.State.PENDING || c.state == Challenge.State.CREATING_MATCH)
          && (c.sender.equals(caller) || c.recipient.equals(caller)))
        finish(c, Challenge.State.INVALIDATED, "DISCONNECT", null);
  }

  public synchronized void close() {
    closed = true;
    for (var c : challenges.values())
      if (c.state == Challenge.State.PENDING || c.state == Challenge.State.CREATING_MATCH)
        finish(c, Challenge.State.INVALIDATED, "SERVER_SHUTDOWN", null);
  }
}
