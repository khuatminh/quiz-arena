package vn.edu.nhom7.quiz.server.match;

public interface GameClock {
  long nanoTime();

  java.time.Instant instant();
}
