package vn.edu.nhom7.quiz.server.quiz;

import java.util.*;
import java.util.random.RandomGenerator;
import vn.edu.nhom7.quiz.server.domain.QuestionSnapshot;

public interface QuizRepository {
  List<QuestionSnapshot> loadMatchQuestions(long quizId, RandomGenerator random);
}
