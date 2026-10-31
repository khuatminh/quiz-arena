package vn.edu.nhom7.quiz.server.match;

import static vn.edu.nhom7.quiz.server.match.MatchPayloads.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/** Round readiness, answer acceptance, scoring and leaderboard transitions under the match lock. */
final class MatchRounds {
  private final MatchManager manager;

  MatchRounds(MatchManager manager) {
    this.manager = manager;
  }

  void timeout(Match match) {
    switch (match.phase) {
      case WAITING_READY ->
          manager.finish(match, FinishReason.ABORTED, ReasonCode.CLIENT_NOT_READY, null);
      case MATCH_COUNTDOWN -> nextQuestion(match);
      case QUESTION_PREPARING ->
          manager.finish(match, FinishReason.ABORTED, ReasonCode.CLIENT_NOT_READY, null);
      case ROUND_COUNTDOWN -> {
        long windowMs = match.question().questionType().timeLimitMs();
        manager.phase(match, MatchPhase.ANSWERING, (int) (windowMs / 1000));
        match.startNano = match.deadline - windowMs * 1_000_000L;
        manager.emit(
            match,
            "QUESTION_OPEN",
            null,
            null,
            obj(
                "questionId",
                match.question().questionId(),
                "remainingMs",
                windowMs,
                "deadlineAtMs",
                manager.endMillis(match),
                "serverTimeMs",
                manager.now()));
      }
      case ANSWERING -> closeRound(match);
      case REVEAL -> leaderboard(match);
      case LEADERBOARD -> {
        if (match.completed == match.questions.size())
          manager.finish(match, FinishReason.COMPLETED, ReasonCode.NORMAL, null);
        else nextQuestion(match);
      }
      case RESULT -> manager.closeResult(match, "EXPIRED");
      default -> {}
    }
  }

  void nextQuestion(Match match) {
    match.index++;
    match.roundId = UUID.randomUUID();
    match.accepted.clear();
    match.roundLifeline.clear();
    match.questionReady.clear();
    manager.phase(match, MatchPhase.QUESTION_PREPARING, 30);
    manager.emit(
        match,
        "QUESTION",
        null,
        null,
        obj(
            "question",
            publicQuestion(match.question()),
            "roundIndex",
            match.index + 1,
            "totalRounds",
            match.questions.size(),
            "readyDeadlineAtMs",
            manager.endMillis(match)));
  }

  void answer(Match match, SessionContext caller, Envelope command) {
    long received = manager.clock.nanoTime();
    match.requireCurrentRound(command);
    Match.Accepted previous = match.accepted.get(caller.userId());
    if (previous != null) {
      if (Objects.equals(previous.request(), command.requestId())) {
        ack(match, caller, command.requestId(), previous);
        return;
      }
      throw new ProtocolException(
          "ALREADY_ANSWERED",
          "Answer already accepted",
          obj("acceptedRequestId", previous.request()));
    }
    if (match.phase != MatchPhase.ANSWERING)
      throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
    if (received > match.deadline) throw new ProtocolException("LATE_ANSWER", "LATE_ANSWER");
    JsonNode answer = command.payload().get("answer");
    boolean correct = manager.evaluator.evaluate(match.question(), answer);
    long elapsed = received - match.startNano;
    var lifeline = match.roundLifeline.get(caller.userId());
    int multiplier = lifeline != null && lifeline.kind().equals("DOUBLE_POINTS") ? 2 : 1;
    Match.Accepted accepted =
        new Match.Accepted(
            answer.toString(),
            elapsed,
            manager.clock.instant(),
            correct,
            ScoreCalculator.calculate(
                    correct, elapsed, match.question().questionType().timeLimitMs() * 1_000_000L)
                * multiplier,
            command.requestId());
    match.accepted.put(caller.userId(), accepted);
    ack(match, caller, command.requestId(), accepted);
    manager.emit(
        match, "ANSWER_STATUS", null, null, obj("userId", caller.userId(), "answered", true));
    if (match.accepted.size() == 2) closeRound(match);
  }

