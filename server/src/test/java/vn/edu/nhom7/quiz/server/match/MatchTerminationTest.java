package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.persistence.*;
import vn.edu.nhom7.quiz.server.support.*;

class MatchTerminationTest {
  static Stream<Arguments> phases() {
    return Stream.of(
            MatchPhase.WAITING_READY,
            MatchPhase.MATCH_COUNTDOWN,
            MatchPhase.QUESTION_PREPARING,
            MatchPhase.ROUND_COUNTDOWN,
            MatchPhase.ANSWERING,
            MatchPhase.REVEAL,
            MatchPhase.LEADERBOARD)
        .flatMap(
            p ->
                Stream.of(ReasonCode.USER_EXIT, ReasonCode.LOGOUT, ReasonCode.DISCONNECT)
                    .map(r -> Arguments.of(p, r)));
  }

  @ParameterizedTest
  @MethodSource("phases")
  void exitLogoutDisconnectAtEveryPhase(MatchPhase desired, ReasonCode reason) {
    var engine = new MatchEngineTest();
    engine.setup();
    switch (desired) {
      case WAITING_READY -> {}
      case MATCH_COUNTDOWN -> {
        engine.command(0, "MATCH_READY");
        engine.command(1, "MATCH_READY");
      }
      case QUESTION_PREPARING -> engine.start();
      case ROUND_COUNTDOWN -> {
        engine.start();
        engine.command(0, "QUESTION_READY", "questionId", 1);
        engine.command(1, "QUESTION_READY", "questionId", 1);
      }
      case ANSWERING -> {
        engine.start();
        engine.open();
      }
      case REVEAL, LEADERBOARD -> {
        engine.start();
        engine.open();
        engine.command(0, "ANSWER", "questionId", 1, "answer", "A");
        engine.command(1, "ANSWER", "questionId", 1, "answer", "A");
        if (desired == MatchPhase.LEADERBOARD) engine.scheduler.advance(Duration.ofSeconds(2));
      }
      default -> throw new AssertionError();
    }
    assertEquals(desired, engine.match.phase());
    var caller = ServerFixtures.players().getFirst();
    switch (reason) {
      case USER_EXIT -> engine.command(0, "EXIT_MATCH");
      case LOGOUT -> engine.manager.onLogout(caller);
      case DISCONNECT -> engine.manager.onDisconnected(caller);
      default -> throw new AssertionError();
    }
    if (desired == MatchPhase.WAITING_READY) {
      assertEquals(MatchPhase.CANCELLED, engine.match.phase());
      assertTrue(engine.saved.isEmpty());
      return;
    }
    var summary = engine.saved.getFirst();
    assertEquals(FinishReason.FORFEIT, summary.finishReason());
    assertEquals(reason, summary.reasonCode());
    assertEquals(MatchOutcome.PLAYER2_WIN, summary.outcome());
    int finalized = desired == MatchPhase.REVEAL || desired == MatchPhase.LEADERBOARD ? 1 : 0;
    assertEquals(finalized, summary.completedRounds());
    assertEquals(finalized * 3, summary.player1().totalScore());
    engine.manager.onDisconnected(ServerFixtures.players().get(1));
    assertEquals(1, engine.saved.size());
    assertEquals(summary, engine.match.summary().orElseThrow());
  }

  @Test
  void bothOfflineBeforeCloseAbortsWithoutWinner() {
    var clock = new FakeGameClock();
    var scheduler = new FakeGameScheduler(clock);
    var transport = new RecordingTransport();
    var sink = new RecordingResultSink();
    Set<UUID> offline = new HashSet<>();
    var manager =
        new MatchManager(
            clock,
            scheduler,
            transport,
            sink,
            id -> new ParticipantSummary(id, "Player", "avatar-01", 0, 0),
            c -> !offline.contains(c),
            (u, m) -> {});
    var match =
        manager.prepare(
            UUID.randomUUID(),
            ServerFixtures.quiz(),
            ServerFixtures.players(),
            ServerFixtures.questions());
    manager.activate(match);
    manager.locked(
        match,
        () -> {
          match.startedAt = clock.instant();
          manager.phase(match, MatchPhase.MATCH_COUNTDOWN, 3);
        });
    ServerFixtures.players().forEach(p -> offline.add(p.connectionId()));
    manager.onDisconnected(ServerFixtures.players().getFirst());
    var summary = match.summary().orElseThrow();
    assertEquals(FinishReason.ABORTED, summary.finishReason());
    assertEquals(ReasonCode.BOTH_DISCONNECTED, summary.reasonCode());
    assertEquals(MatchOutcome.NONE, summary.outcome());
  }
}
