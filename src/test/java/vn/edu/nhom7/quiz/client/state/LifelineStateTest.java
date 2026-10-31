package vn.edu.nhom7.quiz.client.state;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class LifelineStateTest {
  private final ClientStateReducer reducer = new ClientStateReducer();

  @Test
  void snapshotRestoresTheCurrentQuestionsLifelineAndANewMatchClearsIt() {
    UUID match = UUID.randomUUID();
    var p = JsonNodeFactory.instance.objectNode().put("phase", "ANSWERING");
    p.putObject("question").put("questionId", 4);
    var lifelines = p.putObject("lifelines");
    lifelines.putArray("remaining").add("DOUBLE_POINTS");
    lifelines.putArray("active").add("REMOVE_ONE");
    lifelines.putArray("removedOptionIds").add("C");
    var s =
        reducer.apply(
            ClientState.initial(),
            new Envelope(1, MessageType.MATCH_START, null, match, null, 1L, p.objectNode()),
            0);
    s = reducer.apply(s, new Envelope(1, MessageType.MATCH_SNAPSHOT, null, match, null, 2L, p), 0);
    var stored = s.payload(MessageType.LIFELINE_RESULT);
    assertEquals(4, stored.path("questionId").asLong());
    assertEquals("REMOVE_ONE", stored.path("kind").asText());
    assertEquals("C", stored.path("removedOptionIds").get(0).asText());
    assertEquals(1, stored.path("remaining").size());
    s =
        reducer.apply(
            s,
            new Envelope(
                1,
                MessageType.MATCH_START,
                null,
                UUID.randomUUID(),
                null,
                1L,
                JsonNodeFactory.instance.objectNode()),
            0);
    assertNull(s.payload(MessageType.LIFELINE_RESULT));
  }

  @Test
  void statusKeepsEachPlayersLatestLifeline() {
    UUID match = UUID.randomUUID();
    var s =
        reducer.apply(
            ClientState.initial(),
            new Envelope(
                1,
                MessageType.MATCH_START,
                null,
                match,
                null,
                1L,
                JsonNodeFactory.instance.objectNode()),
            0);
    long seq = 2;
    for (long user : List.of(102L, 101L))
      s =
          reducer.apply(
              s,
              new Envelope(
                  1,
                  MessageType.LIFELINE_STATUS,
                  null,
                  match,
                  null,
                  seq++,
                  JsonNodeFactory.instance
                      .objectNode()
                      .put("userId", user)
                      .put("questionId", 3)
                      .put("kind", user == 102 ? "DOUBLE_POINTS" : "FIFTY_FIFTY")),
              0);
    var byUser = s.payload(MessageType.LIFELINE_STATUS).path("byUser");
    assertEquals("DOUBLE_POINTS", byUser.path("102").path("kind").asText());
    assertEquals("FIFTY_FIFTY", byUser.path("101").path("kind").asText());
  }
}
