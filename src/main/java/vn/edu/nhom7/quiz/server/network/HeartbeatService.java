package vn.edu.nhom7.quiz.server.network;

public final class HeartbeatService implements Runnable {
  private final ConnectionManager manager;

  public HeartbeatService(ConnectionManager manager) {
    this.manager = manager;
  }

  public void run() {
    long now = System.nanoTime();
    for (var c : manager.all())
      if (c.slow.get()
          || now - c.lastValidFrame >= 30_000_000_000L
          || (!c.handshaken && now - c.connectedAt >= 5_000_000_000L)) manager.close(c.id);
  }
}
