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
  void aNewMatchClearsThePreviousReadyStatus() {
    var reducer = new ClientStateReducer();
    var empty = JsonNodeFactory.instance.objectNode();
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    var state =
        reducer.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, first, null, 1L, empty),
            0);
    var ready =
        JsonNodeFactory.instance
            .objectNode()
            .set("readyUserIds", JsonNodeFactory.instance.arrayNode().add(101));
    state =
        reducer.apply(
            state, new Envelope(1, MessageType.READY_STATUS, null, first, null, 2L, ready), 0);
    assertNotNull(state.payload(MessageType.READY_STATUS));
    state =
        reducer.apply(
            state, new Envelope(1, MessageType.MATCH_START, null, second, null, 1L, empty), 0);
    assertNull(state.payload(MessageType.READY_STATUS));
    assertEquals("WAITING_READY", state.phase());
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
