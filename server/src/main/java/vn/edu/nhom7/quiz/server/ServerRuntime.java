package vn.edu.nhom7.quiz.server;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.edu.nhom7.quiz.common.assets.AssetRegistry;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.auth.*;
import vn.edu.nhom7.quiz.server.challenge.*;
import vn.edu.nhom7.quiz.server.db.*;
import vn.edu.nhom7.quiz.server.domain.ParticipantSummary;
import vn.edu.nhom7.quiz.server.match.*;
import vn.edu.nhom7.quiz.server.network.*;
import vn.edu.nhom7.quiz.server.persistence.*;
import vn.edu.nhom7.quiz.server.query.*;
import vn.edu.nhom7.quiz.server.quiz.*;
import vn.edu.nhom7.quiz.server.session.*;

/** Production wiring; injectable connection factory and clock allow real TCP system tests. */
public final class ServerRuntime implements AutoCloseable {
  public final ConnectionManager connections = new ConnectionManager();
  public final SessionRegistry sessions = new SessionRegistry();
  public final OnlinePresenceService presence = new OnlinePresenceService(sessions, connections);
  private final ThreadPoolExecutor services =
      new ThreadPoolExecutor(
          4,
          4,
          0,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(128),
          new ThreadPoolExecutor.AbortPolicy());
  private final ScheduledExecutorService maintenance = Executors.newScheduledThreadPool(2);
  private final java.util.Set<Long> pendingProfiles = ConcurrentHashMap.newKeySet();
  private final AuthService authentication;
  private final ExecutorGameScheduler ownedScheduler;
  public final PersistenceService persistence;
  public final MatchManager matches;
  public final ChallengeManager challenges;
  public final SocketServer sockets;
  private final ProtocolCodec codec = new ProtocolCodec();
  private final AtomicLong rankingRevision = new AtomicLong();
  private final AtomicBoolean closed = new AtomicBoolean();

  public ServerRuntime(ConnectionFactory factory) {
    this(factory, new SystemGameClock(), null);
  }

