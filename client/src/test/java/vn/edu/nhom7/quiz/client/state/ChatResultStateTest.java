package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class ChatResultStateTest {
  @Test
  void chatIdDedupAndNewMatchClearsOldSession() {
    var r = new ClientStateReducer();
    UUID m = UUID.randomUUID(), chat = UUID.randomUUID();
    var p = JsonNodeFactory.instance.objectNode().put("chatMessageId", chat.toString());
    var s =
        r.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, m, null, 1L, p),
            0);
    s = r.apply(s, new Envelope(1, MessageType.CHAT_MESSAGE, null, m, null, 2L, p), 0);
    s = r.apply(s, new Envelope(1, MessageType.CHAT_MESSAGE, null, m, null, 3L, p), 0);
    assertEquals(1, s.chatById().size());
    s = r.apply(s, new Envelope(1, MessageType.RESULT_SESSION_CLOSED, null, m, null, 4L, p), 0);
    s =
        r.apply(
            s, new Envelope(1, MessageType.MATCH_START, null, UUID.randomUUID(), null, 1L, p), 0);
    assertTrue(s.chatById().isEmpty());
    assertFalse(s.data().containsKey(MessageType.RESULT_SESSION_CLOSED));
  }
}
