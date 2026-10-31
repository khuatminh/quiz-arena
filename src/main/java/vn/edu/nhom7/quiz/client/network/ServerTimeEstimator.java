package vn.edu.nhom7.quiz.client.network;

import java.util.*;

public final class ServerTimeEstimator {
  private record Sample(long rtt, long offset) {}

  private final Deque<Sample> samples = new ArrayDeque<>();

  public synchronized void sample(long sentMs, long receivedMs, long serverMs) {
    if (receivedMs < sentMs) return;
    samples.addLast(new Sample(receivedMs - sentMs, serverMs - (sentMs + receivedMs) / 2));
    while (samples.size() > 5) samples.removeFirst();
  }

  public synchronized long offsetMs() {
    return samples.stream()
        .min(Comparator.comparingLong(Sample::rtt))
        .map(Sample::offset)
        .orElse(0L);
  }

  public synchronized long remainingMs(long deadlineServerMs, long localNowMs, long durationMs) {
    return Math.max(0, Math.min(durationMs, deadlineServerMs - localNowMs - offsetMs()));
  }
}
