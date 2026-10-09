package vn.edu.nhom7.quiz.server.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.persistence.*;
import vn.edu.nhom7.quiz.server.support.*;

class RankingHistoryIT {
  @Test
  void committedHistoryIsParticipantOnly() {
    var s = DatabaseFixtures.completed();
    new JdbcMatchRepository(TestDatabase.factory()).save(s);
    var history = new HistoryService(TestDatabase.factory());
    assertTrue(
        history.history(101, 1, 50).items().stream()
            .anyMatch(m -> m.matchId().equals(s.matchId())));
    assertEquals(10, history.detail(101, s.matchId()).review().size());
    for (var review : history.detail(101, s.matchId()).review())
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
