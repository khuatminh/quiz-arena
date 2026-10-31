package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;

/** Maps match state to public wire data. Callers hold the match lock while reading state. */
final class MatchPayloads {
  static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .findAndRegisterModules();

  private MatchPayloads() {}

  static ParticipantSummary participant(Match match, int i) {
    var participant = match.profiles.get(i);
    return new ParticipantSummary(
        participant.userId(),
        participant.displayName(),
        participant.avatarId(),
        match.scores[i],
        match.correct[i]);
  }

  static List<JsonNode> players(Match match) {
    List<JsonNode> result = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      var participant = participant(match, i);
      result.add(
          obj(
              "userId",
              participant.userId(),
              "displayName",
              participant.displayName(),
              "avatarId",
              participant.avatarId(),
              "totalScore",
              participant.totalScore(),
              "correctCount",
              participant.correctCount(),
              "rank",
              MatchRanker.rank(match.scores[i], match.scores[1 - i])));
    }
    return result;
  }

  static JsonNode review(RoundSummary round) {
    return obj(
        "roundId",
        round.roundId(),
        "roundIndex",
        round.roundIndex(),
        "question",
        publicQuestion(round.question()),
        "correctAnswer",
        round.revealed() ? correctAnswer(round.question()) : null,
        "explanation",
        round.revealed() ? round.question().explanation() : null,
        "explanationAssetId",
        round.revealed() ? round.question().explanationAssetId() : null,
        "outcomes",
        round.outcomes().stream().map(MatchPayloads::publicOutcome).toList(),
        "revealed",
        round.revealed());
  }

  static JsonNode publicOutcome(StoredAnswerOutcome answer) {
    return obj(
        "userId",
        answer.userId(),
        "answer",
        answer.answerJson() == null ? null : parse(answer.answerJson()),
        "outcome",
        answer.outcome().name(),
        "correct",
        answer.correct(),
        "answerTimeMs",
        answer.elapsedNanos() == null ? null : answer.elapsedNanos() / 1_000_000L,
        "earnedPoints",
        answer.earnedPoints(),
        "scoreBefore",
        answer.scoreBefore(),
        "totalScore",
        answer.totalScore());
  }

  static JsonNode publicQuestion(QuestionSnapshot question) {
    return obj(
        "questionId",
        question.questionId(),
        "questionType",
        question.questionType().name(),
        "content",
        question.content(),
        "options",
        question.options(),
        "questionAssetId",
        question.questionAssetId(),
        "timeLimitMs",
        question.questionType().timeLimitMs(),
        "config",
        InteractiveQuestions.publicConfig(
            question.questionType(), parse(question.answerKeyJson())));
  }

  static JsonNode correctAnswer(QuestionSnapshot question) {
    JsonNode key = parse(question.answerKeyJson());
    if (key.isObject() && (key.has("acceptedAnswers") || key.has("value")))
      key = key.has("acceptedAnswers") ? key.get("acceptedAnswers") : key.get("value");
    return question.questionType() == QuestionType.SHORT_ANSWER ? key.get(0) : key;
  }

  static JsonNode parse(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid JSON", e);
    }
  }

  static ObjectNode obj(Object... values) {
    ObjectNode node = JSON.createObjectNode();
    for (int i = 0; i < values.length; i += 2)
      node.set((String) values[i], JSON.valueToTree(values[i + 1]));
    return node;
  }
}
