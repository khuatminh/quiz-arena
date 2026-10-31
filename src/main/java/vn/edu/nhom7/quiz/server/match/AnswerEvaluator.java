package vn.edu.nhom7.quiz.server.match;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.InteractiveQuestions;
import vn.edu.nhom7.quiz.server.domain.QuestionSnapshot;

/** Validates wire types before evaluating; never coerces a submitted answer. */
public final class AnswerEvaluator {
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  public boolean evaluate(QuestionSnapshot question, JsonNode answer) {
    JsonNode key;
    try {
      key = JSON.readTree(question.answerKeyJson());
    } catch (Exception e) {
      throw new IllegalStateException("Invalid answer key", e);
    }
    if (key == null) throw new IllegalStateException("Missing answer key");
    if (key.isObject() && (key.has("acceptedAnswers") || key.has("value")))
      key = key.has("acceptedAnswers") ? key.get("acceptedAnswers") : key.get("value");
    if (key == null) throw new IllegalStateException("Missing answer key value");
    Set<String> options = new HashSet<>();
    question.options().forEach(o -> options.add(o.id()));
    return switch (question.questionType()) {
      case REORDER -> {
        Set<String> submitted = choiceSet(answer, options, false);
        require(submitted.size() == options.size());
        yield key.equals(answer);
      }
      case RANGE -> {
        java.math.BigDecimal value;
        try {
          value = InteractiveQuestions.number(answer);
        } catch (IllegalArgumentException e) {
          require(false);
          yield false;
        }
        var min = key.path("min").decimalValue();
        var max = key.path("max").decimalValue();
        require(
            value.compareTo(min) >= 0
                && value.compareTo(max) <= 0
                && value.subtract(min).remainder(key.path("step").decimalValue()).signum() == 0);
        yield value.compareTo(key.path("acceptedMin").decimalValue()) >= 0
            && value.compareTo(key.path("acceptedMax").decimalValue()) <= 0;
      }
      case PINPOINT -> {
        java.math.BigDecimal x, y;
        try {
          InteractiveQuestions.fields(answer, Set.of("x", "y"));
          x = InteractiveQuestions.number(answer.get("x"));
          y = InteractiveQuestions.number(answer.get("y"));
        } catch (IllegalArgumentException e) {
          require(false);
          yield false;
        }
        require(
            x.signum() >= 0
                && y.signum() >= 0
                && x.compareTo(java.math.BigDecimal.ONE) <= 0
                && y.compareTo(java.math.BigDecimal.ONE) <= 0);
        var left = key.path("x").decimalValue();
        var top = key.path("y").decimalValue();
        yield x.compareTo(left) >= 0
            && y.compareTo(top) >= 0
            && x.compareTo(left.add(key.path("width").decimalValue())) <= 0
            && y.compareTo(top.add(key.path("height").decimalValue())) <= 0;
      }
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
