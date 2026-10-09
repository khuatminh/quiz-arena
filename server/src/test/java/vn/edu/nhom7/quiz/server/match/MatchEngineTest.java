package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.persistence.ResultSink;
import vn.edu.nhom7.quiz.server.support.*;

class MatchEngineTest {
  FakeGameClock clock;
  FakeGameScheduler scheduler;
  MatchManager manager;
  Match match;
  List<Envelope> events;
  List<MatchSummary> saved;

  @BeforeEach
  void setup() {
    clock = new FakeGameClock();
    scheduler = new FakeGameScheduler(clock);
    events = Collections.synchronizedList(new ArrayList<>());
    saved = new ArrayList<>();
    ProtocolCodec codec = new ProtocolCodec();
    manager =
        new MatchManager(
            clock,
            scheduler,
            (c, e) -> {
              codec.encode(e);
              events.add(e);
              return true;
            },
            new ResultSink() {
              public void submit(MatchSummary s) {
                saved.add(s);
              }

              public boolean canCreateMatch() {
                return true;
              }

              public boolean reserveMatch(UUID id) {
                return true;
              }

              public void cancelReservation(UUID id) {}
            },
            id -> new ParticipantSummary(id, "P" + id, "avatar-1", 0, 0),
            c -> true,
            (u, id) -> {});
    match =
        manager.prepare(
            UUID.randomUUID(),
            ServerFixtures.quiz(),
            ServerFixtures.players(),
            ServerFixtures.questions());
    manager.activate(match);
  }

  void command(int player, String type, Object... fields) {
    manager.handle(
        ServerFixtures.players().get(player),
        new Envelope(
            1,
            MessageType.valueOf(type),
            UUID.randomUUID(),
            match.id,
            match.roundId,
            null,
            MatchManager.obj(fields)));
  }

  void start() {
    command(0, "MATCH_READY");
    command(1, "MATCH_READY");
    scheduler.advance(Duration.ofSeconds(3));
  }

  void open() {
    command(0, "QUESTION_READY", "questionId", match.question().questionId());
    command(1, "QUESTION_READY", "questionId", match.question().questionId());
    scheduler.advance(Duration.ofSeconds(2));
  }

  @Test
  void completesTenRoundsAndPublishesLeaderboardTenBeforeResult() {
    start();
    for (int i = 0; i < 10; i++) {
      open();
      var answer = MatchManager.correctAnswer(match.question());
      command(0, "ANSWER", "questionId", match.question().questionId(), "answer", answer);
      command(1, "ANSWER", "questionId", match.question().questionId(), "answer", answer);
      assertEquals(MatchPhase.REVEAL, match.phase());
      scheduler.advance(Duration.ofSeconds(5));
    }
    assertEquals(MatchPhase.RESULT, match.phase());
    assertEquals(1, saved.size());
    assertEquals(10, saved.getFirst().completedRounds());
    assertEquals(10, saved.getFirst().openedRounds());
    assertEquals(30, saved.getFirst().player1().totalScore());
    assertEquals(MatchOutcome.DRAW, saved.getFirst().outcome());
    assertEquals(
        20, events.stream().filter(e -> e.type() == MessageType.ROUND_LEADERBOARD).count());
    int result = 0, lastBoard = 0;
    for (int i = 0; i < events.size(); i++) {
      if (events.get(i).type() == MessageType.MATCH_RESULT) result = i;
      if (events.get(i).type() == MessageType.ROUND_LEADERBOARD) lastBoard = i;
    }
    assertTrue(result > lastBoard);
  }

  @Test
  void waitingTimeoutCancelsWithoutHistoryAndStaleCallbacksDoNothing() {
    var old = scheduler.callbacks();
    scheduler.advance(Duration.ofSeconds(30));
    assertEquals(MatchPhase.CANCELLED, match.phase());
    old.forEach(Runnable::run);
    assertTrue(saved.isEmpty());
  }

  @Test
  void invalidAnswerDoesNotConsumeSlotAndSnapshotProtectsOpponent() {
    start();
    open();
    assertThrows(
        ProtocolException.class, () -> command(0, "ANSWER", "questionId", 1, "answer", "unknown"));
    assertTrue(match.accepted.isEmpty());
    command(0, "ANSWER", "questionId", 1, "answer", "A");
    command(1, "MATCH_SNAPSHOT_REQUEST");
    var snap = events.getLast().payload();
    assertTrue(snap.path("acceptedAnswer").isNull());
    assertFalse(snap.toString().contains("correctAnswer"));
    long deadline = match.deadline;
    command(0, "CHAT", "text", "hello");
    assertEquals(deadline, match.deadline);
    scheduler.advance(Duration.ofSeconds(15));
    assertEquals(3, match.scores[0]);
    assertEquals(0, match.scores[1]);
  }

  @Test
  void unfinalizedAnswerIsAbandonedOnForfeit() {
    start();
    open();
    command(0, "ANSWER", "questionId", 1, "answer", "A");
    command(1, "EXIT_MATCH");
    var summary = saved.getFirst();
    assertEquals(FinishReason.FORFEIT, summary.finishReason());
    assertEquals(0, summary.completedRounds());
    assertEquals(1, summary.openedRounds());
    assertEquals(0, summary.player1().totalScore());
    assertEquals(
        AnswerOutcomeType.ABANDONED, summary.rounds().getFirst().outcomes().getFirst().outcome());
    assertNull(summary.rounds().getFirst().outcomes().getFirst().correct());
  }

