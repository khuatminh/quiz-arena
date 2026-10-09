package vn.edu.nhom7.quiz.server.network;

import java.net.Socket;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Connection {
  public final UUID id = UUID.randomUUID();
  public final Socket socket;
  public final BlockingQueue<byte[]> outbound;
  public final AtomicBoolean closed = new AtomicBoolean(), slow = new AtomicBoolean();
  public final long connectedAt = System.nanoTime();
  public volatile long lastValidFrame = connectedAt;
  public volatile boolean handshaken;
  public final RequestCache requests = new RequestCache();
  public final byte[] fingerprintKey = new byte[32];
  public final RateLimiter invalid = new RateLimiter(2, 30_000_000_000L),
      queries = new RateLimiter(10, 1_000_000_000L),
      authFailures = new RateLimiter(5, 60_000_000_000L);
  public volatile Future<?> reader, writer;

  public Connection(Socket socket, int capacity) {
    this.socket = socket;
    outbound = new ArrayBlockingQueue<>(capacity);
    new SecureRandom().nextBytes(fingerprintKey);
  }
}
