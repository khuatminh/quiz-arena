package vn.edu.nhom7.quiz.server.system;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.nhom7.quiz.server.support.SystemTestSupport.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.*;
import vn.edu.nhom7.quiz.server.support.*;

class ProtocolRoutingIT {
  private final ProtocolCodec codec = new ProtocolCodec();

  private SocketHarness hello(ServerRuntime runtime) throws Exception {
    var s = SocketHarness.connect("127.0.0.1", runtime.port());
    s.send(command(MessageType.HELLO, null, null, new Payloads.Hello("1", "1")));
    s.await(MessageType.HELLO_ACK, WAIT);
    return s;
  }

  @Test
  void unauthenticatedAndWrongDirectionCommandsAreRejected() throws Exception {
    try (var runtime = new ServerRuntime(TestDatabase.factory())) {
      runtime.start("127.0.0.1", 0);
      try (var s = hello(runtime)) {
        var answer =
            command(
                MessageType.ANSWER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Payloads.Answer(1, codec.mapper().valueToTree("A")));
        s.send(answer);
        assertEquals(
            "UNAUTHENTICATED",
            s.await(MessageType.ERROR, answer.requestId(), WAIT).payload().path("code").asText());
        var event =
            codec.envelope(
                MessageType.PONG, UUID.randomUUID(), null, null, null, new Payloads.Pong(1, 1));
        s.send(event);
        assertEquals(
            "INVALID_MESSAGE",
            s.await(MessageType.ERROR, event.requestId(), WAIT).payload().path("code").asText());
      }
    }
  }

  @Test
  void authReplayReturnsSameUserAndRejectsChangedCredentials() throws Exception {
    try (var runtime = new ServerRuntime(TestDatabase.factory())) {
      runtime.start("127.0.0.1", 0);
      try (var s = hello(runtime)) {
        String username = "rp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var register =
            command(
                MessageType.REGISTER,
                null,
                null,
                new Payloads.Register(username, "Password123!", "Replay", "avatar-01"));
        s.send(register);
        var first = s.await(MessageType.REGISTER_RESULT, register.requestId(), WAIT);
        s.send(register);
        assertEquals(first, s.await(MessageType.REGISTER_RESULT, register.requestId(), WAIT));
        var changed =
            codec.envelope(
                MessageType.REGISTER,
                register.requestId(),
                null,
                null,
                null,
                new Payloads.Register(username, "Different123!", "Replay", "avatar-01"));
        s.send(changed);
        assertEquals(
            "REQUEST_ID_REUSED",
            s.await(MessageType.ERROR, register.requestId(), WAIT).payload().path("code").asText());
      }
    }
  }

  @Test
  void duplicateLoginIsRejectedAndLogoutReleasesInvitation() throws Exception {
    try (var runtime = new ServerRuntime(TestDatabase.factory())) {
      runtime.start("127.0.0.1", 0);
      try (var one = login(runtime);
          var two = login(runtime);
          var duplicate = hello(runtime)) {
        var profile =
            command(MessageType.PROFILE_REQUEST, null, null, new Payloads.ProfileRequest());
        one.socket().send(profile);
        String username =
            one.socket()
                .await(MessageType.PROFILE, profile.requestId(), WAIT)
                .payload()
                .path("username")
                .asText();
        var attempt =
            command(MessageType.LOGIN, null, null, new Payloads.Login(username, "ValidPass123!"));
        duplicate.send(attempt);
        assertEquals(
            "ALREADY_LOGGED_IN",
            duplicate
                .await(MessageType.ERROR, attempt.requestId(), WAIT)
                .payload()
                .path("code")
                .asText());
        one.socket()
            .send(
                command(
                    MessageType.CHALLENGE, null, null, new Payloads.Challenge(two.userId(), 1)));
        two.socket().await(MessageType.CHALLENGE_RECEIVED, WAIT);
        one.socket().await(MessageType.CHALLENGE_ACK, WAIT);
        var logout = command(MessageType.LOGOUT, null, null, new Payloads.Logout());
        one.socket().send(logout);
        one.socket().await(MessageType.LOGOUT_ACK, logout.requestId(), WAIT);
        assertEquals(
            "INVALIDATED",
            two.socket()
                .await(MessageType.CHALLENGE_CLOSED, WAIT)
                .payload()
                .path("status")
                .asText());
        assertEquals("FREE", runtime.sessions.status(two.userId()));
        assertTrue(runtime.sessions.user(one.userId()).isEmpty());
      }
    }
  }
}
