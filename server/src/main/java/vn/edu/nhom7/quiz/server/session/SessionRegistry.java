package vn.edu.nhom7.quiz.server.session;

import java.util.*;

/** Short synchronized mutations only; returned values are immutable snapshots. */
public final class SessionRegistry {
  private final Map<UUID, Long> connections = new HashMap<>();
  private final Map<Long, UUID> users = new HashMap<>(),
      reservations = new HashMap<>(),
      matches = new HashMap<>();
  private final Map<UUID, Long> generations = new HashMap<>();
  private long revision, generation;

  public synchronized boolean tryAuthenticate(UUID connectionId, long userId) {
    if (users.containsKey(userId) || connections.containsKey(connectionId)) return false;
    connections.put(connectionId, userId);
    users.put(userId, connectionId);
    revision++;
    return true;
  }

  public synchronized Optional<SessionContext> authenticated(UUID connectionId) {
    return Optional.ofNullable(connections.get(connectionId))
        .map(id -> new SessionContext(connectionId, id));
  }

  public synchronized Optional<SessionContext> user(long id) {
    return Optional.ofNullable(users.get(id)).map(c -> new SessionContext(c, id));
  }

  public synchronized List<SessionContext> online() {
    return connections.entrySet().stream()
        .map(e -> new SessionContext(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingLong(SessionContext::userId))
        .toList();
  }

  public record VisibleSession(SessionContext session, String status) {}

  public synchronized List<VisibleSession> visible() {
    return online().stream().map(s -> new VisibleSession(s, status(s.userId()))).toList();
  }

  public synchronized long revision() {
    return revision;
  }

  public synchronized String status(long id) {
    return matches.containsKey(id) ? "BUSY" : reservations.containsKey(id) ? "CHALLENGING" : "FREE";
  }

  public synchronized Optional<UUID> matchOf(long id) {
    return Optional.ofNullable(matches.get(id));
  }

  public synchronized boolean reservePair(long first, long second, UUID challengeId) {
    if (first == second
        || !users.containsKey(first)
        || !users.containsKey(second)
        || !status(first).equals("FREE")
        || !status(second).equals("FREE")) return false;
    reservations.put(first, challengeId);
    reservations.put(second, challengeId);
    revision++;
    return true;
  }

  public synchronized boolean attachReservedPair(UUID challengeId, UUID matchId) {
    var pair =
        reservations.entrySet().stream()
            .filter(e -> e.getValue().equals(challengeId))
            .map(Map.Entry::getKey)
            .toList();
    if (pair.size() != 2 || pair.stream().anyMatch(u -> !users.containsKey(u))) return false;
    pair.forEach(
        u -> {
          reservations.remove(u);
          matches.put(u, matchId);
        });
    generations.put(matchId, ++generation);
    revision++;
    return true;
  }

  public synchronized Optional<AssignmentSnapshot> assignment(UUID matchId) {
    var pair =
        matches.entrySet().stream()
            .filter(e -> e.getValue().equals(matchId))
            .map(Map.Entry::getKey)
            .sorted()
            .map(this::user)
            .flatMap(Optional::stream)
            .toList();
    return pair.size() == 2
        ? Optional.of(new AssignmentSnapshot(matchId, generations.getOrDefault(matchId, 0L), pair))
        : Optional.empty();
  }

  public synchronized boolean transferPair(UUID oldMatchId, long expectedGeneration, UUID next) {
    var snapshot = assignment(oldMatchId);
    if (snapshot.isEmpty() || snapshot.get().generation() != expectedGeneration) return false;
    snapshot.get().players().forEach(p -> matches.put(p.userId(), next));
    generations.remove(oldMatchId);
    generations.put(next, ++generation);
    revision++;
    return true;
  }

  public synchronized void releaseChallenge(UUID challengeId) {
    if (reservations.values().removeIf(challengeId::equals)) revision++;
  }

  public synchronized void detach(long userId, UUID expectedMatchId) {
    if (matches.remove(userId, expectedMatchId)) {
      revision++;
      if (!matches.containsValue(expectedMatchId)) generations.remove(expectedMatchId);
    }
  }

  public synchronized Optional<SessionContext> removeConnection(UUID connectionId) {
    Long id = connections.remove(connectionId);
    if (id == null) return Optional.empty();
    if (Objects.equals(users.get(id), connectionId)) {
      users.remove(id);
      reservations.remove(id);
      UUID oldMatch = matches.remove(id);
      if (oldMatch != null && !matches.containsValue(oldMatch)) generations.remove(oldMatch);
      revision++;
    }
    return Optional.of(new SessionContext(connectionId, id));
  }
}
