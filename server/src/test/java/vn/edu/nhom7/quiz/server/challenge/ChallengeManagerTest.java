package vn.edu.nhom7.quiz.server.challenge;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.match.*;
import vn.edu.nhom7.quiz.server.persistence.ResultSink;
import vn.edu.nhom7.quiz.server.session.*;
import vn.edu.nhom7.quiz.server.support.*;

class ChallengeManagerTest {
  SessionRegistry sessions;
  OnlinePresenceService presence;
  ChallengeManager challenges;
  FakeGameClock clock;
  FakeGameScheduler scheduler;
  List<Envelope> events;
  Set<UUID> reserved;
  List<Runnable> jobs;
  ProtocolCodec codec = new ProtocolCodec();
  List<SessionContext> users;
  boolean pinnedLoad;

  @BeforeEach
  void setup() {
    clock = new FakeGameClock();
    scheduler = new FakeGameScheduler(clock);
    sessions = new SessionRegistry();
    events = new ArrayList<>();
    reserved = new HashSet<>();
    jobs = new ArrayList<>();
    users = new ArrayList<>(ServerFixtures.players());
    users.add(new SessionContext(UUID.randomUUID(), 103));
    for (var u : users) sessions.tryAuthenticate(u.connectionId(), u.userId());
    vn.edu.nhom7.quiz.server.network.OutboundTransport transport =
        (id, e) -> {
          codec.encode(e);
          events.add(e);
          return true;
        };
    presence = new OnlinePresenceService(sessions, transport);
    for (var u : users)
      presence.cache(
          new Payloads.Profile(u.userId(), "u" + u.userId(), "User", "avatar-01", 0, 0, 0, 0, 0));
    var sink =
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
        };
    var matches =
        new MatchManager(
            clock,
            scheduler,
            transport,
            sink,
            id -> new ParticipantSummary(id, "User", "avatar-01", 0, 0),
            id -> true,
            sessions::detach);
    challenges =
        new ChallengeManager(
            sessions,
            presence,
            matches,
            new vn.edu.nhom7.quiz.server.quiz.QuizRepository() {
              public List<QuestionSnapshot> loadMatchQuestions(
                  long id, java.util.random.RandomGenerator random) {
                return ServerFixtures.questions();
              }

              public <T> T withMatchQuestions(
                  Payloads.QuizSummary quiz,
                  java.util.random.RandomGenerator random,
                  java.util.function.BiFunction<Payloads.QuizSummary, List<QuestionSnapshot>, T>
                      action) {
                pinnedLoad = true;
                return action.apply(quiz, ServerFixtures.questions());
              }
            },
            sink,
            transport,
            clock,
            scheduler,
            jobs::add,
            id -> ServerFixtures.quiz());
  }

  void send(int user, MessageType type, Object payload) {
    challenges.handle(
        users.get(user), codec.envelope(type, UUID.randomUUID(), null, null, null, payload));
  }

  UUID invite(int sender, int target) {
    send(sender, MessageType.CHALLENGE, new Payloads.Challenge(users.get(target).userId(), 1));
    jobs.removeFirst().run();
    return events.stream()
        .filter(e -> e.type() == MessageType.CHALLENGE_ACK)
        .reduce((a, b) -> b)
        .map(e -> UUID.fromString(e.payload().path("challengeId").asText()))
        .orElseThrow();
  }

  @Test
  void sharedOpponentHasOneReservationAndExpiryReleasesBoth() {
    invite(0, 1);
    send(2, MessageType.CHALLENGE, new Payloads.Challenge(102, 1));
    jobs.removeFirst().run();
    assertEquals("USER_BUSY", events.getLast().payload().path("code").asText());
    assertEquals("CHALLENGING", sessions.status(101));
    scheduler.advance(Duration.ofSeconds(20));
    assertEquals("FREE", sessions.status(101));
    assertEquals("FREE", sessions.status(102));
    assertTrue(reserved.isEmpty());
  }

  @Test
  void acceptDuplicateCreatesOnlyOneMatch() {
    UUID id = invite(0, 1);
    send(1, MessageType.CHALLENGE_ACCEPT, new Payloads.ChallengeAccept(id));
    assertThrows(
        ProtocolException.class,
        () -> send(1, MessageType.CHALLENGE_ACCEPT, new Payloads.ChallengeAccept(id)));
    jobs.removeFirst().run();
    var match = sessions.matchOf(101).orElseThrow();
    assertEquals(match, sessions.matchOf(102).orElseThrow());
    assertEquals(2, events.stream().filter(e -> e.type() == MessageType.MATCH_START).count());
    assertEquals(1, reserved.size());
    assertTrue(pinnedLoad, "Match activation must hold the pinned publication guard");
  }

  @Test
  void timedOutLoadAndLateCompletionDoNotAttach() {
    UUID id = invite(0, 1);
    send(1, MessageType.CHALLENGE_ACCEPT, new Payloads.ChallengeAccept(id));
    scheduler.advance(Duration.ofSeconds(5));
    jobs.removeFirst().run();
    assertEquals("FREE", sessions.status(101));
    assertEquals("FREE", sessions.status(102));
    assertTrue(reserved.isEmpty());
    assertFalse(events.stream().anyMatch(e -> e.type() == MessageType.MATCH_START));
  }

  @Test
  void disconnectDuringLoadInvalidatesAndReleasesSlot() {
    UUID id = invite(0, 1);
    send(1, MessageType.CHALLENGE_ACCEPT, new Payloads.ChallengeAccept(id));
    sessions.removeConnection(users.get(1).connectionId());
    challenges.onDisconnected(users.get(1));
    jobs.removeFirst().run();
    assertEquals("FREE", sessions.status(101));
    assertTrue(reserved.isEmpty());
    assertFalse(events.stream().anyMatch(e -> e.type() == MessageType.MATCH_START));
  }

  @Test
  void actorValidationDoesNotReleaseOthersInvitation() {
    UUID id = invite(0, 1);
    assertThrows(
        ProtocolException.class,
        () -> send(2, MessageType.CHALLENGE_ACCEPT, new Payloads.ChallengeAccept(id)));
    assertThrows(
        ProtocolException.class,
        () -> send(1, MessageType.CHALLENGE_CANCEL, new Payloads.ChallengeCancel(id)));
    send(0, MessageType.CHALLENGE_CANCEL, new Payloads.ChallengeCancel(id));
    assertEquals("FREE", sessions.status(101));
    assertEquals(2, events.stream().filter(e -> e.type() == MessageType.CHALLENGE_CLOSED).count());
  }
}
