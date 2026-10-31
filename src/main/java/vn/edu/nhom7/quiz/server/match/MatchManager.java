package vn.edu.nhom7.quiz.server.match;

import static vn.edu.nhom7.quiz.server.match.MatchPayloads.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.network.OutboundTransport;
import vn.edu.nhom7.quiz.server.persistence.ResultSink;
import vn.edu.nhom7.quiz.server.quiz.QuizRepository;
import vn.edu.nhom7.quiz.server.session.*;

public final class MatchManager implements MatchGateway {
  private static final System.Logger LOG = System.getLogger(MatchManager.class.getName());
  final GameClock clock;
  final GameScheduler scheduler;
  final OutboundTransport transport;
  final ResultSink sink;
  final Function<Long, ParticipantSummary> profiles;
  final Predicate<UUID> alive;
  final BiConsumer<Long, UUID> detach;
  final Map<UUID, Match> matches = new ConcurrentHashMap<>();
  final Map<UUID, Match> savingMatches = new ConcurrentHashMap<>();
  final AnswerEvaluator evaluator = new AnswerEvaluator();
  private final ThreadLocal<List<Runnable>> effects = new ThreadLocal<>();
  private RematchCoordinator rematches;
  private final MatchLifelines lifelines = new MatchLifelines(this);
  private final MatchChat chat = new MatchChat(this);
  final MatchRounds rounds = new MatchRounds(this);
  private final MatchResults results = new MatchResults(this);

  public MatchManager(
      GameClock clock,
      GameScheduler scheduler,
      OutboundTransport transport,
      ResultSink sink,
      Function<Long, ParticipantSummary> profiles,
      Predicate<UUID> alive,
      BiConsumer<Long, UUID> detach) {
    this.clock = clock;
    this.scheduler = scheduler;
    this.transport = transport;
    this.sink = sink;
    this.profiles = profiles;
    this.alive = alive;
    this.detach = detach;
  }

  public Match prepare(
      UUID id,
      Payloads.QuizSummary quiz,
      List<SessionContext> players,
      List<QuestionSnapshot> questions) {
    for (var question : questions) {
      if (question.quizId() != quiz.quizId())
        throw new IllegalArgumentException("Question belongs to another quiz");
      switch (question.questionType()) {
        case REORDER, RANGE, PINPOINT ->
            vn.edu.nhom7.quiz.server.quiz.QuestionValidator.validate(question);
        default -> evaluator.evaluate(question, correctAnswer(question));
      }
    }
    var profileSnapshots =
        players.stream().map(participant -> profiles.apply(participant.userId())).toList();
    MatchWireBudget.validate(quiz, questions, profileSnapshots);
    return new Match(id, quiz, players, questions, profileSnapshots);
  }

  public void registerPrepared(Match match) {
    locked(
        match,
        () -> {
          Match existing = matches.putIfAbsent(match.id, match);
          if (existing != null && existing != match)
            throw new IllegalStateException("Duplicate match");
        });
  }

  public void discardPrepared(Match match) {
    locked(
        match,
        () -> {
          if (match.activated) return;
          if (match.timer != null) match.timer.cancel();
          match.phase = MatchPhase.CANCELLED;
          match.version++;
          matches.remove(match.id, match);
        });
  }

  public void activate(Match match) {
    locked(
        match,
        () -> {
          if (match.phase == MatchPhase.CANCELLED || match.phase == MatchPhase.CLOSED)
            throw new ProtocolException("INVALID_STATE", "Prepared match was cancelled");
          if (match.activated) return;
          Match existing = matches.putIfAbsent(match.id, match);
          if (existing != null && existing != match)
            throw new IllegalStateException("Duplicate match");
          match.activated = true;
          phase(match, MatchPhase.WAITING_READY, 30);
          emit(
              match,
              "MATCH_START",
              null,
              null,
              obj(
                  "quiz",
                  match.quiz,
                  "players",
                  players(match),
                  "totalRounds",
                  match.questions.size(),
                  "readyDeadlineAtMs",
                  endMillis(match)));
        });
  }

  public Optional<Match> find(UUID id) {
    return Optional.ofNullable(matches.get(id));
  }

  public void configureRematch(
      SessionRegistry registry, QuizRepository repository, Executor executor) {
    rematches = new RematchCoordinator(this, registry, repository, executor);
  }

