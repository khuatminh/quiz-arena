package vn.edu.nhom7.quiz.server.quiz;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.support.*;

class QuizRepositoryIT {
  @Test
  void seedSamplesTenUniqueQuestionsInFixedRatio() {
    var repo = new JdbcQuizRepository(TestDatabase.factory());
    assertEquals(
        3,
        repo.list(null, 1, 50).items().stream()
            .filter(q -> "SYSTEM".equals(q.quizSource()))
            .count());
    for (long id = 1; id <= 3; id++) {
      var d = repo.detail(id);
      assertEquals(
          Map.of("SINGLE_CHOICE", 8, "MULTIPLE_CHOICE", 4, "TRUE_FALSE", 4, "SHORT_ANSWER", 4),
          d.typeCounts());
      assertEquals("AVAILABLE", d.quiz().availability());
      var questions = repo.loadMatchQuestions(id, new Random(9));
      assertEquals(10, questions.stream().map(q -> q.questionId()).distinct().count());
      assertEquals(
          4,
          questions.stream().filter(q -> q.questionType().name().equals("SINGLE_CHOICE")).count());
      assertEquals(questions, repo.loadMatchQuestions(id, new Random(9)));
    }
  }
}
