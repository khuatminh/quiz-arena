package vn.edu.nhom7.quiz.server.quiz;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;

class QuestionValidatorTest {
  @Test
  void rejectsUnknownChoiceKey() {
    var q =
        new QuestionSnapshot(
            1,
            1,
            QuestionType.SINGLE_CHOICE,
            "Valid question",
            List.of(new Payloads.Option("A", "One"), new Payloads.Option("B", "Two")),
            "\"C\"",
            null,
            null,
            null);
    assertThrows(IllegalArgumentException.class, () -> QuestionValidator.validate(q));
  }
}
