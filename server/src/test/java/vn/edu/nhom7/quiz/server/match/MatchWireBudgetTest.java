package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.support.*;

class MatchWireBudgetTest {
  private static List<ParticipantSummary> players() {
    return List.of(
        new ParticipantSummary(101, "Player one", "avatar-01", 0, 0),
        new ParticipantSummary(102, "Player two", "avatar-02", 0, 0));
  }

  @Test
  void acceptsOrdinaryFiveHundredCharacterQuestions() {
    var questions =
        ServerFixtures.questions().stream()
            .map(
                q ->
                    new QuestionSnapshot(
                        q.questionId(),
                        q.quizId(),
                        q.questionType(),
                        "a".repeat(500),
                        q.options(),
                        q.answerKeyJson(),
                        "Explanation",
                        null,
                        null))
            .toList();
    assertDoesNotThrow(() -> MatchWireBudget.validate(ServerFixtures.quiz(), questions, players()));
  }

  @Test
  void rejectsAggregateEvenWhenIndividualUnicodeQuestionsAreLegal() {
    var questions =
        ServerFixtures.questions().stream()
            .map(
                q ->
                    new QuestionSnapshot(
                        q.questionId(),
                        q.quizId(),
                        q.questionType(),
                        "😀".repeat(500),
                        q.options().stream()
                            .map(o -> new Payloads.Option(o.id(), "😀".repeat(120)))
                            .toList(),
                        q.answerKeyJson(),
                        "😀".repeat(500),
                        null,
                        null))
            .toList();
    ProtocolException error =
        assertThrows(
            ProtocolException.class,
            () -> MatchWireBudget.validate(ServerFixtures.quiz(), questions, players()));
    assertEquals("QUIZ_UNAVAILABLE", error.code());
  }

  @Test
  void saveNotificationRetainsSequenceAfterResultSessionCleanup() {
    var clock = new FakeGameClock();
    var scheduler = new ManualGameScheduler(clock);
    var transport = new RecordingTransport();
    var sink = new RecordingResultSink();
    var manager =
        new MatchManager(
            clock,
            scheduler,
            transport,
            sink,
            id -> players().get(id == 101 ? 0 : 1),
            id -> true,
            (id, match) -> {});
    var match =
        manager.prepare(
            UUID.randomUUID(),
            ServerFixtures.quiz(),
            ServerFixtures.players(),
            ServerFixtures.questions());
    manager.activate(match);
    manager.locked(
        match,
        () -> {
          match.startedAt = clock.instant();
          manager.finish(match, FinishReason.ABORTED, ReasonCode.SERVER_SHUTDOWN, null);
        });
    long before = match.sequence;
    scheduler.advance(java.time.Duration.ofSeconds(60));
    assertTrue(manager.find(match.id).isEmpty());
    UUID fresh = UUID.randomUUID();
    manager.publishSaveStatus(
        match.terminal, "SAVED", false, clock.instant().toEpochMilli(), List.of(fresh));
    var event = transport.events(fresh).getFirst();
    assertEquals(MessageType.MATCH_SAVE_STATUS, event.type());
    assertTrue(event.eventSeq() > before);
    assertEquals(match.id, event.matchId());
    assertFalse(manager.savingMatches.containsKey(match.id));
  }
}
