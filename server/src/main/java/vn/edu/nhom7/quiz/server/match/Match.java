package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/** One aggregate lock serializes commands, deadlines, scores and event order. */
public final class Match {
  final ReentrantLock lock = new ReentrantLock();
  final UUID id;
  final Payloads.QuizSummary quiz;
  final List<SessionContext> sessions;
  final List<QuestionSnapshot> questions;
  final List<ParticipantSummary> profiles;
  final Set<Long> attached = new HashSet<>(),
      ready = new HashSet<>(),
      questionReady = new HashSet<>();
  final Map<Long, Accepted> accepted = new HashMap<>();
  final List<RoundSummary> rounds = new ArrayList<>();
  final int[] scores = {0, 0}, correct = {0, 0}, previousRanks = {1, 1};
  final Map<Long, Deque<Long>> chatTimes = new HashMap<>();
  final Map<UUID, JsonNode> chats = new HashMap<>();
  MatchPhase phase = MatchPhase.WAITING_READY;
  long version, sequence, deadline, startNano, resultDeadline;
  Instant startedAt, endedAt;
  UUID roundId;
  int index = -1, completed;
  boolean activated;
  GameScheduler.Cancellable timer;
  MatchSummary terminal;
  JsonNode latestResult, standings;
  Long rematchRequester;
  long rematchExpiry, rematchGeneration;
  boolean rematchLoading;

  record Accepted(
      String answer, long elapsed, Instant received, boolean correct, int points, UUID request) {}

  Match(
      UUID id,
      Payloads.QuizSummary quiz,
      List<SessionContext> sessions,
      List<QuestionSnapshot> questions,
      List<ParticipantSummary> profiles) {
    this.id = Objects.requireNonNull(id);
    this.quiz = Objects.requireNonNull(quiz);
    this.sessions = List.copyOf(sessions);
    this.questions = List.copyOf(questions);
    this.profiles = List.copyOf(profiles);
    if (sessions.size() != 2 || sessions.get(0).userId() == sessions.get(1).userId())
      throw new IllegalArgumentException("Two distinct players required");
    boolean community = "COMMUNITY".equals(quiz.quizSource());
    int total = community ? quiz.totalRounds() : 10;
    if (total < 1
        || total > 50
        || questions.size() != total
        || questions.stream().map(QuestionSnapshot::questionId).distinct().count() != total)
      throw new IllegalArgumentException("Invalid distinct question count");
    if (!community) {
      var counts =
          new EnumMap<vn.edu.nhom7.quiz.common.protocol.QuestionType, Integer>(
              vn.edu.nhom7.quiz.common.protocol.QuestionType.class);
      for (var q : questions) counts.merge(q.questionType(), 1, Integer::sum);
      for (var type : vn.edu.nhom7.quiz.common.protocol.QuestionType.values())
        if (counts.getOrDefault(type, 0)
            != (type == vn.edu.nhom7.quiz.common.protocol.QuestionType.SINGLE_CHOICE ? 4 : 2))
          throw new IllegalArgumentException("Question distribution must be 4/2/2/2");
    }
    sessions.forEach(s -> attached.add(s.userId()));
  }

  public UUID matchId() {
    return id;
  }

  public MatchPhase phase() {
    lock.lock();
    try {
      return phase;
    } finally {
      lock.unlock();
    }
  }

  public Optional<MatchSummary> summary() {
    lock.lock();
    try {
      return Optional.ofNullable(terminal);
    } finally {
      lock.unlock();
    }
  }

  public long phaseVersion() {
    return version;
  }

  public long deadlineNano() {
    return deadline;
  }

  boolean isCurrent(PhaseToken t) {
    return id.equals(t.matchId())
        && Objects.equals(roundId, t.roundId())
        && phase == t.phase()
        && version == t.version();
  }

  int playerIndex(long user) {
    for (int i = 0; i < 2; i++) if (sessions.get(i).userId() == user) return i;
    throw new IllegalArgumentException("NOT_MATCH_MEMBER");
  }

  QuestionSnapshot question() {
    return questions.get(index);
  }
}
