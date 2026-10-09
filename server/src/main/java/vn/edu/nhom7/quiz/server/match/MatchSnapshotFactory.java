package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.JsonNode;

/** Public phase-safe state. Own accepted answer is the only answer available before reveal. */
public final class MatchSnapshotFactory {
  private final MatchManager manager;

  public MatchSnapshotFactory(MatchManager manager) {
    this.manager = manager;
  }

  public JsonNode create(Match match, long userId) {
    JsonNode[] value = {null};
    manager.locked(
        match,
        () -> {
          if (!match.attached.contains(userId)
              || match.phase == MatchPhase.CLOSED
              || match.phase == MatchPhase.CANCELLED)
            throw new IllegalArgumentException("NOT_MATCH_MEMBER");
          value[0] = manager.snapshot(match, userId);
        });
    return value[0];
  }
}
