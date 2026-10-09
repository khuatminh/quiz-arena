package vn.edu.nhom7.quiz.server.session;

import java.util.*;
import java.util.concurrent.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.network.OutboundTransport;

public final class OnlinePresenceService {
  private final SessionRegistry sessions;
  private final OutboundTransport transport;
  private final ConcurrentHashMap<Long, Payloads.Profile> profiles = new ConcurrentHashMap<>();
  private final ProtocolCodec codec = new ProtocolCodec();
  private long revision;
  private java.util.List<Payloads.OnlineUser> last = java.util.List.of();

  public OnlinePresenceService(SessionRegistry sessions, OutboundTransport transport) {
    this.sessions = sessions;
    this.transport = transport;
  }

  public void cache(Payloads.Profile p) {
    profiles.put(p.userId(), p);
  }

  public Payloads.Profile profile(long id) {
    return Objects.requireNonNull(profiles.get(id), "Profile not cached");
  }

  public synchronized Payloads.OnlineList snapshot() {
    var users =
        sessions.visible().stream()
            .filter(v -> profiles.containsKey(v.session().userId()))
            .map(
                v -> {
                  var p = profile(v.session().userId());
                  return new Payloads.OnlineUser(
                      p.userId(), p.displayName(), p.avatarId(), p.totalScore(), v.status());
                })
            .toList();
    if (!users.equals(last)) {
      revision++;
      last = users;
    }
    return new Payloads.OnlineList(revision, users);
  }

  public void broadcast() {
    var payload = snapshot();
    var e = codec.envelope(MessageType.ONLINE_LIST, null, null, null, null, payload);
    for (var s : sessions.online()) transport.tryEnqueue(s.connectionId(), e);
  }
}
