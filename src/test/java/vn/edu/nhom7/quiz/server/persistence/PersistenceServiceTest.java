package vn.edu.nhom7.quiz.server.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.match.GameScheduler;
import vn.edu.nhom7.quiz.server.support.ServerFixtures;

class PersistenceServiceTest {
  static class Scheduler implements GameScheduler {
    record Scheduled(Duration delay, Runnable action) {}

    final Queue<Scheduled> tasks = new ArrayDeque<>();

    public Cancellable schedule(Duration d, Runnable a) {
      var item = new Scheduled(d, a);
      tasks.add(item);
      return () -> tasks.remove(item);
    }

    void advance(long seconds) {
      var task = tasks.remove();
      assertEquals(Duration.ofSeconds(seconds), task.delay());
      task.action().run();
    }
  }

  @Test
  void failuresRetryWithoutDroppingSummary() {
    var scheduler = new Scheduler();
    var attempts = new AtomicInteger();
    var events = new ArrayList<PersistenceService.SaveNotification>();
    var health =
        new DatabaseHealth(
            () -> {
              throw new SQLException("offline");
            });
    var service =
        new PersistenceService(
            summary -> {
              if (attempts.incrementAndGet() < 5) throw new RuntimeException("offline");
              return SaveResult.ALREADY_SAVED;
            },
            health,
            Runnable::run,
            scheduler,
            events::add);
    var summary = ServerFixtures.completedSummary();
    assertTrue(service.reserveMatch(summary.matchId()));
    service.submit(summary);
    assertEquals(1, service.pendingCount());
    assertFalse(service.canCreateMatch());
    scheduler.advance(1);
    scheduler.advance(2);
    scheduler.advance(4);
    assertEquals("FAILED", events.getLast().status());
    assertEquals(1, service.pendingCount());
    scheduler.advance(30);
    assertEquals("SAVED", events.getLast().status());
    assertEquals(0, service.pendingCount());
    assertEquals(5, attempts.get());
  }

  @Test
  void reservationsAtomicallyBoundActiveAndPending() {
    var scheduler = new Scheduler();
    var health =
        new DatabaseHealth(
            () -> {
              throw new SQLException();
            });
    var service =
        new PersistenceService(s -> SaveResult.SAVED, health, r -> {}, scheduler, n -> {});
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 100; i++) {
      var id = UUID.randomUUID();
      ids.add(id);
      assertTrue(service.reserveMatch(id));
    }
    assertFalse(service.reserveMatch(UUID.randomUUID()));
    assertFalse(service.reserveMatch(ids.getFirst()));
    service.cancelReservation(ids.getFirst());
    assertTrue(service.reserveMatch(UUID.randomUUID()));
  }

  @Test
  void shutdownStopsNewReservationsButSavesExistingMatches() {
    var scheduler = new Scheduler();
    var saved = new AtomicInteger();
    var service =
        new PersistenceService(
            s -> {
              saved.incrementAndGet();
              return SaveResult.SAVED;
            },
            new DatabaseHealth(
                () -> {
                  throw new SQLException();
                }),
            Runnable::run,
            scheduler,
            n -> {});
    var summary = ServerFixtures.completedSummary();
    assertTrue(service.reserveMatch(summary.matchId()));
    service.stopCreatingMatches();
    assertFalse(service.canCreateMatch());
    assertFalse(service.reserveMatch(UUID.randomUUID()));
    service.submit(summary);
    assertEquals(1, saved.get());
    assertEquals(0, service.pendingCount());
    assertEquals(0, service.reservedCount());
  }
}
