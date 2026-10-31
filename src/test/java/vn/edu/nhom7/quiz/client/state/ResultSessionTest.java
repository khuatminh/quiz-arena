package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class ResultSessionTest {
  @Test
  void expiresButRetainsReviewAndCanLeaveLocally() {
    var r = new ClientStateReducer();
    var p = JsonNodeFactory.instance.objectNode().put("persistenceStatus", "PENDING");
    UUID m = UUID.randomUUID();
    var s =
        r.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, m, null, 1L, p),
            0);
    s = r.apply(s, new Envelope(1, MessageType.MATCH_RESULT, null, m, null, 2L, p), 0);
    assertEquals(m, s.resultMatchId());
    s = r.apply(s, new Envelope(1, MessageType.RESULT_SESSION_CLOSED, null, m, null, 3L, p), 0);
    assertNull(s.activeMatchId());
    assertFalse(s.resultAttached());
    assertNotNull(s.payload(MessageType.MATCH_RESULT));
    assertEquals("LOBBY", s.lobby().phase());
  }
}
