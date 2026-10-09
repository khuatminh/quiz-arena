package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.client.ui.GameView;
import vn.edu.nhom7.quiz.common.protocol.*;

class GamePhaseStateTest {
  @Test
  void localZeroDoesNotAdvance() {
    assertEquals(0, GameView.remainingAt(15000, 1000000000, 17000000000L));
    assertEquals(13000, GameView.remainingAt(15000, 1000000000, 3000000000L));
  }

  @Test
  void countdownCannotOpenControls() {
    var r = new ClientStateReducer();
    var p = JsonNodeFactory.instance.objectNode();
    UUID m = UUID.randomUUID();
    var s =
        r.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, m, null, 1L, p),
            0);
    assertEquals("WAITING_READY", s.phase());
    s = r.apply(s, new Envelope(1, MessageType.QUESTION, null, m, UUID.randomUUID(), 2L, p), 0);
    assertFalse(s.canAnswer());
    s = r.apply(s, new Envelope(1, MessageType.ROUND_COUNTDOWN, null, m, null, 3L, p), 0);
    assertFalse(s.canAnswer());
    s = r.apply(s, new Envelope(1, MessageType.QUESTION_OPEN, null, m, null, 4L, p), 0);
    assertTrue(s.canAnswer());
  }
}
