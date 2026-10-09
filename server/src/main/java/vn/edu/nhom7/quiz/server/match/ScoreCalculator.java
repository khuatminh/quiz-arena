package vn.edu.nhom7.quiz.server.match;

public final class ScoreCalculator {
  private ScoreCalculator() {}

  public static int calculate(boolean correct, long elapsedNanos) {
    if (elapsedNanos < 0 || elapsedNanos > 15_000_000_000L)
      throw new IllegalArgumentException("Elapsed outside answer window");
    return !correct
        ? 0
        : elapsedNanos <= 5_000_000_000L ? 3 : elapsedNanos <= 10_000_000_000L ? 2 : 1;
  }
}
