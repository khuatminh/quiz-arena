package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class ClientStateReducerTest {
  @Test
  void ignoresOldSeqButAllowsPrivateGaps() {
    var r = new ClientStateReducer();
    var s = ClientState.initial();
    UUID m = UUID.randomUUID();
    var p = JsonNodeFactory.instance.objectNode();
    s = r.apply(s, new Envelope(1, MessageType.MATCH_START, null, m, null, 1L, p), 0);
    s = r.apply(s, new Envelope(1, MessageType.QUESTION_OPEN, null, m, null, 10L, p), 0);
    assertEquals("OPEN", s.phase());
    assertSame(
        s, r.apply(s, new Envelope(1, MessageType.MATCH_COUNTDOWN, null, m, null, 9L, p), 0));
  }

  @Test
  void oldSaveDoesNotReplaceMatch() {
    var r = new ClientStateReducer();
    UUID old = UUID.randomUUID(), current = UUID.randomUUID();
    var p = JsonNodeFactory.instance.objectNode();
    var s =
        r.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, current, null, 1L, p),
            0);
    p.put("status", "SAVED");
    s = r.apply(s, new Envelope(1, MessageType.MATCH_SAVE_STATUS, null, old, null, 99L, p), 0);
    assertEquals(current, s.activeMatchId());
    assertEquals("SAVED", s.persistenceByMatch().get(old));
  }
}
