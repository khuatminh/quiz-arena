package vn.edu.nhom7.quiz.client.network;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import vn.edu.nhom7.quiz.common.protocol.MessageType;

/** Drops pending requests from an old connection and reports each unresolved timeout once. */
public final class RequestTimeoutMonitor {
  private final RequestTracker tracker;
  private final LongSupplier connectionEpoch;
  private final Consumer<MessageType> timeout;
  private long epoch = -1;

  public RequestTimeoutMonitor(
      RequestTracker tracker, LongSupplier connectionEpoch, Consumer<MessageType> timeout) {
    this.tracker = tracker;
    this.connectionEpoch = connectionEpoch;
    this.timeout = timeout;
  }

  public void poll(long nowNanos) {
    long currentEpoch = connectionEpoch.getAsLong();
    if (epoch != currentEpoch) {
      tracker.clear();
      epoch = currentEpoch;
    }
    for (var requestId : tracker.expired(nowNanos))
      tracker.resolve(requestId).ifPresent(request -> timeout.accept(request.type()));
  }
}
