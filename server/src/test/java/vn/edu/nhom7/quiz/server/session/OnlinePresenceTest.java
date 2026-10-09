package vn.edu.nhom7.quiz.server.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;

class OnlinePresenceTest {
  @Test
  void revisionsChangeOnlyForVisibleChanges() {
    var sessions = new SessionRegistry();
    var codec = new ProtocolCodec();
    var sent = new ArrayList<Envelope>();
    var presence =
        new OnlinePresenceService(
            sessions,
            (id, e) -> {
              codec.encode(e);
              sent.add(e);
              return true;
            });
    var one = UUID.randomUUID();
    var two = UUID.randomUUID();
    sessions.tryAuthenticate(one, 1);
    sessions.tryAuthenticate(two, 2);
    for (long id : new long[] {1, 2})
      presence.cache(
          new Payloads.Profile(id, "user" + id, "User " + id, "avatar-01", 0, 0, 0, 0, 0));
    var first = presence.snapshot();
    assertEquals("FREE", first.users().getFirst().status());
    assertEquals(first.revision(), presence.snapshot().revision());
    var challenge = UUID.randomUUID();
    assertTrue(sessions.reservePair(1, 2, challenge));
    var inviting = presence.snapshot();
    assertTrue(inviting.revision() > first.revision());
    assertTrue(inviting.users().stream().allMatch(u -> u.status().equals("CHALLENGING")));
    var match = UUID.randomUUID();
    sessions.attachReservedPair(challenge, match);
    assertTrue(presence.snapshot().users().stream().allMatch(u -> u.status().equals("BUSY")));
    sessions.detach(1, match);
    assertEquals("FREE", presence.snapshot().users().getFirst().status());
    sessions.removeConnection(one);
    presence.broadcast();
    assertTrue(sent.getLast().payload().path("users").size() == 1);
    long before = presence.snapshot().revision();
    presence.cache(new Payloads.Profile(2, "user2", "User 2", "avatar-01", 3, 1, 1, 0, 0));
    assertTrue(presence.snapshot().revision() > before);
  }
}
