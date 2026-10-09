package vn.edu.nhom7.quiz.server.quiz;

import com.fasterxml.jackson.databind.*;
import java.text.Normalizer;
import java.util.*;
import vn.edu.nhom7.quiz.server.domain.*;

public final class QuestionValidator {
  private static final ObjectMapper JSON = new ObjectMapper();

  private QuestionValidator() {}

  public static void validate(QuestionSnapshot q) {
    text(q.content(), 500);
    if (q.explanation() != null) text(q.explanation(), 500);
    try {
      JsonNode key = JSON.readTree(q.answerKeyJson());
      switch (q.questionType()) {
        case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
          if (q.options().size() < 2 || q.options().size() > 6) throw invalid();
          var ids = new HashSet<String>();
          for (var option : q.options()) {
            text(option.text(), 120);
            if (option.id() == null || option.id().isBlank() || !ids.add(option.id()))
              throw invalid();
          }
          if (key.has("value")) key = key.get("value");
          if (q.questionType().name().equals("SINGLE_CHOICE")) {
            if (!key.isTextual() || !ids.contains(key.textValue())) throw invalid();
          } else {
            if (!key.isArray() || key.isEmpty()) throw invalid();
            var chosen = new HashSet<String>();
            for (var k : key)
              if (!k.isTextual() || !ids.contains(k.textValue()) || !chosen.add(k.textValue()))
                throw invalid();
          }
        }
        case TRUE_FALSE -> {
          if (key.has("value")) key = key.get("value");
          if (!q.options().isEmpty() || !key.isBoolean()) throw invalid();
        }
        case SHORT_ANSWER -> {
          if (key.has("acceptedAnswers")) key = key.get("acceptedAnswers");
          if (!q.options().isEmpty() || !key.isArray() || key.isEmpty()) throw invalid();
          for (var k : key) {
            if (!k.isTextual()) throw invalid();
            text(k.textValue(), 120);
            if (Normalizer.normalize(k.textValue(), Normalizer.Form.NFC).strip().isEmpty())
              throw invalid();
          }
        }
      }
    } catch (java.io.IOException e) {
      throw invalid();
    }
  }

  private static void text(String s, int max) {
    if (s == null || s.isBlank() || s.codePointCount(0, s.length()) > max) throw invalid();
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid question bank entry");
  }
}
