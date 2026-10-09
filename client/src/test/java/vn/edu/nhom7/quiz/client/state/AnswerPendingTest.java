package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class AnswerPendingTest {
  @Test
  void invalidAnswerUnlocksOnlyOpen() {
    var r = new ClientStateReducer();
    var p = JsonNodeFactory.instance.objectNode();
    UUID m = UUID.randomUUID();
    var s =
        r.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, m, null, 1L, p),
            0);
    s = r.apply(s, new Envelope(1, MessageType.QUESTION_OPEN, null, m, null, 2L, p), 0).pending();
    assertFalse(s.canAnswer());
    s =
        r.apply(
            s,
            new Envelope(
                1, MessageType.ERROR, null, null, null, null, p.put("code", "INVALID_ANSWER")),
            0);
    assertTrue(s.canAnswer());
    s = r.apply(s, new Envelope(1, MessageType.ANSWER_ACK, null, m, null, 3L, p), 0);
    assertFalse(s.canAnswer());
    assertTrue(s.accepted());
  }
}
