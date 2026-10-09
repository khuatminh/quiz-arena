package vn.edu.nhom7.quiz.server.domain;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public record RoundSummary(
    UUID roundId,
    int roundIndex,
    QuestionSnapshot question,
    boolean revealed,
    List<StoredAnswerOutcome> outcomes) {
  public RoundSummary {
    outcomes = List.copyOf(outcomes);
  }
}
