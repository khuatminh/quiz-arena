package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class ResyncStateTest {
  @Test
  void answeringSnapshotIsOpenAndAccepted() {
    var r = new ClientStateReducer();
    UUID m = UUID.randomUUID();
    var f = JsonNodeFactory.instance;
    var s =
        r.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, m, null, 1L, f.objectNode()),
            0);
    var p = f.objectNode().put("phase", "ANSWERING");
    p.put("acceptedAnswer", "A");
    s = r.apply(s, new Envelope(1, MessageType.MATCH_SNAPSHOT, null, m, null, 5L, p), 0);
    assertEquals("OPEN", s.phase());
    assertTrue(s.accepted());
    assertFalse(s.canAnswer());
  }
}
