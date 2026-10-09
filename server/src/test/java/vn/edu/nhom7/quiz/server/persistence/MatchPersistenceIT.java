package vn.edu.nhom7.quiz.server.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.auth.*;
import vn.edu.nhom7.quiz.server.support.*;

class MatchPersistenceIT {
  @Test
  void communitySaveKeepsAllRankingCountersUnchanged() {
    var b = DatabaseFixtures.completed();
    var s =
        new vn.edu.nhom7.quiz.server.domain.MatchSummary(
            b.matchId(),
            b.quizId(),
            b.quizTitle(),
            b.player1(),
            b.player2(),
            b.startedAt(),
            b.endedAt(),
            b.finishReason(),
            b.reasonCode(),
            b.outcome(),
            b.completedRounds(),
            b.openedRounds(),
            b.rounds(),
            false,
            0,
            10);
    var counters = java.util.List.of("total_score", "total_matches", "wins", "losses", "draws");
    var before = new java.util.HashMap<String, Long>();
    for (var name : counters)
      for (long id : java.util.List.of(101L, 102L))
        before.put(
            name + id, DatabaseFixtures.scalar("SELECT " + name + " FROM USERS WHERE id=" + id));
    var repo = new JdbcMatchRepository(TestDatabase.factory());
    assertEquals(SaveResult.SAVED, repo.save(s));
    assertEquals(SaveResult.ALREADY_SAVED, repo.save(s));
    assertFalse(repo.load(s.matchId()).ranked());
    for (var name : counters)
      for (long id : java.util.List.of(101L, 102L))
        assertEquals(
            before.get(name + id),
            DatabaseFixtures.scalar("SELECT " + name + " FROM USERS WHERE id=" + id));
  }

  @Test
  void completedMatchCommitsExactlyOnce() {
    var summary = DatabaseFixtures.completed();
    long before = DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101");
    var repo = new JdbcMatchRepository(TestDatabase.factory());
    assertEquals(SaveResult.SAVED, repo.save(summary));
    assertEquals(SaveResult.ALREADY_SAVED, repo.save(summary));
    assertEquals(
        before + 30, DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101"));
    assertEquals(
        10,
        DatabaseFixtures.scalar(
            "SELECT COUNT(*) FROM MATCH_QUESTIONS WHERE match_id='" + summary.matchId() + "'"));
    assertEquals(
        20,
        DatabaseFixtures.scalar(
            "SELECT COUNT(*) FROM MATCH_ANSWERS a JOIN MATCH_QUESTIONS q ON q.round_id=a.round_id"
                + " WHERE q.match_id='"
                + summary.matchId()
                + "'"));
  }

  @Test
  void faultsRollbackAllRowsAndCounters() {
    for (var point : new String[] {"after_round", "after_answers", "after_user"}) {
      var s = DatabaseFixtures.completed();
      long before = DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101");
      var repo =
          new JdbcMatchRepository(
              TestDatabase.factory(),
              p -> {
                if (p.equals(point)) throw new SQLException("injected");
              });
      assertThrows(ServiceException.class, () -> repo.save(s));
      assertNull(new JdbcMatchRepository(TestDatabase.factory()).load(s.matchId()));
      assertEquals(before, DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101"));
    }
  }

  @Test
  void lostCommitResponseIsResolvedByRetry() {
    var s = DatabaseFixtures.completed();
    var first = new AtomicBoolean(true);
    var repo =
        new JdbcMatchRepository(
            TestDatabase.factory(),
            p -> {
              if (p.equals("after_commit") && first.getAndSet(false))
                throw new SQLException("commit response lost");
            });
    long before = DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101");
    assertThrows(ServiceException.class, () -> repo.save(s));
    assertEquals(SaveResult.ALREADY_SAVED, repo.save(s));
    assertEquals(
        before + 30, DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101"));
  }

  @Test
  void sameIdWithDifferentSummaryIsRejected() {
    var original = DatabaseFixtures.completed();
    var repository = new JdbcMatchRepository(TestDatabase.factory());
    repository.save(original);
    var changed =
        new vn.edu.nhom7.quiz.server.domain.MatchSummary(
            original.matchId(),
            original.quizId(),
            "Changed title",
            original.player1(),
            original.player2(),
            original.startedAt(),
            original.endedAt(),
            original.finishReason(),
            original.reasonCode(),
            original.outcome(),
            original.completedRounds(),
            original.openedRounds(),
            original.rounds());
    long before = DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101");
    assertEquals(
        "INTERNAL_ERROR",
        assertThrows(ServiceException.class, () -> repository.save(changed)).code());
    assertEquals(before, DatabaseFixtures.scalar("SELECT total_score FROM USERS WHERE id=101"));
  }

  @Test
  void abortPersistsHistoryWithoutStatsAndForfeitUsesWinner() {
    var base = DatabaseFixtures.completed();
    var repository = new JdbcMatchRepository(TestDatabase.factory());
    long before = DatabaseFixtures.scalar("SELECT total_matches FROM USERS WHERE id=101");
    var aborted =
        new vn.edu.nhom7.quiz.server.domain.MatchSummary(
            java.util.UUID.randomUUID(),
            base.quizId(),
            base.quizTitle(),
            new vn.edu.nhom7.quiz.server.domain.ParticipantSummary(101, "An", "avatar-1", 0, 0),
            new vn.edu.nhom7.quiz.server.domain.ParticipantSummary(102, "Bình", "avatar-2", 0, 0),
            base.startedAt(),
            base.endedAt(),
            vn.edu.nhom7.quiz.server.domain.FinishReason.ABORTED,
            vn.edu.nhom7.quiz.server.domain.ReasonCode.CLIENT_NOT_READY,
            vn.edu.nhom7.quiz.server.domain.MatchOutcome.NONE,
            0,
            0,
            java.util.List.of());
    repository.save(aborted);
    assertEquals(before, DatabaseFixtures.scalar("SELECT total_matches FROM USERS WHERE id=101"));
    var forfeit =
        new vn.edu.nhom7.quiz.server.domain.MatchSummary(
            java.util.UUID.randomUUID(),
            base.quizId(),
            base.quizTitle(),
            aborted.player1(),
            aborted.player2(),
            base.startedAt(),
            base.endedAt(),
            vn.edu.nhom7.quiz.server.domain.FinishReason.FORFEIT,
            vn.edu.nhom7.quiz.server.domain.ReasonCode.DISCONNECT,
            vn.edu.nhom7.quiz.server.domain.MatchOutcome.PLAYER2_WIN,
            0,
            0,
            java.util.List.of());
    long wins = DatabaseFixtures.scalar("SELECT wins FROM USERS WHERE id=102");
    repository.save(forfeit);
    assertEquals(wins + 1, DatabaseFixtures.scalar("SELECT wins FROM USERS WHERE id=102"));
    assertEquals(
        before + 1, DatabaseFixtures.scalar("SELECT total_matches FROM USERS WHERE id=101"));
  }
}
