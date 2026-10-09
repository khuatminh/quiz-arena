package vn.edu.nhom7.quiz.server.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.net.FrameCodec;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.support.SocketHarness;

class SocketServerTest {
  private final ProtocolCodec codec = new ProtocolCodec();

  @Test
  void fragmentedFramesAndBadJsonRecover() throws Exception {
    var manager = new ConnectionManager();
    var disconnected = new java.util.concurrent.atomic.AtomicInteger();
    var cleanup = new CountDownLatch(1);
    manager.onClosed(
        id -> {
          disconnected.incrementAndGet();
          cleanup.countDown();
        });
    try (var server =
        new SocketServer(
            manager,
            (c, e) ->
                manager.tryEnqueue(
                    c.id,
                    codec.envelope(
                        MessageType.PONG,
                        e.requestId(),
                        null,
                        null,
                        null,
                        new Payloads.Pong(e.payload().path("clientTimeMs").asLong(), 0))),
            (c, e) ->
                manager.tryEnqueue(
                    c.id,
                    codec.envelope(
                        MessageType.ERROR,
                        null,
                        null,
                        null,
                        null,
                        new Payloads.Error(
                            e.code(),
                            e.getMessage(),
                            false,
                            codec.mapper().createObjectNode()))))) {
      server.start("127.0.0.1", 0);
      try (var client = SocketHarness.connect("127.0.0.1", server.port())) {
        var request =
            codec.envelope(
                MessageType.PING, UUID.randomUUID(), null, null, null, new Payloads.Ping(7));
        client.sendFragmented(request, 1);
        assertEquals(
            request.requestId(), client.await(MessageType.PONG, Duration.ofSeconds(2)).requestId());
        client.sendRaw(FrameCodec.encode("{".getBytes()));
        assertEquals(
            MessageType.ERROR, client.await(MessageType.ERROR, Duration.ofSeconds(2)).type());
        client.send(request);
        assertEquals(
            7,
            client
                .await(MessageType.PONG, Duration.ofSeconds(2))
                .payload()
                .path("clientTimeMs")
                .asLong());
      }
    }
    assertTrue(cleanup.await(2, TimeUnit.SECONDS));
    assertEquals(1, disconnected.get());
  }

  @Test
  void concurrentProducersWriteWholeFrames() throws Exception {
    var manager = new ConnectionManager();
    try (var server =
        new SocketServer(
            manager,
            (c, e) -> {
              c.handshaken = true;
              try (var pool = Executors.newFixedThreadPool(4)) {
                for (int i = 0; i < 100; i++) {
                  int n = i;
                  pool.submit(
                      () ->
                          manager.tryEnqueue(
                              c.id,
                              codec.envelope(
                                  MessageType.PONG,
                                  UUID.randomUUID(),
                                  null,
                                  null,
                                  null,
                                  new Payloads.Pong(n, 0))));
                }
              }
            },
            (c, e) -> {})) {
      server.start("127.0.0.1", 0);
      try (var client = SocketHarness.connect("127.0.0.1", server.port())) {
        client.send(
            codec.envelope(
                MessageType.PING, UUID.randomUUID(), null, null, null, new Payloads.Ping(1)));
        var values = new HashSet<Long>();
        for (int i = 0; i < 100; i++)
          values.add(
              client
                  .await(MessageType.PONG, Duration.ofSeconds(3))
                  .payload()
                  .path("clientTimeMs")
                  .asLong());
        assertEquals(100, values.size());
      }
    }
  }

  @Test
  void fullQueueReturnsImmediatelyAndCleanupOnce() throws Exception {
    var m = new ConnectionManager();
    var count = new java.util.concurrent.atomic.AtomicInteger();
    m.onClosed(id -> count.incrementAndGet());
    var c = new Connection(new Socket(), 1);
    m.add(c);
    var e = codec.envelope(MessageType.PONG, null, null, null, null, new Payloads.Pong(0, 0));
    assertTrue(m.tryEnqueue(c.id, e));
    assertTimeoutPreemptively(Duration.ofMillis(200), () -> assertFalse(m.tryEnqueue(c.id, e)));
    new HeartbeatService(m).run();
    m.close(c.id);
    assertEquals(1, count.get());
    assertEquals(0, m.size());
  }

  @Test
  void handshakeValidatesVersionsAndDeadline() {
    var v = new HandshakeValidator();
    var hello =
        codec.envelope(
            MessageType.HELLO, UUID.randomUUID(), null, null, null, new Payloads.Hello("1", "1"));
    v.validateFirst(hello, 0, 1);
    assertEquals(
        "HANDSHAKE_TIMEOUT",
        assertThrows(
                vn.edu.nhom7.quiz.common.protocol.ProtocolException.class,
                () -> v.validateFirst(hello, 0, 5_000_000_000L))
            .code());
    var mismatch =
        codec.envelope(
            MessageType.HELLO, UUID.randomUUID(), null, null, null, new Payloads.Hello("1", "2"));
    assertEquals(
        "ASSET_VERSION_MISMATCH",
        assertThrows(
                vn.edu.nhom7.quiz.common.protocol.ProtocolException.class,
                () -> v.validateFirst(mismatch, 0, 1))
            .code());
  }
}
