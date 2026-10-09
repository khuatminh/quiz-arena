package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import vn.edu.nhom7.quiz.server.domain.QuestionSnapshot;

/** Validates wire types before evaluating; never coerces a submitted answer. */
public final class AnswerEvaluator {
  private static final ObjectMapper JSON = new ObjectMapper();

  public boolean evaluate(QuestionSnapshot question, JsonNode answer) {
    JsonNode key;
    try {
      key = JSON.readTree(question.answerKeyJson());
    } catch (Exception e) {
      throw new IllegalStateException("Invalid answer key", e);
    }
    if (key == null) throw new IllegalStateException("Missing answer key");
    if (key.isObject())
      key = key.has("acceptedAnswers") ? key.get("acceptedAnswers") : key.get("value");
    if (key == null) throw new IllegalStateException("Missing answer key value");
    Set<String> options = new HashSet<>();
    question.options().forEach(o -> options.add(o.id()));
    return switch (question.questionType()) {
      case SINGLE_CHOICE -> {
        require(answer != null && answer.isTextual() && options.contains(answer.textValue()));
        if (!key.isTextual() || !options.contains(key.textValue()))
          throw new IllegalStateException("Invalid single key");
        yield key.equals(answer);
      }
      case MULTIPLE_CHOICE -> {
        Set<String> submitted = choiceSet(answer, options, false);
        Set<String> expected = choiceSet(key, options, true);
        yield submitted.equals(expected);
      }
      case TRUE_FALSE -> {
        require(answer != null && answer.isBoolean());
        if (!key.isBoolean()) throw new IllegalStateException("Invalid boolean key");
        yield key.equals(answer);
      }
      case SHORT_ANSWER -> {
        require(answer != null && answer.isTextual());
        String raw = answer.textValue();
        int count = raw.codePointCount(0, raw.length());
        require(count >= 1 && count <= 120 && !ShortAnswerNormalizer.normalize(raw).isEmpty());
        if (!key.isArray() || key.isEmpty()) throw new IllegalStateException("Invalid aliases");
        boolean correct = false;
        for (JsonNode alias : key) {
          if (!alias.isTextual() || ShortAnswerNormalizer.normalize(alias.textValue()).isEmpty())
            throw new IllegalStateException("Invalid alias");
          correct |=
              ShortAnswerNormalizer.normalize(alias.textValue())
                  .equals(ShortAnswerNormalizer.normalize(raw));
        }
        yield correct;
      }
    };
  }

  private static Set<String> choiceSet(JsonNode node, Set<String> options, boolean key) {
    Set<String> result = new HashSet<>();
    boolean valid = node != null && node.isArray() && !node.isEmpty();
    if (valid)
      for (JsonNode entry : node) {
        if (!entry.isTextual()
            || !options.contains(entry.textValue())
            || !result.add(entry.textValue())) {
          valid = false;
          break;
        }
      }
    if (!valid) {
      if (key) throw new IllegalStateException("Invalid choice key");
      require(false);
    }
    return result;
  }

  private static void require(boolean valid) {
    if (!valid)
      throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
          "INVALID_ANSWER", "Invalid typed answer");
  }
}
