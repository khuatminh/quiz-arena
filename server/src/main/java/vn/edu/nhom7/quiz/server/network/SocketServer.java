package vn.edu.nhom7.quiz.server.network;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;

public final class SocketServer implements AutoCloseable {
  private final ConnectionManager manager;
  private final BiConsumer<Connection, Envelope> router;
  private final BiConsumer<Connection, ProtocolException> errors;
  private final ExecutorService readers = Executors.newFixedThreadPool(32),
      writers = Executors.newFixedThreadPool(32);
  private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor();
  private ServerSocket listener;
  private Thread acceptor;
  private volatile boolean closed;

  public SocketServer(
      ConnectionManager manager,
      BiConsumer<Connection, Envelope> router,
      BiConsumer<Connection, ProtocolException> errors) {
    this.manager = manager;
    this.router = router;
    this.errors = errors;
  }

  public void start(String host, int port) throws IOException {
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress(host, port));
    acceptor = new Thread(this::accept, "quiz-accept");
    acceptor.start();
    maintenance.scheduleAtFixedRate(new HeartbeatService(manager), 250, 250, TimeUnit.MILLISECONDS);
  }

  public int port() {
    return listener.getLocalPort();
  }

  private void accept() {
    while (!closed)
      try {
        Socket socket = listener.accept();
        if (manager.size() >= 32) {
          socket.close();
          continue;
        }
        socket.setSoTimeout(5000);
        socket.setTcpNoDelay(true);
        var c = new Connection(socket, 256);
        manager.add(c);
        c.writer = writers.submit(new ConnectionWriter(c, manager));
        c.reader = readers.submit(new ConnectionReader(c, manager, router, errors));
      } catch (IOException e) {
        if (!closed) System.err.println("Accept failed: " + e.getClass().getSimpleName());
      }
  }

  public void stopAccepting() {
    closed = true;
    try {
      if (listener != null) listener.close();
    } catch (IOException ignored) {
    }
  }

  public void close() {
    stopAccepting();
    manager.close();
    maintenance.shutdownNow();
    readers.shutdownNow();
    writers.shutdownNow();
  }
}
