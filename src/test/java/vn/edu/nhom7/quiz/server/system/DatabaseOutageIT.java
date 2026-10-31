package vn.edu.nhom7.quiz.server.system;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.nhom7.quiz.server.support.SystemTestSupport.*;

import java.sql.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.*;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;
import vn.edu.nhom7.quiz.server.support.*;

class DatabaseOutageIT {
  @Test
  void failedSaveRetainsResultAndRetriesExactlyOnceAfterRecovery() throws Exception {
    var clock = new FakeGameClock();
    var timer = new FakeGameScheduler(clock);
    var outage = new AtomicBoolean();
    var real = TestDatabase.factory();
    ConnectionFactory controlled =
        () -> {
          if (outage.get()) throw new SQLException("Injected outage");
          return real.open();
        };
    try (var runtime = new ServerRuntime(controlled, clock, timer)) {
      runtime.start("127.0.0.1", 0);
      try (var one = login(runtime);
          var two = login(runtime)) {
        var match = challenge(one, two);
        ready(one, match);
        ready(two, match);
        one.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        two.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        outage.set(true);
        runtime.matches.shutdown();
        var result = one.socket().await(MessageType.MATCH_RESULT, WAIT);
        two.socket().await(MessageType.MATCH_RESULT, WAIT);
        assertEquals("PENDING", result.payload().path("persistenceStatus").asText());
        one.socket().await(MessageType.MATCH_SAVE_STATUS, WAIT);
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (timer.activeTaskCount() < 2 && System.nanoTime() < end) Thread.onSpinWait();
        assertEquals(1, runtime.persistence.pendingCount());
        assertFalse(runtime.persistence.canCreateMatch());
        one.socket()
            .send(command(MessageType.REMATCH_REQUEST, match, null, new Payloads.RematchRequest()));
        assertEquals(
            "SERVER_BUSY",
            one.socket().await(MessageType.ERROR, WAIT).payload().path("code").asText());
        outage.set(false);
        timer.advance(Duration.ofSeconds(1));
        var saved = one.socket().await(MessageType.MATCH_SAVE_STATUS, WAIT);
        assertEquals("SAVED", saved.payload().path("status").asText());
        assertEquals(0, runtime.persistence.awaitPending(Duration.ofSeconds(5)));
        try (var c = real.open();
            var p = c.prepareStatement("SELECT COUNT(*) FROM MATCHES WHERE id=?")) {
          p.setString(1, match.toString());
          try (var rs = p.executeQuery()) {
            rs.next();
            assertEquals(1, rs.getInt(1));
          }
        }
      } finally {
        outage.set(false);
      }
    }
  }
}
