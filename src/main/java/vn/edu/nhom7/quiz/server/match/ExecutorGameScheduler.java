package vn.edu.nhom7.quiz.server.match;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Two scheduler workers; bounded dispatch samples contain no payload or player data. */
public final class ExecutorGameScheduler implements GameScheduler, AutoCloseable {
  public record DispatchSample(
      Instant dispatchedAt,
      long expectedNano,
      long actualNano,
      long delayNanos,
      int connections,
      int matches) {}

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private final ArrayDeque<DispatchSample> samples = new ArrayDeque<>();
  private volatile java.util.function.IntSupplier connections = () -> 0, matches = () -> 0;

  public void setLoadSuppliers(
      java.util.function.IntSupplier connections, java.util.function.IntSupplier matches) {
    this.connections = Objects.requireNonNull(connections);
    this.matches = Objects.requireNonNull(matches);
  }

  public Cancellable schedule(Duration delay, Runnable action) {
    long nanos = Math.max(0, delay.toNanos());
    long expected = System.nanoTime() + nanos;
    var future =
        executor.schedule(
            () -> {
              long actual = System.nanoTime();
              synchronized (samples) {
                if (samples.size() == 4096) samples.removeFirst();
                samples.addLast(
                    new DispatchSample(
                        Instant.now(),
                        expected,
                        actual,
                        Math.max(0, actual - expected),
                        connections.getAsInt(),
                        matches.getAsInt()));
              }
              action.run();
            },
            nanos,
            TimeUnit.NANOSECONDS);
    return () -> future.cancel(false);
  }

  public List<DispatchSample> samples() {
    synchronized (samples) {
      return List.copyOf(samples);
    }
  }

  public void writeSamples(java.nio.file.Path csv) throws java.io.IOException {
    var parent = csv.toAbsolutePath().getParent();
    if (parent != null) java.nio.file.Files.createDirectories(parent);
    try (var writer = java.nio.file.Files.newBufferedWriter(csv)) {
      writer.write("event,type,latencyMs,connections,matches,rttMs\n");
      for (var sample : samples())
        writer.write(
            String.format(
                Locale.ROOT,
                "%d,TIMER_DISPATCH,%.6f,%d,%d,%n",
                sample.actualNano(),
                sample.delayNanos() / 1_000_000.0,
                sample.connections(),
                sample.matches()));
    }
  }

  public void close() {
    executor.shutdownNow();
  }
}
