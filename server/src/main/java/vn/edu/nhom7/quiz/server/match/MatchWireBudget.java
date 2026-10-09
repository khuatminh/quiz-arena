package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;

/** Reject banks whose largest review page cannot fit the 64 KiB frame. */
public final class MatchWireBudget {
  private MatchWireBudget() {}

  public static void validate(
      Payloads.QuizSummary quiz,
      List<QuestionSnapshot> questions,
      List<ParticipantSummary> players) {
    List<JsonNode> reviews = new ArrayList<>();
    UUID uuid = new UUID(-1, -1);
    for (int index = 0; index < questions.size(); index++) {
      QuestionSnapshot q = questions.get(index);
      JsonNode largest = largestAnswer(q);
      List<JsonNode> outcomes = new ArrayList<>();
      for (var p : players)
        outcomes.add(
            MatchManager.obj(
                "userId",
                p.userId(),
                "answer",
                largest,
                "outcome",
                "ABANDONED",
                "correct",
                false,
                "answerTimeMs",
                15000,
                "earnedPoints",
                3,
                "scoreBefore",
                150,
                "totalScore",
                30));
      reviews.add(
          MatchManager.obj(
              "roundId",
              uuid,
              "roundIndex",
              index + 1,
              "question",
              MatchManager.publicQuestion(q),
              "correctAnswer",
              MatchManager.correctAnswer(q),
              "explanation",
              q.explanation(),
              "explanationAssetId",
              q.explanationAssetId(),
              "outcomes",
              outcomes,
              "revealed",
              false));
    }
    List<JsonNode> summaries =
        players.stream()
            .map(
                p ->
                    (JsonNode)
                        MatchManager.obj(
                            "userId",
                            p.userId(),
                            "displayName",
                            p.displayName(),
                            "avatarId",
                            p.avatarId(),
                            "totalScore",
                            150,
                            "correctCount",
                            50,
                            "rank",
                            1))
            .toList();
    // Both result and history include these same reviews. The two-row review page plus combined
    // headers deliberately
    // overestimate either message, including maximum-length timestamps and identifiers.
    JsonNode payload =
        MatchManager.obj(
            "finishReason",
            "COMPLETED",
            "reasonCode",
            "BOTH_DISCONNECTED",
            "winnerUserId",
            Long.MAX_VALUE,
            "players",
            summaries,
            "completedRounds",
            50,
            "openedRounds",
            50,
            "review",
            reviews.stream()
                .sorted(Comparator.comparingInt(MatchWireBudget::bytes).reversed())
                .limit(Payloads.REVIEW_PAGE_SIZE)
                .toList(),
            "persistenceStatus",
            "PENDING",
            "resultExpiresAtMs",
            Long.MAX_VALUE,
            "summary",
            MatchManager.obj(
                "matchId",
                uuid,
                "quizId",
                quiz.quizId(),
                "quizTitle",
                quiz.title(),
                "opponentUserId",
                Long.MAX_VALUE,
                "opponentDisplayName",
                players.getFirst().displayName(),
                "ownScore",
                150,
                "opponentScore",
                150,
                "outcome",
                "LOSS",
                "finishReason",
                "COMPLETED",
                "reasonCode",
                "BOTH_DISCONNECTED",
                "startedAtMs",
                Long.MAX_VALUE,
                "endedAtMs",
                Long.MAX_VALUE));
    var envelope =
        new Envelope(1, MessageType.MATCH_RESULT, uuid, uuid, uuid, Long.MAX_VALUE, payload);
    if (bytes(MatchManager.JSON.valueToTree(envelope)) > 65_536)
      throw new ProtocolException(
          "QUIZ_UNAVAILABLE", "Quiz review exceeds the protocol frame budget");
  }

  private static JsonNode largestAnswer(QuestionSnapshot q) {
    return switch (q.questionType()) {
      case TRUE_FALSE -> MatchManager.JSON.valueToTree(false);
      case MULTIPLE_CHOICE ->
          MatchManager.JSON.valueToTree(q.options().stream().map(Payloads.Option::id).toList());
      case SINGLE_CHOICE ->
          q.options().stream()
              .<JsonNode>map(o -> MatchManager.JSON.valueToTree(o.id()))
              .max(Comparator.comparingInt(MatchWireBudget::bytes))
              .orElseThrow();
      case SHORT_ANSWER -> {
        var emoji = MatchManager.JSON.valueToTree("😀".repeat(120));
        var controls = MatchManager.JSON.valueToTree("\u0000".repeat(120));
        var surrogates = MatchManager.JSON.valueToTree("\ud800".repeat(120));
        yield List.of(emoji, controls, surrogates).stream()
            .max(Comparator.comparingInt(MatchWireBudget::bytes))
            .orElseThrow();
      }
    };
  }

  private static int bytes(JsonNode node) {
    try {
      return MatchManager.JSON.writeValueAsBytes(node).length;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to size public review", e);
    }
  }
}
