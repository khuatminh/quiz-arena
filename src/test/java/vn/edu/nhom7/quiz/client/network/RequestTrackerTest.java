package vn.edu.nhom7.quiz.client.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class RequestTrackerTest {
  @Test
  void timeoutAndEpoch() {
    var t = new RequestTracker();
    UUID id = UUID.randomUUID();
    t.register(id, MessageType.ANSWER, 0);
    assertTrue(t.expired(2_000_000_000L).contains(id));
    assertEquals(1, t.size());
    t.clear();
    assertEquals(0, t.size());
  }
}
