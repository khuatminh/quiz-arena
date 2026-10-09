package vn.edu.nhom7.quiz.server.support;

import java.util.*;
import vn.edu.nhom7.quiz.server.domain.MatchSummary;
import vn.edu.nhom7.quiz.server.persistence.ResultSink;

public final class RecordingResultSink implements ResultSink {
  private final Set<UUID> reservations = new HashSet<>();
  private final Map<UUID, MatchSummary> summaries = new LinkedHashMap<>();
  public volatile boolean healthy = true;
  private final int capacity;

  public RecordingResultSink() {
    this(100);
  }

  public RecordingResultSink(int capacity) {
    this.capacity = capacity;
  }

  public synchronized boolean canCreateMatch() {
    return healthy && reservations.size() + summaries.size() < capacity;
  }

  public synchronized boolean reserveMatch(UUID id) {
    return canCreateMatch() && !summaries.containsKey(id) && reservations.add(id);
  }

  public synchronized void cancelReservation(UUID id) {
    reservations.remove(id);
  }

  public synchronized void submit(MatchSummary summary) {
    var old = summaries.get(summary.matchId());
    if (old != null && !old.equals(summary))
      throw new IllegalArgumentException("Conflicting summary");
    reservations.remove(summary.matchId());
    summaries.putIfAbsent(summary.matchId(), summary);
  }

  public synchronized Map<UUID, MatchSummary> summaries() {
    return Map.copyOf(summaries);
  }

  public synchronized Set<UUID> reservations() {
    return Set.copyOf(reservations);
  }
}
