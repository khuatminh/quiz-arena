package vn.edu.nhom7.quiz.server.persistence;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.edu.nhom7.quiz.server.domain.MatchSummary;
import vn.edu.nhom7.quiz.server.match.GameScheduler;

public final class PersistenceService implements ResultSink, AutoCloseable {
  @FunctionalInterface
  public interface Saver {
    SaveResult save(MatchSummary summary);
  }

  public record SaveNotification(
      MatchSummary summary, String status, boolean retryable, Long savedAtMs) {}

  private static final int CAPACITY = 100;
  private final Saver saver;
  private final DatabaseHealth health;
  private final Executor workers;
  private final GameScheduler scheduler;
  private final Consumer<SaveNotification> listener;
  private final Set<UUID> reservations = new HashSet<>();
  private final Map<UUID, Pending> pending = new HashMap<>();
  private boolean closed;
  private boolean acceptingMatches = true;

  private static final class Pending {
    final MatchSummary summary;
    int failures;
    boolean inFlight;
    GameScheduler.Cancellable timer;

    Pending(MatchSummary s) {
      summary = s;
    }
  }

  public PersistenceService(
      JdbcMatchRepository repository,
      DatabaseHealth health,
      Executor workers,
      GameScheduler scheduler,
      Consumer<SaveNotification> listener) {
    this(repository::save, health, workers, scheduler, listener);
  }

  public PersistenceService(
      Saver saver,
      DatabaseHealth health,
      Executor workers,
      GameScheduler scheduler,
      Consumer<SaveNotification> listener) {
    this.saver = saver;
    this.health = health;
    this.workers = workers;
    this.scheduler = scheduler;
    this.listener = listener;
  }

  public synchronized boolean canCreateMatch() {
    return !closed
        && acceptingMatches
        && health.available()
        && reservations.size() + pending.size() < CAPACITY;
  }

  public synchronized boolean reserveMatch(UUID id) {
    if (reservations.contains(id) || pending.containsKey(id)) return false;
    if (!canCreateMatch()) return false;
    reservations.add(id);
    return true;
  }

  public synchronized void stopCreatingMatches() {
    acceptingMatches = false;
  }

  public synchronized void cancelReservation(UUID id) {
    reservations.remove(id);
  }

  public void submit(MatchSummary summary) {
    Pending item;
    synchronized (this) {
      if (closed) throw new IllegalStateException("Persistence service closed");
      var existing = pending.get(summary.matchId());
      if (existing != null) {
        if (!existing.summary.equals(summary))
          throw new IllegalArgumentException("Conflicting summary");
        return;
      }
      boolean reserved = reservations.remove(summary.matchId());
      if (!reserved && reservations.size() + pending.size() >= CAPACITY)
        throw new IllegalStateException(
            "Persistence capacity exceeded; reserve before match activation");
      item = new Pending(summary);
      pending.put(summary.matchId(), item);
    }
    notify(item, "PENDING", true, null);
    dispatch(item);
  }

  private void dispatch(Pending item) {
    synchronized (this) {
      if (closed || pending.get(item.summary.matchId()) != item || item.inFlight) return;
      item.inFlight = true;
    }
    try {
      workers.execute(() -> save(item));
    } catch (RejectedExecutionException e) {
      failed(item);
    }
  }

  private void save(Pending item) {
    try {
      saver.save(item.summary);
      synchronized (this) {
        if (pending.remove(item.summary.matchId(), item)) {
          item.inFlight = false;
          notifyAll();
        }
      }
      health.probe();
      notify(item, "SAVED", false, System.currentTimeMillis());
    } catch (RuntimeException e) {
      failed(item);
    }
  }

  private void failed(Pending item) {
    health.unavailable();
    int failures;
    synchronized (this) {
      item.inFlight = false;
      failures = ++item.failures;
      if (closed || pending.get(item.summary.matchId()) != item) return;
    }
    if (failures >= 4) notify(item, "FAILED", true, null);
    long seconds = failures <= 3 ? 1L << (failures - 1) : 30;
    synchronized (this) {
      if (!closed)
        item.timer = scheduler.schedule(Duration.ofSeconds(seconds), () -> dispatch(item));
    }
  }

  private void notify(Pending item, String status, boolean retryable, Long savedAt) {
    try {
      listener.accept(new SaveNotification(item.summary, status, retryable, savedAt));
    } catch (RuntimeException ignored) {
      /* A transport failure never changes a committed save. */
    }
  }

  public synchronized int pendingCount() {
    return pending.size();
  }

  public synchronized int reservedCount() {
    return reservations.size();
  }

  public synchronized int awaitPending(Duration timeout) throws InterruptedException {
    long end = System.nanoTime() + timeout.toNanos();
    while (!pending.isEmpty()) {
      long remaining = end - System.nanoTime();
      if (remaining <= 0) break;
      TimeUnit.NANOSECONDS.timedWait(this, remaining);
    }
    return pending.size();
  }

  public synchronized void close() {
    closed = true;
    for (var p : pending.values()) if (p.timer != null) p.timer.cancel();
  }
}