  @Override
  public void handle(SessionContext caller, Envelope command) {
    if (command.matchId() == null)
      throw new ProtocolException("MATCH_NOT_FOUND", "Match ID required");
    Match match = matches.get(command.matchId());
    if (match == null) throw new ProtocolException("MATCH_NOT_FOUND", "MATCH_NOT_FOUND");
    locked(
        match,
        () -> {
          member(match, caller);
          JsonNode payload = command.payload();
          switch (command.type()) {
            case MATCH_READY -> {
              if (match.phase != MatchPhase.WAITING_READY)
                throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
              match.ready.add(caller.userId());
              emit(
                  match,
                  "READY_STATUS",
                  command.requestId(),
                  null,
                  obj("readyUserIds", match.ready, "phase", match.phase.name()));
              if (match.ready.size() == 2) {
                match.startedAt = clock.instant();
                phase(match, MatchPhase.MATCH_COUNTDOWN, 3);
                emit(match, "MATCH_COUNTDOWN", null, null, timing(match, 3000));
              }
            }
            case QUESTION_READY -> {
              match.requireCurrentRound(command);
              if (match.phase != MatchPhase.QUESTION_PREPARING)
                throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
              match.questionReady.add(caller.userId());
              if (match.questionReady.size() == 2) {
                phase(match, MatchPhase.ROUND_COUNTDOWN, 2);
                emit(match, "ROUND_COUNTDOWN", null, null, timing(match, 2000));
              }
            }
            case ANSWER -> rounds.answer(match, caller, command);
            case LIFELINE_USE -> lifelines.lifeline(match, caller, command);
            case CHAT -> chat.chat(match, caller, command);
            case EXIT_MATCH ->
                results.exit(match, caller, ReasonCode.USER_EXIT, command.requestId());
            case MATCH_SNAPSHOT_REQUEST ->
                emit(
                    match,
                    "MATCH_SNAPSHOT",
                    command.requestId(),
                    caller.connectionId(),
                    MatchSnapshotFactory.snapshotLocked(match, caller.userId(), clock));
            case LIVE_REVIEW_REQUEST -> {
              if (match.phase != MatchPhase.RESULT)
                throw new ProtocolException("INVALID_STATE", "Result required");
              int page = Math.max(1, payload.path("page").asInt(1));
              emit(
                  match,
                  "LIVE_REVIEW",
                  command.requestId(),
                  caller.connectionId(),
                  obj(
                      "page",
                      page,
                      "totalItems",
                      match.rounds.size(),
                      "review",
                      match.rounds.stream()
                          .skip((long) (page - 1) * Payloads.REVIEW_PAGE_SIZE)
                          .limit(Payloads.REVIEW_PAGE_SIZE)
                          .map(MatchPayloads::review)
                          .toList()));
            }
            case REMATCH_REQUEST, REMATCH_RESPONSE -> {
              if (rematches == null) throw new IllegalStateException("REMATCH_UNAVAILABLE");
              rematches.commandLocked(match, caller, command);
            }
            default -> throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
          }
        });
  }

  @Override
  public void onDisconnected(SessionContext caller) {
    for (Match match : matches.values())
      if (match.sessions.contains(caller))
        locked(
            match,
            () -> {
              if (match.attached.contains(caller.userId()))
                results.exit(match, caller, ReasonCode.DISCONNECT, null);
            });
  }

  public void onLogout(SessionContext caller) {
    for (Match match : matches.values())
      if (match.sessions.contains(caller))
        locked(
            match,
            () -> {
              if (match.attached.contains(caller.userId()))
                results.exit(match, caller, ReasonCode.LOGOUT, null);
            });
  }

  public void shutdown() {
    for (Match match : matches.values())
      locked(match, () -> finish(match, FinishReason.ABORTED, ReasonCode.SERVER_SHUTDOWN, null));
  }

  void locked(Match match, Runnable action) {
    List<Runnable> work = new ArrayList<>();
    match.lock.lock();
    effects.set(work);
    try {
      action.run();
    } finally {
      effects.remove();
      match.lock.unlock();
      for (Runnable effect : work) effect.run();
    }
  }

  void after(Runnable action) {
    effects.get().add(action);
  }

  void member(Match match, SessionContext caller) {
    if (!match.sessions.contains(caller) || !match.attached.contains(caller.userId()))
      throw new ProtocolException("NOT_MATCH_MEMBER", "NOT_MATCH_MEMBER");
  }

