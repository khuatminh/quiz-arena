package vn.edu.nhom7.quiz.server.network;

import java.security.MessageDigest;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class RequestCache {
  private record Entry(byte[] fingerprint, Envelope reply, long at) {
    Entry {
      fingerprint = fingerprint.clone();
    }
  }

  private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();

  public synchronized Optional<Envelope> find(UUID requestId, byte[] fingerprint, long now) {
    entries.values().removeIf(e -> now - e.at() >= 60_000_000_000L);
    var entry = entries.get(requestId);
    if (entry == null) return Optional.empty();
    if (!MessageDigest.isEqual(entry.fingerprint(), fingerprint))
      throw new ProtocolException("REQUEST_ID_REUSED", "Request ID was reused for another command");
    return Optional.ofNullable(entry.reply());
  }

  public synchronized boolean begin(UUID id, byte[] fingerprint, long now) {
    find(id, fingerprint, now);
    if (entries.containsKey(id)) return false;
    remember(id, fingerprint, null, now);
    return true;
  }

  public synchronized void remember(UUID id, byte[] fingerprint, Envelope reply, long now) {
    entries.put(id, new Entry(fingerprint, reply, now));
    while (entries.size() > 100) entries.remove(entries.keySet().iterator().next());
  }

  public synchronized void complete(UUID id, Envelope reply) {
    var e = entries.get(id);
    if (e != null) entries.put(id, new Entry(e.fingerprint(), reply, e.at()));
  }

  public synchronized void clear() {
    entries.clear();
  }
}
