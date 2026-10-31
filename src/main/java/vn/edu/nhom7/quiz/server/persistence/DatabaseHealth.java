package vn.edu.nhom7.quiz.server.persistence;

import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;

public final class DatabaseHealth {
  private final ConnectionFactory connections;
  private final AtomicBoolean available = new AtomicBoolean(true);

  public DatabaseHealth(ConnectionFactory c) {
    connections = c;
  }

  public boolean available() {
    return available.get();
  }

  public void unavailable() {
    available.set(false);
  }

  public boolean probe() {
    try (var c = connections.open();
        var p = c.prepareStatement("SELECT 1");
        var r = p.executeQuery()) {
      boolean ok = r.next() && r.getInt(1) == 1;
      available.set(ok);
      return ok;
    } catch (Exception e) {
      available.set(false);
      return false;
    }
  }
}
