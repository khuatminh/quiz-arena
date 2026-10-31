package vn.edu.nhom7.quiz.server.support;

import java.sql.*;
import java.time.*;
import java.util.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.quiz.*;

public final class DatabaseFixtures {
  public static MatchSummary completed() {
    var c = TestDatabase.factory();
    try (var db = c.open();
        var p =
            db.prepareStatement(
                "INSERT IGNORE INTO"
                    + " USERS(id,username,display_name,avatar_id,password_hash,created_at)"
                    + " VALUES(101,'fixture101','An','avatar-1','unused',UTC_TIMESTAMP(3)),(102,'fixture102','Bình','avatar-2','unused',UTC_TIMESTAMP(3)),(103,'fixture103','Ngoài','avatar-3','unused',UTC_TIMESTAMP(3))")) {
      p.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    var base = ServerFixtures.completedSummary();
    var questions = new JdbcQuizRepository(c).loadMatchQuestions(1, new Random(7));
    var rounds = new ArrayList<RoundSummary>();
    UUID match = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      var r = base.rounds().get(i);
      var q = questions.get(i);
      rounds.add(new RoundSummary(UUID.randomUUID(), i + 1, q, true, r.outcomes()));
    }
    return new MatchSummary(
        match,
        1,
        base.quizTitle(),
        base.player1(),
        base.player2(),
        base.startedAt(),
        base.endedAt(),
        base.finishReason(),
        base.reasonCode(),
        base.outcome(),
        10,
        10,
        rounds);
  }

  public static long scalar(String sql) {
    try (var c = TestDatabase.factory().open();
        var p = c.prepareStatement(sql);
        var r = p.executeQuery()) {
      r.next();
      return r.getLong(1);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
