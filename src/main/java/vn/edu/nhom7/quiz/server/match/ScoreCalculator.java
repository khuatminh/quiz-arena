package vn.edu.nhom7.quiz.server.match;

public final class ScoreCalculator {
  private ScoreCalculator() {}

  public static int calculate(boolean correct, long elapsedNanos) {
    return calculate(correct, elapsedNanos, 15_000_000_000L);
  }

  public static int calculate(boolean correct, long elapsedNanos, long windowNanos) {
    if (windowNanos <= 0 || elapsedNanos < 0 || elapsedNanos > windowNanos)
      throw new IllegalArgumentException("Elapsed outside answer window");
    return !correct
        ? 0
        : elapsedNanos <= windowNanos / 3 ? 3 : elapsedNanos <= windowNanos / 3 * 2 ? 2 : 1;
  }
}
