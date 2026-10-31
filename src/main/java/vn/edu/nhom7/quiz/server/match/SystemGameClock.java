package vn.edu.nhom7.quiz.server.match;

public final class SystemGameClock implements GameClock {
  public long nanoTime() {
    return System.nanoTime();
  }

  public java.time.Instant instant() {
    return java.time.Instant.now();
  }
}
