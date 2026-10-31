package vn.edu.nhom7.quiz.client.network;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class RequestTracker {
  public record Pending(MessageType type, long sentNanos) {}

  private final Map<UUID, Pending> pending = new LinkedHashMap<>();

  public synchronized void register(UUID id, MessageType type, long now) {
    if (type != MessageType.QUESTION_READY) pending.put(id, new Pending(type, now));
  }

  public synchronized Optional<Pending> resolve(UUID id) {
    return Optional.ofNullable(pending.remove(id));
  }

  public synchronized List<UUID> expired(long now) {
    return pending.entrySet().stream()
        .filter(
            e ->
                now - e.getValue().sentNanos()
                    >= ((e.getValue().type() == MessageType.ANSWER
                            || e.getValue().type() == MessageType.LOGOUT)
                        ? 2_000_000_000L
                        : 5_000_000_000L))
        .map(Map.Entry::getKey)
        .toList();
  }

  public synchronized int size() {
    return pending.size();
  }

  public synchronized void clear() {
    pending.clear();
  }
}
