package vn.edu.nhom7.quiz.server.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {
  @Test
  void concurrentLoginHasOneWinner() throws Exception {
    var registry = new SessionRegistry();
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return registry.tryAuthenticate(UUID.randomUUID(), 1);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return registry.tryAuthenticate(UUID.randomUUID(), 1);
              });
      gate.countDown();
      assertNotEquals(a.get(), b.get());
    }
  }

  @Test
  void reservationsTransferAndIdentityCleanup() {
    var r = new SessionRegistry();
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var c = UUID.randomUUID();
    assertTrue(r.tryAuthenticate(a, 1));
    assertTrue(r.tryAuthenticate(b, 2));
    assertTrue(r.tryAuthenticate(c, 3));
    var challenge = UUID.randomUUID();
    assertTrue(r.reservePair(1, 2, challenge));
    assertFalse(r.reservePair(1, 3, UUID.randomUUID()));
    var first = UUID.randomUUID();
    assertTrue(r.attachReservedPair(challenge, first));
    var assignment = r.assignment(first).orElseThrow();
    var next = UUID.randomUUID();
    assertTrue(r.transferPair(first, assignment.generation(), next));
    r.detach(1, first);
    assertEquals(next, r.matchOf(1).orElseThrow());
    r.removeConnection(a);
    assertTrue(r.authenticated(a).isEmpty());
    assertTrue(r.tryAuthenticate(UUID.randomUUID(), 1));
    assertTrue(r.removeConnection(a).isEmpty());
  }

  @Test
  void competingPairReservationsHaveOneWinner() throws Exception {
    var registry = new SessionRegistry();
    for (long id = 1; id <= 3; id++) registry.tryAuthenticate(UUID.randomUUID(), id);
    var gate = new java.util.concurrent.CountDownLatch(1);
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return registry.reservePair(1, 3, first);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return registry.reservePair(2, 3, second);
              });
      gate.countDown();
      assertNotEquals(a.get(), b.get());
      assertEquals(
          2, registry.visible().stream().filter(v -> v.status().equals("CHALLENGING")).count());
      registry.releaseChallenge(first);
      registry.releaseChallenge(second);
      long revision = registry.revision();
      registry.releaseChallenge(first);
      assertEquals(revision, registry.revision());
      assertTrue(registry.visible().stream().allMatch(v -> v.status().equals("FREE")));
    }
  }
}
