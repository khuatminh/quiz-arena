package vn.edu.nhom7.quiz.server.match;

public interface GameScheduler {
  Cancellable schedule(java.time.Duration delay, Runnable action);

  interface Cancellable {
    void cancel();
  }
}