  void ack(Match match, SessionContext caller, UUID request, Match.Accepted answer) {
    manager.emit(
        match,
        "ANSWER_ACK",
        request,
        caller.connectionId(),
        obj(
            "questionId",
            match.question().questionId(),
            "accepted",
            true,
            "answerTimeMs",
            answer.elapsed() / 1_000_000L,
            "acceptedRequestId",
            answer.request()));
  }

  void closeRound(Match match) {
    if (match.phase != MatchPhase.ANSWERING) return;
    List<StoredAnswerOutcome> outcomes = outcomes(match, true);
    match.rounds.add(
        new RoundSummary(match.roundId, match.index + 1, match.question(), true, outcomes));
    match.completed++;
    manager.phase(match, MatchPhase.REVEAL, 2);
    match.latestResult =
        obj(
            "questionId",
            match.question().questionId(),
            "correctAnswer",
            correctAnswer(match.question()),
            "explanation",
            match.question().explanation(),
            "explanationAssetId",
            match.question().explanationAssetId(),
            "outcomes",
            outcomes.stream().map(MatchPayloads::publicOutcome).toList(),
            "durationMs",
            2000,
            "phaseEndsAtMs",
            manager.endMillis(match),
            "serverTimeMs",
            manager.now());
    manager.emit(match, "QUESTION_RESULT", null, null, match.latestResult);
  }

  List<StoredAnswerOutcome> outcomes(Match match, boolean finalized) {
    List<StoredAnswerOutcome> values = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long user = match.sessions.get(i).userId();
      var answer = match.accepted.get(user);
      int before = match.scores[i];
      int points = finalized && answer != null ? answer.points() : 0;
      if (finalized) {
        match.scores[i] += points;
        if (answer != null && answer.correct()) match.correct[i]++;
      }
      values.add(
          new StoredAnswerOutcome(
              user,
              answer == null ? null : answer.answer(),
              answer == null ? null : answer.elapsed(),
              answer == null ? null : answer.received(),
              !finalized
                  ? AnswerOutcomeType.ABANDONED
                  : answer == null ? AnswerOutcomeType.TIMEOUT : AnswerOutcomeType.ANSWERED,
              !finalized ? null : answer != null && answer.correct(),
              points,
              before,
              match.scores[i],
              answer == null ? null : answer.request()));
    }
    return values;
  }

  void leaderboard(Match match) {
    manager.phase(match, MatchPhase.LEADERBOARD, 3);
    List<JsonNode> rows = new ArrayList<>();
    var outcomes = match.rounds.getLast().outcomes();
    for (int i = 0; i < 2; i++) {
      var participant = match.profiles.get(i);
      var outcome = outcomes.get(i);
      int rank = MatchRanker.rank(match.scores[i], match.scores[1 - i]);
      rows.add(
          obj(
              "userId",
              participant.userId(),
              "displayName",
              participant.displayName(),
              "avatarId",
              participant.avatarId(),
              "rank",
              rank,
              "previousRank",
              match.previousRanks[i],
              "scoreBefore",
              outcome.scoreBefore(),
              "earnedPoints",
              outcome.earnedPoints(),
              "totalScore",
              match.scores[i],
              "correctCount",
              match.correct[i],
              "outcome",
              outcome.outcome().name()));
      match.previousRanks[i] = rank;
    }
    rows.sort(Comparator.comparingInt((JsonNode n) -> n.path("totalScore").asInt()).reversed());
    match.standings = JSON.valueToTree(rows);
    manager.emit(
        match,
        "ROUND_LEADERBOARD",
        null,
        null,
        obj(
            "roundIndex",
            match.index + 1,
            "standings",
            match.standings,
            "durationMs",
            3000,
            "phaseEndsAtMs",
            manager.endMillis(match),
            "serverTimeMs",
            manager.now()));
  }
}