  void phase(Match match, MatchPhase next, int seconds) {
    if (match.timer != null) match.timer.cancel();
    match.phase = next;
    match.version++;
    LOG.log(
        System.Logger.Level.INFO,
        "utc="
            + clock.instant()
            + " matchId="
            + match.id
            + " roundId="
            + match.roundId
            + " phase="
            + next
            + " phaseVersion="
            + match.version
            + " eventSeq="
            + match.sequence);
    match.deadline = clock.nanoTime() + Duration.ofSeconds(seconds).toNanos();
    PhaseToken token = new PhaseToken(match.id, match.roundId, next, match.version);
    match.timer =
        scheduler.schedule(
            Duration.ofSeconds(seconds),
            () ->
                locked(
                    match,
                    () -> {
                      if (match.isCurrent(token)) rounds.timeout(match);
                    }));
  }

  void finish(Match match, FinishReason reason, ReasonCode code, Long loser) {
    results.finish(match, reason, code, loser);
  }

  void closeResult(Match match, String reason) {
    results.closeResult(match, reason);
  }

  long now() {
    return clock.instant().toEpochMilli();
  }

  long endMillis(Match match) {
    return now() + Math.max(0, (match.deadline - clock.nanoTime()) / 1_000_000L);
  }

  JsonNode timing(Match match, int duration) {
    return obj("durationMs", duration, "phaseEndsAtMs", endMillis(match), "serverTimeMs", now());
  }

  void emit(Match match, String type, UUID request, UUID connection, JsonNode payload) {
    UUID eventRound =
        Set.of(
                    "QUESTION",
                    "ROUND_COUNTDOWN",
                    "QUESTION_OPEN",
                    "ANSWER_ACK",
                    "ANSWER_STATUS",
                    "QUESTION_RESULT",
                    "ROUND_LEADERBOARD",
                    "MATCH_SNAPSHOT",
                    "LIFELINE_RESULT",
                    "LIFELINE_STATUS")
                .contains(type)
            ? match.roundId
            : null;
    Envelope event =
        new Envelope(
            1, MessageType.valueOf(type), request, match.id, eventRound, ++match.sequence, payload);
    for (var s : match.sessions)
      if (connection == null
          ? match.attached.contains(s.userId())
          : connection.equals(s.connectionId())) {
        try {
          if (!transport.tryEnqueue(s.connectionId(), event)) after(() -> onDisconnected(s));
        } catch (RuntimeException failure) {
          after(() -> onDisconnected(s));
        }
      }
  }

  public boolean canAccessMedia(long userId, String mediaId) {
    if (mediaId == null || mediaId.isBlank()) return false;
    for (Match match : matches.values()) {
      match.lock.lock();
      try {
        if (!match.attached.contains(userId)
            || match.phase == MatchPhase.CANCELLED
            || match.phase == MatchPhase.CLOSED) continue;
        if (match.index >= 0 && mediaId.equals(match.question().questionAssetId())) return true;
        for (var round : match.rounds) {
          if (mediaId.equals(round.question().questionAssetId())
              || round.revealed() && mediaId.equals(round.question().explanationAssetId()))
            return true;
        }
      } finally {
        match.lock.unlock();
      }
    }
    return false;
  }

  public int activeCount() {
    return matches.size();
  }

  public void publishSaveStatus(
      MatchSummary summary,
      String status,
      boolean retryable,
      Long savedAtMs,
      List<UUID> recipients) {
    Match match = savingMatches.get(summary.matchId());
    if (match == null) match = matches.get(summary.matchId());
    if (match == null) return;
    Match original = match;
    locked(
        original,
        () -> {
          Envelope event =
              new Envelope(
                  1,
                  MessageType.MATCH_SAVE_STATUS,
                  null,
                  original.id,
                  null,
                  ++original.sequence,
                  obj("status", status, "retryable", retryable, "savedAtMs", savedAtMs));
          for (UUID connection : recipients)
            try {
              transport.tryEnqueue(connection, event);
            } catch (RuntimeException failure) {
              LOG.log(
                  System.Logger.Level.WARNING,
                  "utc="
                      + clock.instant()
                      + " matchId="
                      + original.id
                      + " save notification enqueue failed");
            }
          if (status.equals("SAVED")) savingMatches.remove(original.id, original);
        });
  }
}
