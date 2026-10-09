package vn.edu.nhom7.quiz.server.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.persistence.*;
import vn.edu.nhom7.quiz.server.support.*;

class RankingHistoryIT {
  @Test
  void historyMediaRequiresParticipantAndRevealedSnapshot() {
    var b = DatabaseFixtures.completed();
    var rounds = new java.util.ArrayList<vn.edu.nhom7.quiz.server.domain.RoundSummary>();
    for (var r : b.rounds()) {
      var q = r.question();
      var image =
          new vn.edu.nhom7.quiz.server.domain.QuestionSnapshot(
              q.questionId(),
              q.quizId(),
              q.questionType(),
              q.content(),
              q.options(),
              q.answerKeyJson(),
              q.explanation(),
              "history-question-" + b.matchId(),
              "history-explanation-" + b.matchId());
      rounds.add(
          new vn.edu.nhom7.quiz.server.domain.RoundSummary(
              r.roundId(), r.roundIndex(), image, r.revealed(), r.outcomes()));
    }
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
            rounds);
    new JdbcMatchRepository(TestDatabase.factory()).save(s);
    var history = new HistoryService(TestDatabase.factory());
    assertTrue(history.canAccessMedia(101, "history-question-" + b.matchId()));
    assertTrue(history.canAccessMedia(102, "history-explanation-" + b.matchId()));
    assertFalse(history.canAccessMedia(103, "history-question-" + b.matchId()));
    assertFalse(history.canAccessMedia(103, "history-explanation-" + b.matchId()));
  }

  @Test
  void abandonedHistoryHidesExplanationAndRefusesItsMedia() {
    var b = DatabaseFixtures.completed();
    var original = b.rounds().getFirst();
    var q = original.question();
    var image =
        new vn.edu.nhom7.quiz.server.domain.QuestionSnapshot(
            q.questionId(),
            q.quizId(),
            q.questionType(),
            q.content(),
            q.options(),
            q.answerKeyJson(),
            q.explanation(),
            "abandoned-question-" + b.matchId(),
            "abandoned-explanation-" + b.matchId());
    var outcomes =
        java.util.stream.Stream.of(101L, 102L)
            .map(
                id ->
                    new vn.edu.nhom7.quiz.server.domain.StoredAnswerOutcome(
                        id,
                        null,
                        null,
                        null,
                        vn.edu.nhom7.quiz.server.domain.AnswerOutcomeType.ABANDONED,
                        null,
                        0,
                        0,
                        0,
                        null))
            .toList();
    var round =
        new vn.edu.nhom7.quiz.server.domain.RoundSummary(
            original.roundId(), 1, image, false, outcomes);
    var s =
        new vn.edu.nhom7.quiz.server.domain.MatchSummary(
            b.matchId(),
            b.quizId(),
            b.quizTitle(),
            new vn.edu.nhom7.quiz.server.domain.ParticipantSummary(101, "An", "avatar-1", 0, 0),
            new vn.edu.nhom7.quiz.server.domain.ParticipantSummary(102, "Binh", "avatar-2", 0, 0),
            b.startedAt(),
            b.endedAt(),
            vn.edu.nhom7.quiz.server.domain.FinishReason.ABORTED,
            vn.edu.nhom7.quiz.server.domain.ReasonCode.CLIENT_NOT_READY,
            vn.edu.nhom7.quiz.server.domain.MatchOutcome.NONE,
            0,
            1,
            java.util.List.of(round));
    new JdbcMatchRepository(TestDatabase.factory()).save(s);
    var history = new HistoryService(TestDatabase.factory());
    assertTrue(history.canAccessMedia(101, "abandoned-question-" + b.matchId()));
    assertFalse(history.canAccessMedia(101, "abandoned-explanation-" + b.matchId()));
    var review = history.detail(101, s.matchId()).review().getFirst();
    assertNull(review.correctAnswer());
    assertNull(review.explanation());
    assertNull(review.explanationAssetId());
  }

  @Test
  void committedHistoryIsParticipantOnly() {
    var s = DatabaseFixtures.completed();
    new JdbcMatchRepository(TestDatabase.factory()).save(s);
    var history = new HistoryService(TestDatabase.factory());
    var firstPage = history.history(101, 1, 50);
    var seen = new java.util.HashSet<java.util.UUID>();
    for (int page = 1; (long) (page - 1) * firstPage.pageSize() < firstPage.totalItems(); page++) {
      for (var item : history.history(101, page, 50).items())
        assertTrue(seen.add(item.matchId()), "History pages must not duplicate matches");
    }
    assertTrue(seen.contains(s.matchId()));
    assertEquals(firstPage.totalItems(), seen.size());
    assertEquals(2, history.detail(101, s.matchId()).review().size());
    assertEquals(10, history.detail(101, s.matchId()).totalItems());
    assertEquals(2, history.detail(101, s.matchId(), 2).review().size());
    for (var review : history.detail(101, s.matchId(), 5).review())
      if (review.question().questionType().equals("SHORT_ANSWER"))
        assertTrue(review.correctAnswer().isTextual());
    assertEquals(
        "NOT_MATCH_MEMBER",
        assertThrows(ServiceException.class, () -> history.detail(103, s.matchId())).code());
    var ranking = new RankingService(TestDatabase.factory()).ranking(102, 1, 1);
    assertNotNull(ranking.myRank());
    assertEquals(102, ranking.myRank().userId());
  }

  @Test
  void competitionTiesAndMyRankUseTheWholeTable() throws Exception {
    var factory = TestDatabase.factory();
    try (var c = factory.open();
        var p =
            c.prepareStatement(
                "INSERT INTO"
                    + " USERS(username,display_name,avatar_id,password_hash,total_score,total_matches,wins,created_at)"
                    + " VALUES(?,?,?, ?,900000000001,7,7,UTC_TIMESTAMP(3))")) {
      for (int i = 0; i < 2; i++) {
        p.setString(1, "tie_" + java.util.UUID.randomUUID().toString().substring(0, 12));
        p.setString(2, "Tie");
        p.setString(3, "avatar-01");
        p.setString(4, "unused");
        p.executeUpdate();
      }
    }
    var ranking = new RankingService(factory).ranking(103, 1, 2);
    assertEquals(ranking.entries().get(0).rank(), ranking.entries().get(1).rank());
    assertEquals(1, ranking.entries().get(0).rank());
    assertNotNull(ranking.myRank());
    assertTrue(ranking.myRank().rank() >= 3);
  }
}
