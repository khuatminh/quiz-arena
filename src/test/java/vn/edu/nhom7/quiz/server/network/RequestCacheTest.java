package vn.edu.nhom7.quiz.server.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class RequestCacheTest {
  @Test
  void detectsReuseAndExpires() {
    var c = new RequestCache();
    var id = UUID.randomUUID();
    var e =
        new Envelope(
            1,
            MessageType.LOGOUT_ACK,
            id,
            null,
            null,
            null,
            new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
    c.remember(id, new byte[] {1}, e, 0);
    assertEquals(e, c.find(id, new byte[] {1}, 1).orElseThrow());
    assertThrows(ProtocolException.class, () -> c.find(id, new byte[] {2}, 2));
    assertTrue(c.find(id, new byte[] {1}, 60_000_000_001L).isEmpty());
  }

  @Test
  void limiterWindowDoesNotBlockUnrelatedCommands() {
    var l = new RateLimiter(2, 100);
    assertTrue(l.allow(0));
    assertTrue(l.allow(0));
    assertFalse(l.allow(0));
    assertTrue(l.allow(100));
  }
}