  public ServerRuntime(
      ConnectionFactory factory, GameClock clock, GameScheduler suppliedScheduler) {
    new StartupValidator(factory).verify();
    var assets = AssetRegistry.loadDefault();
    ownedScheduler = suppliedScheduler == null ? new ExecutorGameScheduler() : null;
    GameScheduler scheduler = suppliedScheduler == null ? ownedScheduler : suppliedScheduler;
    var users = new JdbcUserRepository(factory);
    var auth =
        new AuthService(
            users,
            new PasswordHasher(),
            java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(i -> String.format("avatar-%02d", i))
                .collect(java.util.stream.Collectors.toSet()));
    authentication = auth;
    var quizzes = new JdbcQuizRepository(factory);
    var ranking = new RankingService(factory);
    var history = new HistoryService(factory);
    var health = new DatabaseHealth(factory);
    if (!health.probe()) throw new IllegalStateException("Database unavailable");
    persistence =
        new PersistenceService(
            new JdbcMatchRepository(factory),
            health,
            services,
            scheduler,
            this::onSaveNotification);
    matches =
        new MatchManager(
            clock,
            scheduler,
            connections,
            persistence,
            id -> {
              var p = presence.profile(id);
              return new ParticipantSummary(p.userId(), p.displayName(), p.avatarId(), 0, 0);
            },
            connections::isAlive,
            (id, match) -> {
              sessions.detach(id, match);
              presence.broadcast();
            });
    matches.configureRematch(sessions, quizzes, services);
    if (ownedScheduler != null)
      ownedScheduler.setLoadSuppliers(connections::size, matches::activeCount);
    challenges =
        new ChallengeManager(
            sessions,
            presence,
            matches,
            quizzes,
            persistence,
            connections,
            clock,
            scheduler,
            services,
            quizzes::summary);
    var router =
        new MessageRouter(
            connections,
            sessions,
            presence,
            auth,
            matches,
            challenges::handle,
            s -> {
              challenges.onDisconnected(s);
              matches.onLogout(s);
            },
            (caller, e) -> {
              var p = e.payload();
              int page = p.path("page").asInt(1), size = p.path("pageSize").asInt(20);
              return switch (e.type()) {
                case QUIZ_LIST_REQUEST ->
                    quizzes.list(
                        p.hasNonNull("categoryId") ? p.get("categoryId").asLong() : null,
                        page,
                        size);
                case QUIZ_DETAIL_REQUEST -> quizzes.detail(p.path("quizId").asLong());
                case PROFILE_REQUEST -> auth.profile(caller.userId());
                case RANKING_REQUEST -> ranking.ranking(caller.userId(), page, size);
                case HISTORY_REQUEST -> history.history(caller.userId(), page, size);
                case MATCH_DETAIL_REQUEST ->
                    history.detail(
                        caller.userId(), UUID.fromString(p.path("historyMatchId").asText()));
                default ->
                    throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
                        "INVALID_MESSAGE", "Unsupported query");
              };
            },
            services,
            maintenance);
    connections.onClosed(
        id ->
            sessions
                .removeConnection(id)
                .ifPresent(
                    s -> {
                      challenges.onDisconnected(s);
                      matches.onDisconnected(s);
                      presence.broadcast();
                    }));
    sockets = new SocketServer(connections, router::route, router::malformed);
    maintenance.scheduleAtFixedRate(
        () -> {
          try {
            services.execute(
                () -> {
                  if (health.probe()) refreshProfiles();
                });
          } catch (RejectedExecutionException ignored) {
          }
        },
        5,
        5,
        TimeUnit.SECONDS);
  }

  private void onSaveNotification(PersistenceService.SaveNotification n) {
    var summary = n.summary();
    var recipients =
        java.util.stream.LongStream.of(summary.player1().userId(), summary.player2().userId())
            .mapToObj(sessions::user)
            .flatMap(Optional::stream)
            .map(SessionContext::connectionId)
            .toList();
    matches.publishSaveStatus(summary, n.status(), n.retryable(), n.savedAtMs(), recipients);
    if (n.status().equals("SAVED")) {
      var invalid =
          codec.envelope(
              MessageType.RANKING_INVALIDATED,
              null,
              null,
              null,
              null,
              new Payloads.RankingInvalidated(rankingRevision.incrementAndGet(), "MATCH_SAVED"));
      for (var session : sessions.online()) connections.tryEnqueue(session.connectionId(), invalid);
      pendingProfiles.add(summary.player1().userId());
      pendingProfiles.add(summary.player2().userId());
      refreshProfiles();
    }
  }

  private void refreshProfiles() {
    for (long id : java.util.Set.copyOf(pendingProfiles))
      try {
        var profile = authentication.profile(id);
        presence.cache(profile);
        sessions
            .user(id)
            .ifPresent(
                s ->
                    connections.tryEnqueue(
                        s.connectionId(),
                        codec.envelope(MessageType.PROFILE, null, null, null, null, profile)));
        pendingProfiles.remove(id);
      } catch (RuntimeException unavailable) {
        /* Health probe retries after recovery; invalidation was already sent. */
      }
    presence.broadcast();
  }

  public Map<String, Long> metrics() {
    return Map.of(
        "activeConnections",
        (long) connections.size(),
        "authenticatedUsers",
        (long) sessions.online().size(),
        "activeMatches",
        (long) matches.activeCount(),
        "pendingSaves",
        (long) persistence.pendingCount());
  }

  public void start(String host, int port) throws java.io.IOException {
    sockets.start(host, port);
  }

  public int port() {
    return sockets.port();
  }

  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    persistence.stopCreatingMatches();
    sockets.stopAccepting();
    challenges.close();
    matches.shutdown();
    try {
      int pending = persistence.awaitPending(Duration.ofSeconds(10));
      if (pending > 0)
        System.err.println("Shutdown: " + pending + " summaries remain unsaved in RAM");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    sockets.close();
    persistence.close();
    maintenance.shutdownNow();
    services.shutdownNow();
    if (ownedScheduler != null) {
      String telemetry = System.getenv("QUIZ_TELEMETRY_CSV");
      if (telemetry != null && !telemetry.isBlank())
        try {
          ownedScheduler.writeSamples(java.nio.file.Path.of(telemetry));
        } catch (java.io.IOException failure) {
          System.err.println("Could not write scheduler telemetry CSV");
        }
      ownedScheduler.close();
    }
  }
}
