package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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
  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
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
      evaluator.evaluate(question, correctAnswer(question));
    }
    var profileSnapshots = players.stream().map(p -> profiles.apply(p.userId())).toList();
    MatchWireBudget.validate(quiz, questions, profileSnapshots);
    return new Match(id, quiz, players, questions, profileSnapshots);
  }

  public void registerPrepared(Match m) {
    locked(
        m,
        () -> {
          Match existing = matches.putIfAbsent(m.id, m);
          if (existing != null && existing != m) throw new IllegalStateException("Duplicate match");
        });
  }

  public void discardPrepared(Match m) {
    locked(
        m,
        () -> {
          if (m.activated) return;
          if (m.timer != null) m.timer.cancel();
          m.phase = MatchPhase.CANCELLED;
          m.version++;
          matches.remove(m.id, m);
        });
  }

  public void activate(Match m) {
    locked(
        m,
        () -> {
          if (m.phase == MatchPhase.CANCELLED || m.phase == MatchPhase.CLOSED)
            throw new ProtocolException("INVALID_STATE", "Prepared match was cancelled");
          if (m.activated) return;
          Match existing = matches.putIfAbsent(m.id, m);
          if (existing != null && existing != m) throw new IllegalStateException("Duplicate match");
          m.activated = true;
          phase(m, MatchPhase.WAITING_READY, 30);
          emit(
              m,
              "MATCH_START",
              null,
              null,
              obj(
                  "quiz",
                  m.quiz,
                  "players",
                  players(m),
                  "totalRounds",
                  m.questions.size(),
                  "readyDeadlineAtMs",
                  endMillis(m)));
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
    Match m = matches.get(command.matchId());
    if (m == null) throw new ProtocolException("MATCH_NOT_FOUND", "MATCH_NOT_FOUND");
    locked(
        m,
        () -> {
          member(m, caller);
          String type = command.type().name();
          JsonNode p = command.payload();
          switch (type) {
            case "MATCH_READY" -> {
              if (m.phase != MatchPhase.WAITING_READY)
                throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
              m.ready.add(caller.userId());
              emit(
                  m,
                  "READY_STATUS",
                  command.requestId(),
                  null,
                  obj("readyUserIds", m.ready, "phase", m.phase.name()));
              if (m.ready.size() == 2) {
                m.startedAt = clock.instant();
                phase(m, MatchPhase.MATCH_COUNTDOWN, 3);
                emit(m, "MATCH_COUNTDOWN", null, null, timing(m, 3000));
              }
            }
            case "QUESTION_READY" -> {
              round(m, command, p);
              if (m.phase != MatchPhase.QUESTION_PREPARING)
                throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
              m.questionReady.add(caller.userId());
              if (m.questionReady.size() == 2) {
                phase(m, MatchPhase.ROUND_COUNTDOWN, 2);
                emit(m, "ROUND_COUNTDOWN", null, null, timing(m, 2000));
              }
            }
            case "ANSWER" -> answer(m, caller, command);
            case "CHAT" -> chat(m, caller, command);
            case "EXIT_MATCH" -> exit(m, caller, ReasonCode.USER_EXIT, command.requestId());
            case "MATCH_SNAPSHOT_REQUEST" ->
                emit(
                    m,
                    "MATCH_SNAPSHOT",
                    command.requestId(),
                    caller.connectionId(),
                    snapshot(m, caller.userId()));
            case "LIVE_REVIEW_REQUEST" -> {
              if (m.phase != MatchPhase.RESULT)
                throw new ProtocolException("INVALID_STATE", "Result required");
              int page = Math.max(1, p.path("page").asInt(1));
              emit(
                  m,
                  "LIVE_REVIEW",
                  command.requestId(),
                  caller.connectionId(),
                  obj(
                      "page",
                      page,
                      "totalItems",
                      m.rounds.size(),
                      "review",
                      m.rounds.stream()
                          .skip((long) (page - 1) * Payloads.REVIEW_PAGE_SIZE)
                          .limit(Payloads.REVIEW_PAGE_SIZE)
                          .map(this::review)
                          .toList()));
            }
            case "REMATCH_REQUEST", "REMATCH_RESPONSE" -> {
              if (rematches == null) throw new IllegalStateException("REMATCH_UNAVAILABLE");
              rematches.commandLocked(m, caller, command);
            }
            default -> throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
          }
        });
  }

  @Override
  public void onDisconnected(SessionContext caller) {
    for (Match m : matches.values())
      if (m.sessions.contains(caller))
        locked(
            m,
            () -> {
              if (m.attached.contains(caller.userId()))
                exit(m, caller, ReasonCode.DISCONNECT, null);
            });
  }

  public void onLogout(SessionContext caller) {
    for (Match m : matches.values())
      if (m.sessions.contains(caller))
        locked(
            m,
            () -> {
              if (m.attached.contains(caller.userId())) exit(m, caller, ReasonCode.LOGOUT, null);
            });
  }

  public void shutdown() {
    for (Match m : matches.values())
      locked(m, () -> finish(m, FinishReason.ABORTED, ReasonCode.SERVER_SHUTDOWN, null));
  }

  void locked(Match m, Runnable action) {
    List<Runnable> work = new ArrayList<>();
    m.lock.lock();
    effects.set(work);
    try {
      action.run();
    } finally {
      effects.remove();
      m.lock.unlock();
      for (Runnable effect : work) effect.run();
    }
  }

  void after(Runnable action) {
    effects.get().add(action);
  }

  void member(Match m, SessionContext caller) {
    if (!m.sessions.contains(caller) || !m.attached.contains(caller.userId()))
      throw new ProtocolException("NOT_MATCH_MEMBER", "NOT_MATCH_MEMBER");
  }

  void phase(Match m, MatchPhase next, int seconds) {
    if (m.timer != null) m.timer.cancel();
    m.phase = next;
    m.version++;
    LOG.log(
        System.Logger.Level.INFO,
        "utc="
            + clock.instant()
            + " matchId="
            + m.id
            + " roundId="
            + m.roundId
            + " phase="
            + next
            + " phaseVersion="
            + m.version
            + " eventSeq="
            + m.sequence);
    m.deadline = clock.nanoTime() + Duration.ofSeconds(seconds).toNanos();
    PhaseToken token = new PhaseToken(m.id, m.roundId, next, m.version);
    m.timer =
        scheduler.schedule(
            Duration.ofSeconds(seconds),
            () ->
                locked(
                    m,
                    () -> {
                      if (m.isCurrent(token)) timeout(m);
                    }));
  }

  void timeout(Match m) {
    switch (m.phase) {
      case WAITING_READY -> finish(m, FinishReason.ABORTED, ReasonCode.CLIENT_NOT_READY, null);
      case MATCH_COUNTDOWN -> nextQuestion(m);
      case QUESTION_PREPARING -> finish(m, FinishReason.ABORTED, ReasonCode.CLIENT_NOT_READY, null);
      case ROUND_COUNTDOWN -> {
        phase(m, MatchPhase.ANSWERING, 15);
        m.startNano = m.deadline - 15_000_000_000L;
        emit(
            m,
            "QUESTION_OPEN",
            null,
            null,
            obj(
                "questionId",
                m.question().questionId(),
                "remainingMs",
                15000,
                "deadlineAtMs",
                endMillis(m),
                "serverTimeMs",
                now()));
      }
      case ANSWERING -> closeRound(m);
      case REVEAL -> leaderboard(m);
      case LEADERBOARD -> {
        if (m.completed == m.questions.size())
          finish(m, FinishReason.COMPLETED, ReasonCode.NORMAL, null);
        else nextQuestion(m);
      }
      case RESULT -> closeResult(m, "EXPIRED");
      default -> {}
    }
  }

  void nextQuestion(Match m) {
    m.index++;
    m.roundId = UUID.randomUUID();
    m.accepted.clear();
    m.questionReady.clear();
    phase(m, MatchPhase.QUESTION_PREPARING, 30);
    emit(
        m,
        "QUESTION",
        null,
        null,
        obj(
            "question",
            publicQuestion(m.question()),
            "roundIndex",
            m.index + 1,
            "totalRounds",
            m.questions.size(),
            "readyDeadlineAtMs",
            endMillis(m)));
  }

  void round(Match m, Envelope c, JsonNode p) {
    if (!Objects.equals(m.roundId, c.roundId())
        || m.index < 0
        || p.path("questionId").asLong(-1) != m.question().questionId())
      throw new ProtocolException("STALE_ROUND", "STALE_ROUND");
  }

  void answer(Match m, SessionContext caller, Envelope c) {
    long received = clock.nanoTime();
    round(m, c, c.payload());
    Match.Accepted previous = m.accepted.get(caller.userId());
    if (previous != null) {
      if (Objects.equals(previous.request(), c.requestId())) {
        ack(m, caller, c.requestId(), previous);
        return;
      }
      throw new ProtocolException(
          "ALREADY_ANSWERED",
          "Answer already accepted",
          obj("acceptedRequestId", previous.request()));
    }
    if (m.phase != MatchPhase.ANSWERING)
      throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
    if (received > m.deadline) throw new ProtocolException("LATE_ANSWER", "LATE_ANSWER");
    JsonNode a = c.payload().get("answer");
    boolean correct = evaluator.evaluate(m.question(), a);
    long elapsed = received - m.startNano;
    Match.Accepted accepted =
        new Match.Accepted(
            a.toString(),
            elapsed,
            clock.instant(),
            correct,
            ScoreCalculator.calculate(correct, elapsed),
            c.requestId());
    m.accepted.put(caller.userId(), accepted);
    ack(m, caller, c.requestId(), accepted);
    emit(m, "ANSWER_STATUS", null, null, obj("userId", caller.userId(), "answered", true));
    if (m.accepted.size() == 2) closeRound(m);
  }

  void ack(Match m, SessionContext caller, UUID request, Match.Accepted a) {
    emit(
        m,
        "ANSWER_ACK",
        request,
        caller.connectionId(),
        obj(
            "questionId",
            m.question().questionId(),
            "accepted",
            true,
            "answerTimeMs",
            a.elapsed() / 1_000_000L,
            "acceptedRequestId",
            a.request()));
  }

  void closeRound(Match m) {
    if (m.phase != MatchPhase.ANSWERING) return;
    List<StoredAnswerOutcome> outcomes = outcomes(m, true);
    m.rounds.add(new RoundSummary(m.roundId, m.index + 1, m.question(), true, outcomes));
    m.completed++;
    phase(m, MatchPhase.REVEAL, 2);
    m.latestResult =
        obj(
            "questionId",
            m.question().questionId(),
            "correctAnswer",
            correctAnswer(m.question()),
            "explanation",
            m.question().explanation(),
            "explanationAssetId",
            m.question().explanationAssetId(),
            "outcomes",
            outcomes.stream().map(this::publicOutcome).toList(),
            "durationMs",
            2000,
            "phaseEndsAtMs",
            endMillis(m),
            "serverTimeMs",
            now());
    emit(m, "QUESTION_RESULT", null, null, m.latestResult);
  }

  List<StoredAnswerOutcome> outcomes(Match m, boolean finalized) {
    List<StoredAnswerOutcome> values = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long user = m.sessions.get(i).userId();
      var a = m.accepted.get(user);
      int before = m.scores[i];
      int points = finalized && a != null ? a.points() : 0;
      if (finalized) {
        m.scores[i] += points;
        if (a != null && a.correct()) m.correct[i]++;
      }
      values.add(
          new StoredAnswerOutcome(
              user,
              a == null ? null : a.answer(),
              a == null ? null : a.elapsed(),
              a == null ? null : a.received(),
              !finalized
                  ? AnswerOutcomeType.ABANDONED
                  : a == null ? AnswerOutcomeType.TIMEOUT : AnswerOutcomeType.ANSWERED,
              !finalized ? null : a != null && a.correct(),
              points,
              before,
              m.scores[i],
              a == null ? null : a.request()));
    }
    return values;
  }

  void leaderboard(Match m) {
    phase(m, MatchPhase.LEADERBOARD, 3);
    List<JsonNode> rows = new ArrayList<>();
    var outcomes = m.rounds.getLast().outcomes();
    for (int i = 0; i < 2; i++) {
      var p = m.profiles.get(i);
      var o = outcomes.get(i);
      int rank = MatchRanker.rank(m.scores[i], m.scores[1 - i]);
      rows.add(
          obj(
              "userId",
              p.userId(),
              "displayName",
              p.displayName(),
              "avatarId",
              p.avatarId(),
              "rank",
              rank,
              "previousRank",
              m.previousRanks[i],
              "scoreBefore",
              o.scoreBefore(),
              "earnedPoints",
              o.earnedPoints(),
              "totalScore",
              m.scores[i],
              "correctCount",
              m.correct[i],
              "outcome",
              o.outcome().name()));
      m.previousRanks[i] = rank;
    }
    rows.sort(Comparator.comparingInt((JsonNode n) -> n.path("totalScore").asInt()).reversed());
    m.standings = JSON.valueToTree(rows);
    emit(
        m,
        "ROUND_LEADERBOARD",
        null,
        null,
        obj(
            "roundIndex",
            m.index + 1,
            "standings",
            m.standings,
            "durationMs",
            3000,
            "phaseEndsAtMs",
            endMillis(m),
            "serverTimeMs",
            now()));
  }

  void finish(Match m, FinishReason reason, ReasonCode code, Long loser) {
    if (m.terminal != null || m.phase == MatchPhase.CANCELLED || m.phase == MatchPhase.CLOSED)
      return;
    if (m.startedAt == null) {
      if (m.timer != null) m.timer.cancel();
      m.phase = MatchPhase.CANCELLED;
      m.version++;
      emit(m, "RESULT_SESSION_CLOSED", null, null, obj("reason", "EXITED"));
      for (var s : m.sessions) after(() -> detach.accept(s.userId(), m.id));
      after(() -> sink.cancelReservation(m.id));
      after(() -> matches.remove(m.id, m));
      return;
    }
    if (!alive.test(m.sessions.get(0).connectionId())
        && !alive.test(m.sessions.get(1).connectionId())) {
      reason = FinishReason.ABORTED;
      code = ReasonCode.BOTH_DISCONNECTED;
    }
    if (m.index >= 0 && m.rounds.size() <= m.index)
      m.rounds.add(
          new RoundSummary(m.roundId, m.index + 1, m.question(), false, outcomes(m, false)));
    m.endedAt = clock.instant();
    MatchOutcome outcome =
        reason == FinishReason.ABORTED
            ? MatchOutcome.NONE
            : reason == FinishReason.FORFEIT
                ? (Objects.equals(loser, m.sessions.get(0).userId())
                    ? MatchOutcome.PLAYER2_WIN
                    : MatchOutcome.PLAYER1_WIN)
                : m.scores[0] == m.scores[1]
                    ? MatchOutcome.DRAW
                    : m.scores[0] > m.scores[1]
                        ? MatchOutcome.PLAYER1_WIN
                        : MatchOutcome.PLAYER2_WIN;
    m.terminal =
        new MatchSummary(
            m.id,
            m.quiz.quizId(),
            m.quiz.title(),
            participant(m, 0),
            participant(m, 1),
            m.startedAt,
            m.endedAt,
            reason,
            code,
            outcome,
            m.completed,
            m.index + 1,
            m.rounds,
            !"COMMUNITY".equals(m.quiz.quizSource()),
            m.quiz.quizVersionId(),
            m.questions.size());
    phase(m, MatchPhase.RESULT, 60);
    m.resultDeadline = m.deadline;
    Long winner = null;
    if (outcome == MatchOutcome.PLAYER1_WIN) winner = m.sessions.get(0).userId();
    else if (outcome == MatchOutcome.PLAYER2_WIN) winner = m.sessions.get(1).userId();
    savingMatches.put(m.id, m);
    after(() -> sink.submit(m.terminal));
    emit(
        m,
        "MATCH_RESULT",
        null,
        null,
        obj(
            "finishReason",
            reason.name(),
            "reasonCode",
            code.name(),
            "winnerUserId",
            winner,
            "players",
            players(m),
            "completedRounds",
            m.completed,
            "openedRounds",
            m.index + 1,
            "review",
            m.rounds.stream().limit(Payloads.REVIEW_PAGE_SIZE).map(this::review).toList(),
            "reviewPage",
            1,
            "reviewTotalItems",
            m.rounds.size(),
            "totalRounds",
            m.questions.size(),
            "ranked",
            m.terminal.ranked(),
            "persistenceStatus",
            "PENDING",
            "resultExpiresAtMs",
            endMillis(m)));
  }

  void exit(Match m, SessionContext caller, ReasonCode reason, UUID request) {
    if (m.phase != MatchPhase.RESULT && m.phase != MatchPhase.CLOSED)
      finish(m, FinishReason.FORFEIT, reason, caller.userId());
    invalidateRematch(m);
    m.attached.remove(caller.userId());
    m.rematchGeneration++;
    m.rematchRequester = null;
    after(() -> detach.accept(caller.userId(), m.id));
    emit(
        m,
        "EXIT_ACK",
        request,
        caller.connectionId(),
        obj("reason", reason.name(), "lobbyStatus", "FREE"));
    emit(m, "RESULT_SESSION_CLOSED", null, caller.connectionId(), obj("reason", "EXITED"));
    if (m.attached.isEmpty() && m.phase == MatchPhase.RESULT) closeResult(m, "EXITED");
  }

  void invalidateRematch(Match m) {
    if (m.rematchRequester != null) {
      emit(
          m,
          "REMATCH_STATUS",
          null,
          null,
          obj(
              "status",
              "INVALIDATED",
              "requesterUserId",
              m.rematchRequester,
              "expiresAtMs",
              null,
              "newMatchId",
              null));
      m.rematchRequester = null;
      m.rematchLoading = false;
      m.rematchGeneration++;
    }
  }

  void closeResult(Match m, String reason) {
    invalidateRematch(m);
    if (m.timer != null) m.timer.cancel();
    m.version++;
    m.phase = MatchPhase.CLOSED;
    m.rematchGeneration++;
    for (var s : m.sessions)
      if (m.attached.remove(s.userId())) {
        emit(m, "RESULT_SESSION_CLOSED", null, s.connectionId(), obj("reason", reason));
        after(() -> detach.accept(s.userId(), m.id));
      }
    after(() -> matches.remove(m.id, m));
  }

  void chat(Match m, SessionContext caller, Envelope c) {
    if (m.phase == MatchPhase.CLOSED || m.phase == MatchPhase.CANCELLED)
      throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
    if (m.phase == MatchPhase.RESULT && m.attached.size() != 2)
      throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
    JsonNode previous = m.chats.get(c.requestId());
    if (previous != null) {
      emit(m, "CHAT_MESSAGE", c.requestId(), caller.connectionId(), previous);
      return;
    }
    JsonNode raw = c.payload().get("text");
    if (raw == null || !raw.isTextual())
      throw new ProtocolException("INVALID_INPUT", "INVALID_INPUT");
    String text = raw.textValue().strip();
    int count = text.codePointCount(0, text.length());
    if (count < 1 || count > 300) throw new ProtocolException("INVALID_INPUT", "INVALID_INPUT");
    long now = clock.nanoTime();
    Deque<Long> times = m.chatTimes.computeIfAbsent(caller.userId(), id -> new ArrayDeque<>());
    while (!times.isEmpty() && now - times.getFirst() >= 10_000_000_000L) times.removeFirst();
    if (times.size() >= 5)
      throw new ProtocolException(
          "RATE_LIMITED",
          "Too many chat messages",
          obj(
              "retryAfterMs",
              Math.max(1, (10_000_000_000L - (now - times.getFirst()) + 999_999L) / 1_000_000L)));
    times.addLast(now);
    JsonNode message =
        obj(
            "chatMessageId",
            UUID.randomUUID(),
            "senderUserId",
            caller.userId(),
            "displayName",
            m.profiles.get(m.playerIndex(caller.userId())).displayName(),
            "text",
            text,
            "sentAtMs",
            now());
    m.chats.put(c.requestId(), message);
    emit(m, "CHAT_MESSAGE", c.requestId(), null, message);
  }

  JsonNode snapshot(Match m, long user) {
    JsonNode accepted = null;
    var a = m.accepted.get(user);
    if (a != null)
      accepted =
          obj(
              "answer",
              parse(a.answer()),
              "answerTimeMs",
              a.elapsed() / 1_000_000L,
              "acceptedRequestId",
              a.request());
    return obj(
        "phase",
        m.phase.name(),
        "phaseVersion",
        m.version,
        "serverTimeMs",
        now(),
        "phaseRemainingMs",
        Math.max(0, (m.deadline - clock.nanoTime()) / 1_000_000L),
        "quiz",
        m.quiz,
        "players",
        players(m),
        "roundIndex",
        m.index + 1,
        "totalRounds",
        m.questions.size(),
        "question",
        m.index < 0 ? null : publicQuestion(m.question()),
        "acceptedAnswer",
        accepted,
        "answeredUserIds",
        m.accepted.keySet(),
        "latestResult",
        m.phase == MatchPhase.REVEAL
                || m.phase == MatchPhase.LEADERBOARD
                || m.phase == MatchPhase.RESULT
            ? m.latestResult
            : null,
        "standings",
        m.standings);
  }

  ParticipantSummary participant(Match m, int i) {
    var p = m.profiles.get(i);
    return new ParticipantSummary(
        p.userId(), p.displayName(), p.avatarId(), m.scores[i], m.correct[i]);
  }

  List<JsonNode> players(Match m) {
    List<JsonNode> result = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      var p = participant(m, i);
      result.add(
          obj(
              "userId",
              p.userId(),
              "displayName",
              p.displayName(),
              "avatarId",
              p.avatarId(),
              "totalScore",
              p.totalScore(),
              "correctCount",
              p.correctCount(),
              "rank",
              MatchRanker.rank(m.scores[i], m.scores[1 - i])));
    }
    return result;
  }

  JsonNode review(RoundSummary r) {
    return obj(
        "roundId",
        r.roundId(),
        "roundIndex",
        r.roundIndex(),
        "question",
        publicQuestion(r.question()),
        "correctAnswer",
        r.revealed() ? correctAnswer(r.question()) : null,
        "explanation",
        r.revealed() ? r.question().explanation() : null,
        "explanationAssetId",
        r.revealed() ? r.question().explanationAssetId() : null,
        "outcomes",
        r.outcomes().stream().map(this::publicOutcome).toList(),
        "revealed",
        r.revealed());
  }

  JsonNode publicOutcome(StoredAnswerOutcome a) {
    return obj(
        "userId",
        a.userId(),
        "answer",
        a.answerJson() == null ? null : parse(a.answerJson()),
        "outcome",
        a.outcome().name(),
        "correct",
        a.correct(),
        "answerTimeMs",
        a.elapsedNanos() == null ? null : a.elapsedNanos() / 1_000_000L,
        "earnedPoints",
        a.earnedPoints(),
        "scoreBefore",
        a.scoreBefore(),
        "totalScore",
        a.totalScore());
  }

  static JsonNode publicQuestion(QuestionSnapshot q) {
    return obj(
        "questionId",
        q.questionId(),
        "questionType",
        q.questionType().name(),
        "content",
        q.content(),
        "options",
        q.options(),
        "questionAssetId",
        q.questionAssetId(),
        "timeLimitMs",
        15000);
  }

  static JsonNode correctAnswer(QuestionSnapshot q) {
    JsonNode key = parse(q.answerKeyJson());
    if (key.isObject())
      key = key.has("acceptedAnswers") ? key.get("acceptedAnswers") : key.get("value");
    return q.questionType() == QuestionType.SHORT_ANSWER ? key.get(0) : key;
  }

  static JsonNode parse(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid JSON", e);
    }
  }

  static ObjectNode obj(Object... values) {
    ObjectNode node = JSON.createObjectNode();
    for (int i = 0; i < values.length; i += 2)
      node.set((String) values[i], JSON.valueToTree(values[i + 1]));
    return node;
  }

  long now() {
    return clock.instant().toEpochMilli();
  }

  long endMillis(Match m) {
    return now() + Math.max(0, (m.deadline - clock.nanoTime()) / 1_000_000L);
  }

  JsonNode timing(Match m, int duration) {
    return obj("durationMs", duration, "phaseEndsAtMs", endMillis(m), "serverTimeMs", now());
  }

  void emit(Match m, String type, UUID request, UUID connection, JsonNode payload) {
    UUID eventRound =
        Set.of(
                    "QUESTION",
                    "ROUND_COUNTDOWN",
                    "QUESTION_OPEN",
                    "ANSWER_ACK",
                    "ANSWER_STATUS",
                    "QUESTION_RESULT",
                    "ROUND_LEADERBOARD",
                    "MATCH_SNAPSHOT")
                .contains(type)
            ? m.roundId
            : null;
    Envelope event =
        new Envelope(
            1, MessageType.valueOf(type), request, m.id, eventRound, ++m.sequence, payload);
    for (var s : m.sessions)
      if (connection == null
          ? m.attached.contains(s.userId())
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
    for (Match m : matches.values()) {
      m.lock.lock();
      try {
        if (!m.attached.contains(userId)
            || m.phase == MatchPhase.CANCELLED
            || m.phase == MatchPhase.CLOSED) continue;
        if (m.index >= 0 && mediaId.equals(m.question().questionAssetId())) return true;
        for (var r : m.rounds) {
          if (mediaId.equals(r.question().questionAssetId())
              || r.revealed() && mediaId.equals(r.question().explanationAssetId())) return true;
        }
      } finally {
        m.lock.unlock();
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
    Match m = savingMatches.get(summary.matchId());
    if (m == null) m = matches.get(summary.matchId());
    if (m == null) return;
    Match original = m;
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
