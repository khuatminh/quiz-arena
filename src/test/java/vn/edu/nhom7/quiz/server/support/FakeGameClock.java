package vn.edu.nhom7.quiz.server.support;

import java.time.*;
import vn.edu.nhom7.quiz.server.match.GameClock;

public final class FakeGameClock implements GameClock {
  private long nanos;

  public synchronized long nanoTime() {
    return nanos;
  }

  public synchronized Instant instant() {
    return Instant.parse("2026-10-05T00:00:00Z").plusNanos(nanos);
  }

  public synchronized void advance(Duration duration) {
    nanos += duration.toNanos();
  }
}
