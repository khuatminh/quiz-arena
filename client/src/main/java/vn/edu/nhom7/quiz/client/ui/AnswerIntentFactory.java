package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

public final class AnswerIntentFactory {
  private AnswerIntentFactory() {}

  public static JsonNode single(String id) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Choose an answer");
    return TextNode.valueOf(id);
  }

  public static JsonNode multiple(Collection<String> ids) {
    if (ids == null || ids.isEmpty() || ids.stream().anyMatch(x -> x == null || x.isBlank()))
      throw new IllegalArgumentException("Choose at least one answer");
    var a = JsonNodeFactory.instance.arrayNode();
    new LinkedHashSet<>(ids).forEach(a::add);
    return a;
  }

  public static JsonNode trueFalse(boolean v) {
    return BooleanNode.valueOf(v);
  }

  public static JsonNode shortAnswer(String text) {
    if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 120)
      throw new IllegalArgumentException("Answer requires 1–120 characters");
    return TextNode.valueOf(text);
  }
}
