package vn.edu.nhom7.quiz.client.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Opt-in integration against the actual configured server and its isolated test database. */
class NetworkClientLiveProbe {
  private Envelope await(BlockingQueue<Envelope> queue, MessageType type) throws Exception {
    long end = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < end) {
      Envelope e = queue.poll(250, TimeUnit.MILLISECONDS);
      if (e == null) continue;
      if (e.type() == MessageType.ERROR) fail(e.payload().path("code").asText());
      if (e.type() == type) return e;
    }
    throw new AssertionError("Timed out waiting for " + type);
  }

  @Test
  void twoRealClientsRegisterLoginAndQuery() throws Exception {
    String port = System.getProperty("quiz.integrationPort");
    assertNotNull(port, "Supply -Dquiz.integrationPort for real-server integration");
    var codec = new ProtocolCodec();
    for (int i = 0; i < 2; i++) {
      var events = new LinkedBlockingQueue<Envelope>();
      try (var client = new NetworkClient(events::add, reason -> {})) {
        client.connect("localhost", Integer.parseInt(port)).get(5, TimeUnit.SECONDS);
        await(events, MessageType.HELLO_ACK);
        String username = "ui_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        assertTrue(
            client.send(
                codec.envelope(
                    MessageType.REGISTER,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    new Payloads.Register(
                        username, "TestPassword123!", "UI Test " + i, "avatar-01"))));
        await(events, MessageType.REGISTER_RESULT);
        assertTrue(
            client.send(
                codec.envelope(
                    MessageType.LOGIN,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    new Payloads.Login(username, "TestPassword123!"))));
        Envelope profile = await(events, MessageType.LOGIN_RESULT);
        assertEquals(username, profile.payload().path("profile").path("username").asText());
        assertTrue(
            client.send(
                codec.envelope(
                    MessageType.QUIZ_LIST_REQUEST,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    new Payloads.QuizListRequest(null, 1, 20))));
        assertFalse(await(events, MessageType.QUIZ_LIST).payload().path("items").isEmpty());
        assertTrue(
            client.send(
                codec.envelope(
                    MessageType.LOGOUT,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    new Payloads.Logout())));
        await(events, MessageType.LOGOUT_ACK);
      }
    }
  }
}
