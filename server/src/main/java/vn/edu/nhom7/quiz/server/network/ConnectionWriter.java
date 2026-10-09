package vn.edu.nhom7.quiz.server.network;

public final class ConnectionWriter implements Runnable {
  private final Connection c;
  private final ConnectionManager manager;

  public ConnectionWriter(Connection c, ConnectionManager manager) {
    this.c = c;
    this.manager = manager;
  }

  public void run() {
    try {
      var out = c.socket.getOutputStream();
      while (!c.closed.get()) {
        byte[] frame = c.outbound.take();
        out.write(frame);
        out.flush();
      }
    } catch (Exception ignored) {
    } finally {
      manager.close(c.id);
    }
  }
}
