package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class LeaderboardPresentationTest {
  @Test
  void authoritativeTotals() {
    var n =
        JsonNodeFactory.instance
            .objectNode()
            .put("userId", 1)
            .put("rank", 1)
            .put("scoreBefore", 7)
            .put("earnedPoints", 3)
            .put("totalScore", 10)
            .put("correctCount", 4);
    var r = LeaderboardPanel.Row.from(n);
    assertEquals(10, r.totalScore());
    assertEquals(r.totalScore(), r.scoreBefore() + r.earnedPoints());
  }
}
