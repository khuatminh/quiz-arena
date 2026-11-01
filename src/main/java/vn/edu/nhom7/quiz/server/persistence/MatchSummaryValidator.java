package vn.edu.nhom7.quiz.server.persistence;

import java.util.*;
import vn.edu.nhom7.quiz.server.domain.*;

/** Verifies round, participant and score invariants before any database mutation. */
final class MatchSummaryValidator {
  private MatchSummaryValidator() {}

  static void validate(MatchSummary summary) {
    if (summary.player1().userId() == summary.player2().userId()
        || summary.totalRounds() < 1
        || summary.totalRounds() > 50
        || summary.rounds().size() > summary.totalRounds()
        || summary.openedRounds() != summary.rounds().size()
        || summary.completedRounds()
            != summary.rounds().stream().filter(RoundSummary::revealed).count()
        || summary.startedAt() == null
        || summary.endedAt() == null) throw new IllegalArgumentException("Invalid match summary");
    if (summary.finishReason() == FinishReason.COMPLETED
        && (summary.rounds().size() != summary.totalRounds()
            || summary.completedRounds() != summary.totalRounds()))
      throw new IllegalArgumentException("Completed match requires all rounds");
    var totals = new HashMap<Long, Integer>();
    var counts = new HashMap<Long, Integer>();
    var indices = new HashSet<Integer>();
    for (var round : summary.rounds()) {
      if (round.roundIndex() < 1
          || round.roundIndex() > summary.totalRounds()
          || !indices.add(round.roundIndex())
          || round.outcomes().size() != 2) throw new IllegalArgumentException("Invalid rounds");
      var users = new HashSet<Long>();
      for (var answer : round.outcomes()) {
        if (!users.add(answer.userId())
            || (answer.userId() != summary.player1().userId()
                && answer.userId() != summary.player2().userId()))
          throw new IllegalArgumentException("Invalid participants");
        int before = totals.getOrDefault(answer.userId(), 0);
        if (before != answer.scoreBefore()
            || answer.totalScore() != before + answer.earnedPoints()
            || answer.earnedPoints() < 0
            || answer.earnedPoints() > 6)
          throw new IllegalArgumentException("Inconsistent score projection");
        totals.put(answer.userId(), answer.totalScore());
        if (Boolean.TRUE.equals(answer.correct())) counts.merge(answer.userId(), 1, Integer::sum);
      }
    }
    for (var player : List.of(summary.player1(), summary.player2()))
      if (player.totalScore() != totals.getOrDefault(player.userId(), 0)
          || player.correctCount() != counts.getOrDefault(player.userId(), 0))
        throw new IllegalArgumentException("Final score mismatch");
  }
}
