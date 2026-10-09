package vn.edu.nhom7.quiz.server.support;

/**
 * Manual scheduler name from the F03 contract; FakeGameScheduler is the same deterministic engine.
 */
public final class ManualGameScheduler extends FakeGameScheduler {
  public ManualGameScheduler(FakeGameClock clock) {
    super(clock);
  }
}
