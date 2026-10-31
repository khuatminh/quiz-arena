package vn.edu.nhom7.quiz.client.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.MessageType;

class RequestTimeoutMonitorTest {
  @Test
  void reportsUnresolvedRequestsOnceAndIgnoresRepliesAlreadyReceived() {
    var tracker = new RequestTracker();
    var reported = new ArrayList<MessageType>();
    var monitor = new RequestTimeoutMonitor(tracker, () -> 0, reported::add);
    monitor.poll(0);
    tracker.register(UUID.randomUUID(), MessageType.ANSWER, 0);
    UUID replied = UUID.randomUUID();
    tracker.register(replied, MessageType.HISTORY_REQUEST, 0);
    tracker.resolve(replied);
    monitor.poll(2_000_000_000L);
    monitor.poll(6_000_000_000L);
    assertEquals(List.of(MessageType.ANSWER), reported);
    assertEquals(0, tracker.size());
  }

  @Test
  void reconnectDiscardsTimeoutsFromThePreviousConnection() {
    var tracker = new RequestTracker();
    var epoch = new AtomicLong();
    var reported = new ArrayList<MessageType>();
    var monitor = new RequestTimeoutMonitor(tracker, epoch::get, reported::add);
    monitor.poll(0);
    tracker.register(UUID.randomUUID(), MessageType.LOGIN, 0);
    epoch.incrementAndGet();
    monitor.poll(6_000_000_000L);
    assertTrue(reported.isEmpty());
    tracker.register(UUID.randomUUID(), MessageType.RANKING_REQUEST, 6_000_000_000L);
    monitor.poll(11_000_000_000L);
    assertEquals(List.of(MessageType.RANKING_REQUEST), reported);
  }
}
