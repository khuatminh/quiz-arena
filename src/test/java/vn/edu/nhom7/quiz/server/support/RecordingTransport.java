package vn.edu.nhom7.quiz.server.support;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.network.OutboundTransport;

public final class RecordingTransport implements OutboundTransport {
  public record Delivery(UUID connectionId, Envelope event) {}

  private final List<Delivery> deliveries = Collections.synchronizedList(new ArrayList<>());
  private final ProtocolCodec codec = new ProtocolCodec();
  public volatile boolean queueFull;

  public boolean tryEnqueue(UUID id, Envelope event) {
    codec.encode(event);
    if (queueFull) return false;
    deliveries.add(new Delivery(id, event));
    return true;
  }

  public List<Delivery> deliveries() {
    synchronized (deliveries) {
      return List.copyOf(deliveries);
    }
  }

  public List<Envelope> events(UUID id) {
    return deliveries().stream()
        .filter(d -> d.connectionId().equals(id))
        .map(Delivery::event)
        .toList();
  }
}
