package vn.edu.nhom7.quiz.server.system;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.nhom7.quiz.server.support.SystemTestSupport.*;

import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.*;
import vn.edu.nhom7.quiz.server.support.*;

class LobbyFlowIT {
  @Test
  void fragmentedHandshakeRegisterLoginQuizPresenceAndChallenge() throws Exception {
    var clock = new FakeGameClock();
    var timer = new FakeGameScheduler(clock);
    try (var runtime = new ServerRuntime(TestDatabase.factory(), clock, timer)) {
      runtime.start("127.0.0.1", 0);
      try (var one = login(runtime);
          var two = login(runtime)) {
        one.socket()
            .send(
                command(
                    MessageType.QUIZ_LIST_REQUEST,
                    null,
                    null,
                    new Payloads.QuizListRequest(null, 1, 20)));
        var quizzes = one.socket().await(MessageType.QUIZ_LIST, WAIT);
        assertTrue(quizzes.payload().path("items").size() >= 3);
        one.socket()
            .send(
                command(
                    MessageType.ONLINE_LIST_REQUEST, null, null, new Payloads.OnlineListRequest()));
        assertTrue(
            one.socket().await(MessageType.ONLINE_LIST, WAIT).payload().path("users").size() >= 1);
        var id = challenge(one, two);
        assertEquals("BUSY", runtime.sessions.status(one.userId()));
        assertEquals(id, runtime.sessions.matchOf(two.userId()).orElseThrow());
      }
    }
  }

  @Test
  void rejectedCancelledAndExpiredInvitationsReleaseBothPlayers() throws Exception {
    var clock = new FakeGameClock();
    var timer = new FakeGameScheduler(clock);
    try (var runtime = new ServerRuntime(TestDatabase.factory(), clock, timer)) {
      runtime.start("127.0.0.1", 0);
      try (var one = login(runtime);
          var two = login(runtime)) {
        for (String terminal : new String[] {"REJECTED", "CANCELLED", "EXPIRED"}) {
          one.socket()
              .send(
                  command(
                      MessageType.CHALLENGE, null, null, new Payloads.Challenge(two.userId(), 1)));
          var received = two.socket().await(MessageType.CHALLENGE_RECEIVED, WAIT);
          one.socket().await(MessageType.CHALLENGE_ACK, WAIT);
          java.util.UUID id =
              java.util.UUID.fromString(received.payload().path("challengeId").asText());
          switch (terminal) {
            case "REJECTED" ->
                two.socket()
                    .send(
                        command(
                            MessageType.CHALLENGE_REJECT,
                            null,
                            null,
                            new Payloads.ChallengeReject(id)));
            case "CANCELLED" ->
                one.socket()
                    .send(
                        command(
                            MessageType.CHALLENGE_CANCEL,
                            null,
                            null,
                            new Payloads.ChallengeCancel(id)));
            case "EXPIRED" -> timer.advance(java.time.Duration.ofSeconds(20));
            default -> throw new AssertionError();
          }
          assertEquals(
              terminal,
              one.socket()
                  .await(MessageType.CHALLENGE_CLOSED, WAIT)
                  .payload()
                  .path("status")
                  .asText());
          assertEquals(
              terminal,
              two.socket()
                  .await(MessageType.CHALLENGE_CLOSED, WAIT)
                  .payload()
                  .path("status")
                  .asText());
          assertEquals("FREE", runtime.sessions.status(one.userId()));
          assertEquals("FREE", runtime.sessions.status(two.userId()));
        }
      }
    }
  }
}
