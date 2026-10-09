package vn.edu.nhom7.quiz.server.domain;

import java.time.Instant;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public record MatchSummary(
    UUID matchId,
    long quizId,
    String quizTitle,
    ParticipantSummary player1,
    ParticipantSummary player2,
    Instant startedAt,
    Instant endedAt,
    FinishReason finishReason,
    ReasonCode reasonCode,
    MatchOutcome outcome,
    int completedRounds,
    int openedRounds,
    List<RoundSummary> rounds,
    boolean ranked,
    long quizVersionId,
    int totalRounds) {
  public MatchSummary(
      UUID matchId,
      long quizId,
      String quizTitle,
      ParticipantSummary player1,
      ParticipantSummary player2,
      Instant startedAt,
      Instant endedAt,
      FinishReason finishReason,
      ReasonCode reasonCode,
      MatchOutcome outcome,
      int completedRounds,
      int openedRounds,
      List<RoundSummary> rounds) {
    this(
        matchId,
        quizId,
        quizTitle,
        player1,
        player2,
        startedAt,
        endedAt,
        finishReason,
        reasonCode,
        outcome,
        completedRounds,
        openedRounds,
        rounds,
        true,
        0,
        10);
  }

  public MatchSummary {
    rounds = List.copyOf(rounds);
  }
}
