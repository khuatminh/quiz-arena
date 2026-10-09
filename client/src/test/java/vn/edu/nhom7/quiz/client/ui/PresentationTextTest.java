package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class PresentationTextTest {
  @Test
  void typedAnswersAreReadable() {
    var f = JsonNodeFactory.instance;
    var q = f.objectNode();
    q.putArray("options").addObject().put("id", "A").put("text", "Hà Nội");
    assertEquals("Hà Nội", PresentationText.answer(q, f.textNode("A")));
    assertEquals("Đúng", PresentationText.answer(q, f.booleanNode(true)));
    assertEquals("Hà Nội, B", PresentationText.answer(q, f.arrayNode().add("A").add("B")));
    assertEquals("  Raw  ", PresentationText.answer(q, f.textNode("  Raw  ")));
    assertEquals("Câu chưa được chấm", PresentationText.outcome("ABANDONED"));
    assertEquals("Người chơi mất kết nối", PresentationText.reason("DISCONNECT"));
  }
}
