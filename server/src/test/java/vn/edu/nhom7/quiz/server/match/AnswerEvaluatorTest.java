package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;
import vn.edu.nhom7.quiz.server.support.ServerFixtures;

class AnswerEvaluatorTest {
  private final AnswerEvaluator evaluator = new AnswerEvaluator();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void singleChoiceValidatesIdsAndTypes() throws Exception {
    var q = ServerFixtures.questions().getFirst();
    assertTrue(evaluator.evaluate(q, json.readTree("\"A\"")));
    assertFalse(evaluator.evaluate(q, json.readTree("\"B\"")));
    assertThrows(
        ProtocolException.class, () -> evaluator.evaluate(q, json.readTree("\"unknown\"")));
    assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, json.readTree("1")));
  }

  @Test
  void multipleRequiresExactUniqueKnownSet() throws Exception {
    var q = ServerFixtures.questions().get(4);
    assertTrue(evaluator.evaluate(q, json.readTree("[\"B\",\"A\"]")));
    assertFalse(evaluator.evaluate(q, json.readTree("[\"A\"]")));
    assertFalse(evaluator.evaluate(q, json.readTree("[\"A\",\"B\",\"C\"]")));
    for (String raw : new String[] {"[]", "[\"A\",\"A\"]", "[\"X\"]", "\"A\""})
      assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, json.readTree(raw)));
  }

  @Test
  void trueFalseNeverCoerces() throws Exception {
    var q = ServerFixtures.questions().get(6);
    assertTrue(evaluator.evaluate(q, json.readTree("true")));
    assertFalse(evaluator.evaluate(q, json.readTree("false")));
    assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, json.readTree("\"true\"")));
  }

  @Test
  void shortNormalizesOnlyWhitespaceCaseAndNfc() throws Exception {
    var q = ServerFixtures.questions().get(8);
    assertTrue(evaluator.evaluate(q, json.valueToTree(" HÀ\u00a0  NỘI ")));
    assertTrue(evaluator.evaluate(q, json.valueToTree("Ha\u0300 Nội")));
    assertTrue(evaluator.evaluate(q, json.valueToTree("Thủ đô Hà Nội")));
    assertFalse(evaluator.evaluate(q, json.valueToTree("Ha Noi")));
    assertFalse(evaluator.evaluate(q, json.valueToTree("Hà Nội!")));
    assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, json.valueToTree(" ")));
    assertThrows(
        ProtocolException.class, () -> evaluator.evaluate(q, json.valueToTree("a".repeat(121))));
  }
}
