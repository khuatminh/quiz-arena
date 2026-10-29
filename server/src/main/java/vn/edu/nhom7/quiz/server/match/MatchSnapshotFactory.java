package vn.edu.nhom7.quiz.server.match;

import static vn.edu.nhom7.quiz.server.match.MatchPayloads.*;

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
          value[0] = snapshotLocked(match, userId, manager.clock);
        });
    return value[0];
  }

  static JsonNode snapshotLocked(Match match, long user, GameClock clock) {
    JsonNode accepted = null;
    var answer = match.accepted.get(user);
    if (answer != null)
      accepted =
          obj(
              "answer",
              parse(answer.answer()),
              "answerTimeMs",
              answer.elapsed() / 1_000_000L,
              "acceptedRequestId",
              answer.request());
    return obj(
        "phase",
        match.phase.name(),
        "phaseVersion",
        match.version,
        "serverTimeMs",
        clock.instant().toEpochMilli(),
        "phaseRemainingMs",
        Math.max(0, (match.deadline - clock.nanoTime()) / 1_000_000L),
        "quiz",
        match.quiz,
        "players",
        players(match),
        "roundIndex",
        match.index + 1,
        "totalRounds",
        match.questions.size(),
        "question",
        match.index < 0 ? null : publicQuestion(match.question()),
        "acceptedAnswer",
        accepted,
        "answeredUserIds",
        match.accepted.keySet(),
        "latestResult",
        match.phase == MatchPhase.REVEAL
                || match.phase == MatchPhase.LEADERBOARD
                || match.phase == MatchPhase.RESULT
            ? match.latestResult
            : null,
        "standings",
        match.standings,
        "lifelines",
        MatchLifelines.lifelineState(match, user));
  }
}
