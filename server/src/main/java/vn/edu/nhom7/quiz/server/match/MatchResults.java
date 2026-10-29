package vn.edu.nhom7.quiz.server.match;

import static vn.edu.nhom7.quiz.server.match.MatchPayloads.*;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/** Match completion, forfeits and the attached result session under the match lock. */
final class MatchResults {
  private final MatchManager manager;

  MatchResults(MatchManager manager) {
    this.manager = manager;
  }

  void finish(Match match, FinishReason reason, ReasonCode code, Long loser) {
    if (match.terminal != null
        || match.phase == MatchPhase.CANCELLED
        || match.phase == MatchPhase.CLOSED) return;
    if (match.startedAt == null) {
      if (match.timer != null) match.timer.cancel();
      match.phase = MatchPhase.CANCELLED;
      match.version++;
      manager.emit(match, "RESULT_SESSION_CLOSED", null, null, obj("reason", "EXITED"));
      for (var s : match.sessions) manager.after(() -> manager.detach.accept(s.userId(), match.id));
      manager.after(() -> manager.sink.cancelReservation(match.id));
      manager.after(() -> manager.matches.remove(match.id, match));
      return;
    }
    if (!manager.alive.test(match.sessions.get(0).connectionId())
        && !manager.alive.test(match.sessions.get(1).connectionId())) {
      reason = FinishReason.ABORTED;
      code = ReasonCode.BOTH_DISCONNECTED;
    }
    if (match.index >= 0 && match.rounds.size() <= match.index)
      match.rounds.add(
          new RoundSummary(
              match.roundId,
              match.index + 1,
              match.question(),
              false,
              manager.rounds.outcomes(match, false)));
    match.endedAt = manager.clock.instant();
    MatchOutcome outcome = determineOutcome(match, reason, loser);
    match.terminal =
        new MatchSummary(
            match.id,
            match.quiz.quizId(),
            match.quiz.title(),
            participant(match, 0),
            participant(match, 1),
            match.startedAt,
            match.endedAt,
            reason,
            code,
            outcome,
            match.completed,
            match.index + 1,
            match.rounds,
            !"COMMUNITY".equals(match.quiz.quizSource()),
            match.quiz.quizVersionId(),
            match.questions.size());
    manager.phase(match, MatchPhase.RESULT, 60);
    match.resultDeadline = match.deadline;
    Long winner =
        switch (outcome) {
          case PLAYER1_WIN -> match.sessions.get(0).userId();
          case PLAYER2_WIN -> match.sessions.get(1).userId();
          default -> null;
        };
    manager.savingMatches.put(match.id, match);
    manager.after(() -> manager.sink.submit(match.terminal));
    manager.emit(
        match,
        "MATCH_RESULT",
        null,
        null,
        obj(
            "finishReason",
            reason.name(),
            "reasonCode",
            code.name(),
            "winnerUserId",
            winner,
            "players",
            players(match),
            "completedRounds",
            match.completed,
            "openedRounds",
            match.index + 1,
            "review",
            match.rounds.stream()
                .limit(Payloads.REVIEW_PAGE_SIZE)
                .map(MatchPayloads::review)
                .toList(),
            "reviewPage",
            1,
            "reviewTotalItems",
            match.rounds.size(),
            "totalRounds",
            match.questions.size(),
            "ranked",
            match.terminal.ranked(),
            "persistenceStatus",
            "PENDING",
            "resultExpiresAtMs",
            manager.endMillis(match)));
  }

  private static MatchOutcome determineOutcome(Match match, FinishReason reason, Long loser) {
    if (reason == FinishReason.ABORTED) return MatchOutcome.NONE;
    if (reason == FinishReason.FORFEIT) {
      return Objects.equals(loser, match.sessions.get(0).userId())
          ? MatchOutcome.PLAYER2_WIN
          : MatchOutcome.PLAYER1_WIN;
    }
    if (match.scores[0] == match.scores[1]) return MatchOutcome.DRAW;
    return match.scores[0] > match.scores[1] ? MatchOutcome.PLAYER1_WIN : MatchOutcome.PLAYER2_WIN;
  }

  void exit(Match match, SessionContext caller, ReasonCode reason, UUID request) {
    if (match.phase != MatchPhase.RESULT && match.phase != MatchPhase.CLOSED)
      finish(match, FinishReason.FORFEIT, reason, caller.userId());
    invalidateRematch(match);
    match.attached.remove(caller.userId());
    match.rematchGeneration++;
    match.rematchRequester = null;
    manager.after(() -> manager.detach.accept(caller.userId(), match.id));
    manager.emit(
        match,
        "EXIT_ACK",
        request,
        caller.connectionId(),
        obj("reason", reason.name(), "lobbyStatus", "FREE"));
    manager.emit(
        match, "RESULT_SESSION_CLOSED", null, caller.connectionId(), obj("reason", "EXITED"));
    if (match.attached.isEmpty() && match.phase == MatchPhase.RESULT) closeResult(match, "EXITED");
  }

  void invalidateRematch(Match match) {
    if (match.rematchRequester != null) {
      manager.emit(
          match,
          "REMATCH_STATUS",
          null,
          null,
          obj(
              "status",
              "INVALIDATED",
              "requesterUserId",
              match.rematchRequester,
              "expiresAtMs",
              null,
              "newMatchId",
              null));
      match.rematchRequester = null;
      match.rematchLoading = false;
      match.rematchGeneration++;
    }
  }

  void closeResult(Match match, String reason) {
    invalidateRematch(match);
    if (match.timer != null) match.timer.cancel();
    match.version++;
    match.phase = MatchPhase.CLOSED;
    match.rematchGeneration++;
    for (var s : match.sessions)
      if (match.attached.remove(s.userId())) {
        manager.emit(match, "RESULT_SESSION_CLOSED", null, s.connectionId(), obj("reason", reason));
        manager.after(() -> manager.detach.accept(s.userId(), match.id));
      }
    manager.after(() -> manager.matches.remove(match.id, match));
  }
}
