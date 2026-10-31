package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AnswerIntentFactoryTest {
  @Test
  void typedAnswers() {
    assertTrue(AnswerIntentFactory.trueFalse(true).isBoolean());
    assertTrue(AnswerIntentFactory.single("A").isTextual());
    assertEquals(2, AnswerIntentFactory.multiple(List.of("A", "B", "A")).size());
    assertThrows(IllegalArgumentException.class, () -> AnswerIntentFactory.shortAnswer(" "));
    assertThrows(
        IllegalArgumentException.class, () -> AnswerIntentFactory.shortAnswer("😀".repeat(121)));
    assertEquals("  Raw  ", AnswerIntentFactory.shortAnswer("  Raw  ").textValue());
  }
}
