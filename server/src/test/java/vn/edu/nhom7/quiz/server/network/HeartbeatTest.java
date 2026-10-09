package vn.edu.nhom7.quiz.server.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HeartbeatTest {
  @Test
  void thirtySecondIdleClosesOnlyThatPeerOnce() {
    var manager = new ConnectionManager();
    var count = new AtomicInteger();
    manager.onClosed(id -> count.incrementAndGet());
    var idle = new Connection(new Socket(), 256);
    idle.handshaken = true;
    idle.lastValidFrame = System.nanoTime() - 30_000_000_000L;
    var live = new Connection(new Socket(), 256);
    live.handshaken = true;
    manager.add(idle);
    manager.add(live);
    new HeartbeatService(manager).run();
    assertFalse(manager.isAlive(idle.id));
    assertTrue(manager.isAlive(live.id));
    new HeartbeatService(manager).run();
    manager.close(idle.id);
    assertEquals(1, count.get());
    manager.close();
    assertEquals(2, count.get());
  }
}
