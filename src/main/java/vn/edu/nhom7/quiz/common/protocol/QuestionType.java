package vn.edu.nhom7.quiz.common.protocol;

public enum QuestionType {
  SINGLE_CHOICE,
  MULTIPLE_CHOICE,
  TRUE_FALSE,
  SHORT_ANSWER,
  REORDER,
  RANGE,
  PINPOINT;

  public long timeLimitMs() {
    return switch (this) {
      case REORDER, RANGE, PINPOINT -> 30_000;
      default -> 15_000;
    };
  }
}
