package vn.edu.nhom7.quiz.server.network;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.edu.nhom7.quiz.common.net.FrameCodec;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ConnectionManager implements OutboundTransport, AutoCloseable {
  private final ConcurrentHashMap<UUID, Connection> connections = new ConcurrentHashMap<>();
  private final ProtocolCodec codec = new ProtocolCodec();
  private volatile Consumer<UUID> cleanup = id -> {};

  public void onClosed(Consumer<UUID> callback) {
    cleanup = callback;
  }

  public void add(Connection c) {
    connections.put(c.id, c);
  }

  public Collection<Connection> all() {
    return List.copyOf(connections.values());
  }

  public Optional<Connection> get(UUID id) {
    return Optional.ofNullable(connections.get(id));
  }

  public boolean isAlive(UUID id) {
    var c = connections.get(id);
    return c != null && !c.closed.get();
  }

  public int size() {
    return connections.size();
  }

  public boolean tryEnqueue(UUID id, Envelope event) {
    var c = connections.get(id);
    if (c == null || c.closed.get()) return false;
    byte[] frame;
    try {
      frame = FrameCodec.encode(codec.encode(event));
    } catch (ProtocolException failure) {
      c.slow.set(true);
      System.err.println(
          java.time.Instant.now()
              + " Outbound rejected connectionId="
              + id
              + " type="
              + event.type()
              + " code="
              + failure.code());
      return false;
    }
    boolean accepted = c.outbound.offer(frame);
    if (accepted && event.requestId() != null) c.requests.complete(event.requestId(), event);
    if (!accepted) c.slow.set(true);
    return accepted;
  }

  public void close(UUID id) {
    var c = connections.get(id);
    if (c == null || !c.closed.compareAndSet(false, true)) return;
    connections.remove(id, c);
    try {
      c.socket.close();
    } catch (Exception ignored) {
    }
    if (c.reader != null) c.reader.cancel(true);
    if (c.writer != null) c.writer.cancel(true);
    c.requests.clear();
    Arrays.fill(c.fingerprintKey, (byte) 0);
    cleanup.accept(id);
  }

  public void close() {
    for (var c : all()) close(c.id);
  }
}
