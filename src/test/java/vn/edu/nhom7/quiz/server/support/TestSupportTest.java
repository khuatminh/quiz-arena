package vn.edu.nhom7.quiz.server.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TestSupportTest {
  @Test
  void manualSchedulerCancelsAndRunsOnceWithoutWallClock() {
    var clock = new FakeGameClock();
    var scheduler = new ManualGameScheduler(clock);
    var count = new AtomicInteger();
    scheduler.schedule(Duration.ofSeconds(2), count::incrementAndGet);
    scheduler.schedule(Duration.ofSeconds(2), count::incrementAndGet).cancel();
    scheduler.advance(Duration.ofSeconds(1));
    assertEquals(0, count.get());
    scheduler.advance(Duration.ofSeconds(1));
    assertEquals(1, count.get());
    scheduler.advance(Duration.ofSeconds(1));
    assertEquals(1, count.get());
  }

  @Test
  void resultCapacityReservationAndSummaryAreIdempotent() {
    var sink = new RecordingResultSink(1);
    var summary = ServerFixtures.completedSummary();
    assertTrue(sink.reserveMatch(summary.matchId()));
    assertFalse(sink.reserveMatch(UUID.randomUUID()));
    sink.submit(summary);
    sink.submit(summary);
    assertEquals(1, sink.summaries().size());
    assertTrue(sink.reservations().isEmpty());
    assertEquals(10, summary.rounds().size());
    assertEquals(20, summary.rounds().stream().mapToInt(r -> r.outcomes().size()).sum());
  }
}
