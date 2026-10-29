package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.*;
import java.util.*;

/** Strict record schema checks, including field ownership for nullable values. */
final class PayloadValidator {
  private PayloadValidator() {}

  private static final Set<String> NULLABLE =
      Set.of(
          "categoryId",
          "coverAssetId",
          "questionAssetId",
          "explanation",
          "explanationAssetId",
          "matchId",
          "winnerUserId",
          "savedAtMs",
          "expiresAtMs",
          "newMatchId",
          "answer",
          "correct",
          "answerTimeMs",
          "acceptedAnswer",
          "latestResult",
          "standings",
          "myRank",
          "question",
          "roundIndex",
          "totalRounds");

  static void validate(JsonNode n, Type t, String field) {
    if (n == null || n.isNull()) {
      if (NULLABLE.contains(field)) return;
      fail("INVALID_MESSAGE", "Required field missing");
    }
    if (t instanceof ParameterizedType pt) {
      if (pt.getRawType() == List.class) {
        if (!n.isArray()) fail("INVALID_MESSAGE", "Expected array");
        for (JsonNode v : n) validate(v, pt.getActualTypeArguments()[0], "element");
        return;
      }
      if (pt.getRawType() == Map.class) {
        if (!n.isObject()) fail("INVALID_MESSAGE", "Expected object");
        for (JsonNode v : n) validate(v, pt.getActualTypeArguments()[1], "element");
        return;
      }
    }
    if (!(t instanceof Class<?> c)) return;
    if (c == JsonNode.class) return;
    if (c == String.class) {
      if (!n.isTextual()) fail("INVALID_MESSAGE", "Expected string");
      return;
    }
    if (c == UUID.class) {
      uuid(n);
      return;
    }
    if (c == boolean.class || c == Boolean.class) {
      if (!n.isBoolean()) fail("INVALID_MESSAGE", "Expected boolean");
      return;
    }
    if (c == int.class || c == Integer.class || c == long.class || c == Long.class) {
      if (!n.isIntegralNumber()
          || !n.canConvertToLong()
          || ((c == int.class || c == Integer.class) && !n.canConvertToInt()))
        fail("INVALID_MESSAGE", "Expected integer");
      return;
    }
    if (c.isRecord()) {
      if (!n.isObject()) fail("INVALID_MESSAGE", "Expected object");
      var names = new HashSet<String>();
      for (var rc : c.getRecordComponents()) {
        names.add(rc.getName());
        JsonNode value = n.get(rc.getName());
        boolean nullable = isNullable(c, rc.getName());
        if (value == null || value.isNull()) {
          if (!nullable) fail("INVALID_MESSAGE", "Required field missing");
        } else validate(value, rc.getGenericType(), rc.getName());
      }
      n.fieldNames()
          .forEachRemaining(
              k -> {
                if (!names.contains(k)) fail("INVALID_MESSAGE", "Unknown payload field");
              });
    }
  }

  private static boolean isNullable(Class<?> owner, String field) {
    if (owner == Payloads.MatchSnapshot.class)
      return Set.of(
              "question",
              "roundIndex",
              "totalRounds",
              "acceptedAnswer",
              "latestResult",
              "standings")
          .contains(field);
    return switch (field) {
      case "correctAnswer" -> owner == Payloads.QuestionReview.class;
      case "authorName" -> owner == Payloads.QuizSummary.class;
      case "mediaId" -> owner == MediaPayloads.UploadAck.class;
      case "categoryId" -> owner == Payloads.QuizListRequest.class;
      case "matchId" -> owner == Payloads.ChallengeClosed.class;
      case "answer", "correct", "answerTimeMs" -> owner == Payloads.AnswerOutcome.class;
      case "myRank" -> owner == Payloads.Ranking.class;
      case "expiresAtMs", "newMatchId" -> owner == Payloads.RematchStatus.class;
      case "savedAtMs" -> owner == Payloads.MatchSaveStatus.class;
      case "coverAssetId", "questionAssetId", "explanation", "explanationAssetId", "winnerUserId" ->
          true;
      default -> false;
    };
  }

  static void uuid(JsonNode n) {
    if (!n.isTextual()
        || !n.textValue()
            .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      fail("INVALID_MESSAGE", "Invalid UUID");
  }

  private static void fail(String code, String message) {
    throw new ProtocolException(code, message);
  }
}
