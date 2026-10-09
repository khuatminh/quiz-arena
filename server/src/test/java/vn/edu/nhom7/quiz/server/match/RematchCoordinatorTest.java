package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.persistence.*;
import vn.edu.nhom7.quiz.server.session.*;
import vn.edu.nhom7.quiz.server.support.*;

class RematchCoordinatorTest {
  FakeGameClock clock = new FakeGameClock();
  FakeGameScheduler scheduler = new FakeGameScheduler(clock);
  SessionRegistry registry = new SessionRegistry();
  List<Envelope> events = new ArrayList<>();
  List<Runnable> loads = new ArrayList<>();
  Set<UUID> reserved = new HashSet<>();
  MatchManager manager;
  Match match;

  @BeforeEach
  void setup() {
    ProtocolCodec codec = new ProtocolCodec();
    manager =
        new MatchManager(
            clock,
            scheduler,
            (id, e) -> {
              codec.encode(e);
              events.add(e);
              return true;
            },
            new ResultSink() {
              public void submit(MatchSummary s) {}

              public boolean canCreateMatch() {
                return true;
              }

              public boolean reserveMatch(UUID id) {
                return reserved.add(id);
              }

              public void cancelReservation(UUID id) {
                reserved.remove(id);
              }
            },
            id -> new ParticipantSummary(id, "User" + id, "avatar-1", 0, 0),
            id -> true,
            registry::detach);
    var players = ServerFixtures.players();
    players.forEach(p -> registry.tryAuthenticate(p.connectionId(), p.userId()));
    UUID challenge = UUID.randomUUID(), id = UUID.randomUUID();
    assertTrue(registry.reservePair(101, 102, challenge));
    assertTrue(registry.attachReservedPair(challenge, id));
    match = manager.prepare(id, ServerFixtures.quiz(), players, ServerFixtures.questions());
    manager.activate(match);
    command(0, "MATCH_READY");
    command(1, "MATCH_READY");
    manager.locked(
        match, () -> manager.finish(match, FinishReason.COMPLETED, ReasonCode.NORMAL, null));
    manager.configureRematch(registry, (quiz, random) -> ServerFixtures.questions(), loads::add);
  }

  void command(int player, String type, Object... fields) {
    manager.handle(
        ServerFixtures.players().get(player),
        new Envelope(
            1,
            MessageType.valueOf(type),
            UUID.randomUUID(),
            match.id,
            null,
            null,
            MatchManager.obj(fields)));
  }

  @Test
  void rematchLoadsCurrentPinnedPublicationAndRejectsUnpublishedQuiz() {
    var current =
        new Payloads.QuizSummary(
            1, "New publication", 1, "General", null, "AVAILABLE", 10, "COMMUNITY", "Author", 77);
    manager.configureRematch(
        registry,
        new vn.edu.nhom7.quiz.server.quiz.QuizRepository() {
          public List<QuestionSnapshot> loadMatchQuestions(
              long quiz, java.util.random.RandomGenerator random) {
            fail("Rematch must load pinned summary");
            return List.of();
          }

          public Payloads.QuizSummary currentMatchQuiz(Payloads.QuizSummary old) {
            return current;
          }

          public List<QuestionSnapshot> loadMatchQuestions(
              Payloads.QuizSummary pinned, java.util.random.RandomGenerator random) {
            assertEquals(77, pinned.quizVersionId());
            return ServerFixtures.questions();
          }
        },
        loads::add);
    command(0, "REMATCH_REQUEST");
    command(1, "REMATCH_RESPONSE", "accept", true);
    loads.getFirst().run();
    var next = manager.find(registry.matchOf(101).orElseThrow()).orElseThrow();
    assertEquals(77, next.quiz.quizVersionId());
    assertEquals("New publication", next.quiz.title());
  }

  @Test
  void unavailablePublicationInvalidatesRematchWithoutReservation() {
    manager.configureRematch(
        registry,
        new vn.edu.nhom7.quiz.server.quiz.QuizRepository() {
          public List<QuestionSnapshot> loadMatchQuestions(
              long quiz, java.util.random.RandomGenerator random) {
            return ServerFixtures.questions();
          }

          public Payloads.QuizSummary currentMatchQuiz(Payloads.QuizSummary old) {
            throw new ProtocolException("QUIZ_UNAVAILABLE", "Quiz unavailable");
          }
        },
        loads::add);
    command(0, "REMATCH_REQUEST");
    command(1, "REMATCH_RESPONSE", "accept", true);
    loads.getFirst().run();
    assertEquals(match.id, registry.matchOf(101).orElseThrow());
    assertTrue(reserved.isEmpty());
    assertNull(match.rematchRequester);
  }

  @Test
  void secondRequestAcceptsAndFreshMatchTransfersWithoutFreeGap() {
    command(0, "REMATCH_REQUEST");
    command(0, "REMATCH_REQUEST");
    assertTrue(loads.isEmpty());
    command(1, "REMATCH_REQUEST");
    assertEquals(1, loads.size());
    loads.getFirst().run();
    UUID nextId = registry.matchOf(101).orElseThrow();
    assertNotEquals(match.id, nextId);
    assertEquals(nextId, registry.matchOf(102).orElseThrow());
    assertEquals("BUSY", registry.status(101));
    Match next = manager.find(nextId).orElseThrow();
    assertEquals(MatchPhase.WAITING_READY, next.phase());
    assertEquals(0, next.scores[0]);
    assertTrue(next.ready.isEmpty());
    assertEquals(1, reserved.size());
    assertFalse(manager.find(match.id).isPresent());
  }

  @Test
  void expiryWhileLoadingCannotCreateNewMatchOrReservation() {
    command(0, "REMATCH_REQUEST");
    command(1, "REMATCH_RESPONSE", "accept", true);
    scheduler.advance(Duration.ofSeconds(60));
    loads.getFirst().run();
    assertEquals(MatchPhase.CLOSED, match.phase());
    assertTrue(registry.matchOf(101).isEmpty());
    assertTrue(registry.matchOf(102).isEmpty());
    assertTrue(reserved.isEmpty());
  }

  @Test
  void pendingExpiryAndRejectionInvalidateAgreement() {
    command(0, "REMATCH_REQUEST");
    scheduler.advance(Duration.ofSeconds(20));
    assertNull(match.rematchRequester);
    command(0, "REMATCH_REQUEST");
    command(1, "REMATCH_RESPONSE", "accept", false);
    assertNull(match.rematchRequester);
    assertTrue(loads.isEmpty());
    assertThrows(ProtocolException.class, () -> command(1, "REMATCH_RESPONSE", "accept", true));
  }

  @Test
  void resultExpiryCapsRequestWindow() {
    scheduler.advance(Duration.ofSeconds(55));
    command(0, "REMATCH_REQUEST");
    assertEquals(match.resultDeadline, match.rematchExpiry);
    scheduler.advance(Duration.ofSeconds(5));
    assertTrue(registry.matchOf(101).isEmpty());
  }
}
