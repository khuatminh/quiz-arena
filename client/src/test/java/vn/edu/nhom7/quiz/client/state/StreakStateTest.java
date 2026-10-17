package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class StreakStateTest {
  private final ClientStateReducer reducer = new ClientStateReducer();
  private long seq = 1;

  private ClientState result(ClientState s, UUID match, long question, boolean self, boolean other) {
    var p = JsonNodeFactory.instance.objectNode().put("questionId", question);
    var outcomes = p.putArray("outcomes");
    outcomes.addObject().put("userId", 101).put("correct", self);
    outcomes.addObject().put("userId", 102).put("correct", other);
    return reducer.apply(
        s, new Envelope(1, MessageType.QUESTION_RESULT, null, match, null, seq++, p), 0);
  }

  private static int streak(ClientState s, long user) {
    return s.payload(MessageType.QUESTION_RESULT).path("streaks").path(Long.toString(user)).asInt();
  }

  @Test
  void countsCorrectAnswersInARowAndResetsOnAMiss() {
    UUID match = UUID.randomUUID();
    var empty = JsonNodeFactory.instance.objectNode();
    var s =
        reducer.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, match, null, seq++, empty),
            0);
    for (int q = 1; q <= 5; q++) s = result(s, match, q, true, q != 3);
    assertEquals(5, streak(s, 101));
    assertEquals(2, streak(s, 102));
    s = result(s, match, 6, false, true);
    assertEquals(0, streak(s, 101));
    assertEquals(3, streak(s, 102));
  }

  @Test
  void aRepeatedResultDoesNotCountTwiceAndANewMatchStartsAtZero() {
    UUID match = UUID.randomUUID();
    var empty = JsonNodeFactory.instance.objectNode();
    var s =
        reducer.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, match, null, seq++, empty),
            0);
    s = result(s, match, 1, true, true);
    s = result(s, match, 1, true, true);
    assertEquals(1, streak(s, 101));
    UUID next = UUID.randomUUID();
    seq = 1;
    s = reducer.apply(s, new Envelope(1, MessageType.MATCH_START, null, next, null, seq++, empty), 0);
    s = result(s, next, 1, true, false);
    assertEquals(1, streak(s, 101));
    assertEquals(0, streak(s, 102));
  }
}
