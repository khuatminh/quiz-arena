package vn.edu.nhom7.quiz.server.quiz;

import com.fasterxml.jackson.databind.*;
import java.util.HashSet;
import vn.edu.nhom7.quiz.common.protocol.CommunityPayloads.DraftQuestion;
import vn.edu.nhom7.quiz.common.protocol.QuestionType;
import vn.edu.nhom7.quiz.server.auth.ServiceException;

/** Draft limits allow incomplete answers; publication performs the full gameplay validation. */
final class DraftValidator {
  private static final ObjectMapper JSON = new ObjectMapper();

  private DraftValidator() {}

  static int index(JsonNode data, String field) {
    var n = data.get(field);
    if (n == null
        || !n.isIntegralNumber()
        || !n.canConvertToInt()
        || n.intValue() < 0
        || n.intValue() > 49) throw invalid("Invalid " + field);
    return n.intValue();
  }

  static String text(JsonNode n, int max, boolean required) {
    if (n == null || !n.isTextual()) throw invalid("Expected text");
    String s = n.asText();
    bound(s, max);
    if (required && s.isBlank()) throw invalid("Text is required");
    return s;
  }

  static void bound(String s, int max) {
    if (s != null && s.codePointCount(0, s.length()) > max)
      throw invalid("Text exceeds " + max + " characters");
  }

  static ServiceException invalid(String message) {
    return new ServiceException("VALIDATION_ERROR", message);
  }

  static void question(DraftQuestion q) {
    if (q.questionType() == null) throw invalid("Question type is required");
    try {
      QuestionType.valueOf(q.questionType());
    } catch (IllegalArgumentException e) {
      throw invalid("Invalid question type");
    }
    bound(q.content(), 500);
    bound(q.explanation(), 500);
    if (q.options() != null) {
      if (q.options().size() > 6) throw invalid("Too many options");
      var ids = new HashSet<String>();
      for (var o : q.options()) {
        if (o == null) throw invalid("Invalid option");
        bound(o.id(), 64);
        bound(o.text(), 120);
        if (o.id() != null && !ids.add(o.id())) throw invalid("Duplicate option ID");
      }
    }
    if (q.answerKey() != null && q.answerKey().toString().length() > 8192)
      throw invalid("Answer key too large");
    if ("SHORT_ANSWER".equals(q.questionType()) && q.answerKey() != null) {
      JsonNode accepted =
          q.answerKey().has("acceptedAnswers")
              ? q.answerKey().get("acceptedAnswers")
              : q.answerKey();
      if (accepted.isArray() && accepted.size() > 20)
        throw invalid("At most 20 accepted answers are allowed");
    }
    try {
      // Reserve room for the author-result envelope and index around the stored question.
      if (JSON.writeValueAsBytes(q).length > 60 * 1024)
        throw invalid("Nội dung câu hỏi quá lớn để tải lại. Hãy rút gọn nội dung hoặc đáp án.");
    } catch (java.io.IOException e) {
      throw invalid("Invalid question data");
    }
  }
}