  @Test
  void concurrentTwoAnswersAndTimeoutCloseExactlyOnce() throws Exception {
    start();
    open();
    var barrier = new CyclicBarrier(3);
    var executor = Executors.newFixedThreadPool(3);
    try {
      List<Future<?>> jobs = new ArrayList<>();
      for (int player = 0; player < 2; player++) {
        int p = player;
        jobs.add(
            executor.submit(
                () -> {
                  try {
                    barrier.await();
                    command(p, "ANSWER", "questionId", 1, "answer", "A");
                  } catch (ProtocolException ignored) {
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
      }
      jobs.add(
          executor.submit(
              () -> {
                try {
                  barrier.await();
                  manager.locked(match, () -> manager.closeRound(match));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
      for (var j : jobs) j.get(2, TimeUnit.SECONDS);
      assertEquals(1, match.completed);
      assertEquals(1, match.rounds.size());
      assertEquals(2, events.stream().filter(e -> e.type() == MessageType.QUESTION_RESULT).count());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void questionPreparationTimeoutAbortsStartedMatch() {
    start();
    scheduler.advance(Duration.ofSeconds(5));
    assertEquals(FinishReason.ABORTED, saved.getFirst().finishReason());
    assertEquals(ReasonCode.CLIENT_NOT_READY, saved.getFirst().reasonCode());
    assertEquals(1, saved.getFirst().openedRounds());
    assertEquals(0, saved.getFirst().completedRounds());
  }

  @Test
  void duplicateReadyCannotExtendDeadline() {
    long deadline = match.deadline;
    command(0, "MATCH_READY");
    scheduler.advance(Duration.ofSeconds(1));
    command(0, "MATCH_READY");
    assertEquals(deadline, match.deadline);
    command(1, "MATCH_READY");
    scheduler.advance(Duration.ofSeconds(3));
    deadline = match.deadline;
    command(0, "QUESTION_READY", "questionId", 1);
    scheduler.advance(Duration.ofSeconds(1));
    command(0, "QUESTION_READY", "questionId", 1);
    assertEquals(deadline, match.deadline);
  }

  @Test
  void resultExpiresAndCanNoLongerSnapshot() {
    start();
    open();
    command(0, "EXIT_MATCH");
    scheduler.advance(Duration.ofSeconds(60));
    assertEquals(MatchPhase.CLOSED, match.phase());
    assertTrue(manager.find(match.id).isEmpty());
    assertThrows(ProtocolException.class, () -> command(1, "MATCH_SNAPSHOT_REQUEST"));
  }

  @Test
  void chatLimiterAndIdempotenceAreIndependentFromAnswer() {
    start();
    open();
    UUID request = UUID.randomUUID();
    var c =
        new Envelope(
            1, MessageType.CHAT, request, match.id, null, null, MatchManager.obj("text", " hi "));
    manager.handle(ServerFixtures.players().getFirst(), c);
    manager.handle(ServerFixtures.players().getFirst(), c);
    long chatIds =
        events.stream()
            .filter(e -> e.type() == MessageType.CHAT_MESSAGE)
            .map(e -> e.payload().path("chatMessageId").asText())
            .distinct()
            .count();
    assertEquals(1, chatIds);
    for (int i = 0; i < 4; i++) command(0, "CHAT", "text", "message");
    assertThrows(ProtocolException.class, () -> command(0, "CHAT", "text", "sixth"));
    command(0, "ANSWER", "questionId", 1, "answer", "A");
    assertTrue(match.accepted.containsKey(101L));
  }

  @Test
  void scoresAtExactDeadlineButRejectsOneNanosecondAfter() {
    start();
    open();
    clock.advance(Duration.ofSeconds(15));
    command(0, "ANSWER", "questionId", 1, "answer", "A");
    assertEquals(1, match.accepted.get(101L).points());
    clock.advance(Duration.ofNanos(1));
    ProtocolException error =
        assertThrows(
            ProtocolException.class, () -> command(1, "ANSWER", "questionId", 1, "answer", "A"));
    assertEquals("LATE_ANSWER", error.code());
  }

  @Test
  void duplicateAnswerAcknowledgesOriginalAfterClose() {
    start();
    open();
    var c =
        new Envelope(
            1,
            MessageType.ANSWER,
            UUID.randomUUID(),
            match.id,
            match.roundId,
            null,
            MatchManager.obj("questionId", 1, "answer", "A"));
    manager.handle(ServerFixtures.players().getFirst(), c);
    command(1, "ANSWER", "questionId", 1, "answer", "A");
    manager.handle(ServerFixtures.players().getFirst(), c);
    assertEquals(3, match.scores[0]);
    assertEquals(MessageType.ANSWER_ACK, events.getLast().type());
  }

  @Test
  void constructorRejectsDuplicatesWrongDistributionAndCallerMutation() {
    var questions = new ArrayList<>(ServerFixtures.questions());
    questions.set(1, questions.getFirst());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.prepare(
                UUID.randomUUID(), ServerFixtures.quiz(), ServerFixtures.players(), questions));
    var source = new ArrayList<>(ServerFixtures.questions());
    var copy =
        manager.prepare(UUID.randomUUID(), ServerFixtures.quiz(), ServerFixtures.players(), source);
    source.clear();
    assertEquals(10, copy.questions.size());
    assertThrows(UnsupportedOperationException.class, () -> copy.questions.clear());
  }

  @Test
  void registeredPreparedDisconnectCannotReviveAtActivation() {
    var prepared =
        manager.prepare(
            UUID.randomUUID(),
            ServerFixtures.quiz(),
            ServerFixtures.players(),
            ServerFixtures.questions());
    manager.registerPrepared(prepared);
    manager.onDisconnected(ServerFixtures.players().getFirst());
    assertEquals(MatchPhase.CANCELLED, prepared.phase());
    assertThrows(ProtocolException.class, () -> manager.activate(prepared));
    assertFalse(manager.find(prepared.id).isPresent());
  }
}
