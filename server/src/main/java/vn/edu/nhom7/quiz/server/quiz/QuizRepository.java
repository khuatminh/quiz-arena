package vn.edu.nhom7.quiz.server.quiz;

import java.util.*;
import java.util.random.RandomGenerator;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.server.domain.QuestionSnapshot;

public interface QuizRepository {
  List<QuestionSnapshot> loadMatchQuestions(long quizId, RandomGenerator random);

  default List<QuestionSnapshot> loadMatchQuestions(
      Payloads.QuizSummary quiz, RandomGenerator random) {
    return loadMatchQuestions(quiz.quizId(), random);
  }

  default <T> T withMatchQuestions(
      Payloads.QuizSummary quiz,
      RandomGenerator random,
      java.util.function.BiFunction<Payloads.QuizSummary, List<QuestionSnapshot>, T> action) {
    return action.apply(quiz, loadMatchQuestions(quiz, random));
  }

  default Payloads.QuizSummary currentMatchQuiz(Payloads.QuizSummary quiz) {
    return quiz;
  }
}
