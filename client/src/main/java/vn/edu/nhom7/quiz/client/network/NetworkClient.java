package vn.edu.nhom7.quiz.client.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import vn.edu.nhom7.quiz.common.net.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/** A single reader and writer per persistent connection, never running on JavaFX. */
public final class NetworkClient implements AutoCloseable {
  private final ProtocolCodec codec = new ProtocolCodec();
  private final ExecutorService io =
      Executors.newCachedThreadPool(
          r -> {
            var t = new Thread(r, "client-network");
            t.setDaemon(true);
            return t;
          });
  private final ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var t = new Thread(r, "client-heartbeat");
            t.setDaemon(true);
            return t;
          });
  private final BlockingQueue<Envelope> outbound = new ArrayBlockingQueue<>(256);
  private final Consumer<Envelope> events;
  private final Consumer<String> disconnected;
  private final AtomicBoolean open = new AtomicBoolean();
  private volatile Socket socket;
  private volatile boolean handshake;
  private volatile long validAt;
  private volatile ScheduledFuture<?> heartbeat;
  private volatile Future<?> writer, reader;
  public final ServerTimeEstimator time = new ServerTimeEstimator();

  public NetworkClient(Consumer<Envelope> events, Consumer<String> disconnected) {
    this.events = events;
    this.disconnected = disconnected;
  }

  public CompletableFuture<Void> connect(String host, int port) {
    return CompletableFuture.runAsync(
        () -> {
          if (open.get()) throw new IllegalStateException("Already connected");
          try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(1000);
            s.setTcpNoDelay(true);
            socket = s;
            handshake = false;
            validAt = System.nanoTime();
            open.set(true);
            outbound.clear();
            outbound.add(
                codec.envelope(
                    MessageType.HELLO,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    new Payloads.Hello("1.0", "1")));
            writer = io.submit(() -> write(s));
            reader = io.submit(() -> read(s));
            heartbeat =
                timer.scheduleAtFixedRate(
                    () -> {
                      if (!open.get()) return;
                      long elapsed = System.nanoTime() - validAt;
                      if ((!handshake && elapsed > 5_000_000_000L) || elapsed > 30_000_000_000L) {
                        disconnect("Server response timed out");
                        return;
                      }
                      if (handshake)
                        send(
                            codec.envelope(
                                MessageType.PING,
                                UUID.randomUUID(),
                                null,
                                null,
                                null,
                                new Payloads.Ping(System.currentTimeMillis())));
                    },
                    10,
                    10,
                    TimeUnit.SECONDS);
          } catch (IOException e) {
            throw new CompletionException(new IOException("Cannot connect to server", e));
          }
        },
        io);
  }

  public boolean send(Envelope e) {
    return open.get() && (handshake || e.type() == MessageType.HELLO) && outbound.offer(e);
  }

  public boolean isConnected() {
    return open.get() && handshake;
  }

  private void write(Socket s) {
    try {
      OutputStream out = s.getOutputStream();
      while (open.get() && socket == s) {
        Envelope e = outbound.take();
        byte[] b = FrameCodec.encode(codec.encode(e));
        out.write(b);
        out.flush();
      }
    } catch (Exception e) {
      if (open.get() && socket == s) disconnect("Connection interrupted");
    }
  }

  private void read(Socket s) {
    var decoder = new FrameDecoder();
    byte[] bytes = new byte[4096];
    try {
      InputStream in = s.getInputStream();
      while (open.get() && socket == s) {
        int n;
        try {
          n = in.read(bytes);
        } catch (SocketTimeoutException ex) {
          decoder.checkTimeout(System.nanoTime());
          if (!handshake && System.nanoTime() - validAt >= 5_000_000_000L)
            throw new IOException("Handshake timed out");
          continue;
        }
        if (n < 0) {
          decoder.endOfInput();
          break;
        }
        for (byte[] body : decoder.accept(bytes, 0, n, System.nanoTime())) {
          if (socket != s || !open.get()) return;
          Envelope e = codec.decode(body);
          validAt = System.nanoTime();
          if (!handshake) {
            if (e.type() != MessageType.HELLO_ACK
                || !e.payload().path("assetPackVersion").asText().equals("1"))
              throw new IOException("Incompatible server asset pack");
            handshake = true;
          }
          if (e.type() == MessageType.PONG)
            time.sample(
                e.payload().path("clientTimeMs").asLong(),
                System.currentTimeMillis(),
                e.payload().path("serverTimeMs").asLong());
          events.accept(e);
        }
      }
      if (socket == s) disconnect("Server disconnected");
    } catch (Exception e) {
      if (open.get() && socket == s) disconnect("Connection or protocol error");
    }
  }

  private void disconnect(String reason) {
    if (open.compareAndSet(true, false)) {
      if (heartbeat != null) heartbeat.cancel(false);
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      if (writer != null) writer.cancel(true);
      outbound.clear();
      disconnected.accept(reason);
    }
  }

  public void disconnect() {
    disconnect("Disconnected");
  }

  public void close() {
    disconnect("Disconnected");
    io.shutdownNow();
    timer.shutdownNow();
  }
}
