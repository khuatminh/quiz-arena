package vn.edu.nhom7.quiz.server.support;

import java.time.*;
import java.util.*;
import vn.edu.nhom7.quiz.server.match.GameScheduler;

public class FakeGameScheduler implements GameScheduler {
  private final FakeGameClock clock;
  private final PriorityQueue<Task> tasks =
      new PriorityQueue<>(Comparator.comparingLong(t -> t.at));
  private long order;

  private static final class Task {
    long at;
    Runnable action;
    boolean cancelled;

    Task(long at, Runnable action) {
      this.at = at;
      this.action = action;
    }
  }

  public FakeGameScheduler(FakeGameClock clock) {
    this.clock = clock;
  }

  public Cancellable schedule(Duration delay, Runnable action) {
    Task t = new Task(clock.nanoTime() + delay.toNanos(), action);
    synchronized (tasks) {
      tasks.add(t);
    }
    return () -> {
      synchronized (tasks) {
        t.cancelled = true;
      }
    };
  }

  public void advance(Duration duration) {
    long target = clock.nanoTime() + duration.toNanos();
    while (true) {
      Task t;
      synchronized (tasks) {
        if (tasks.isEmpty() || tasks.peek().at > target) break;
        t = tasks.remove();
      }
      clock.advance(Duration.ofNanos(Math.max(0, t.at - clock.nanoTime())));
      if (!t.cancelled) t.action.run();
    }
    clock.advance(Duration.ofNanos(target - clock.nanoTime()));
  }

  public long activeTaskCount() {
    synchronized (tasks) {
      return tasks.stream().filter(t -> !t.cancelled).count();
    }
  }

  public List<Runnable> callbacks() {
    synchronized (tasks) {
      return tasks.stream().map(t -> t.action).toList();
    }
  }
}
