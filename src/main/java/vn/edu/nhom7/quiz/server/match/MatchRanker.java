package vn.edu.nhom7.quiz.server.match;

import java.util.*;
import vn.edu.nhom7.quiz.server.domain.ParticipantSummary;

public final class MatchRanker {
  private MatchRanker() {}

  public static int rank(int ownScore, int otherScore) {
    return ownScore >= otherScore ? 1 : 2;
  }

  public static List<ParticipantSummary> rank(List<ParticipantSummary> players) {
    return players.stream()
        .sorted(Comparator.comparingInt(ParticipantSummary::totalScore).reversed())
        .toList();
  }
}
