package vn.edu.nhom7.quiz.server.session;

import java.util.*;

public record AssignmentSnapshot(UUID matchId, long generation, List<SessionContext> players) {
  public AssignmentSnapshot {
    players = List.copyOf(players);
  }
}
