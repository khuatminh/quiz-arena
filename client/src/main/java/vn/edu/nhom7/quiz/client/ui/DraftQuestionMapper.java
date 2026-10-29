package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.List;

/** Converts editor values to draft wire data without sending requests or touching controls. */
final class DraftQuestionMapper {
  private static final ObjectMapper JSON = new ObjectMapper();

  private DraftQuestionMapper() {}

  static ObjectNode serialize(
      String type,
      String content,
      String explanation,
      ArrayNode options,
      List<String> choiceAnswer,
      String answer,
      String questionAsset,
      String explanationAsset) {
    ObjectNode q =
        JSON.createObjectNode()
            .put("questionType", type)
            .put("content", content)
            .put("explanation", explanation);
    q.set("options", type.endsWith("CHOICE") ? options.deepCopy() : JSON.createArrayNode());
    String text = answer.trim();
    if (type.endsWith("CHOICE") && choiceAnswer != null) {
      if (type.equals("MULTIPLE_CHOICE")) q.set("answerKey", JSON.valueToTree(choiceAnswer));
      else q.put("answerKey", choiceAnswer.isEmpty() ? "" : choiceAnswer.getFirst());
    } else if (type.equals("TRUE_FALSE")) {
      if (!text.isEmpty() && !text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
        throw new IllegalArgumentException("Đáp án đúng/sai phải là true hoặc false");
      q.set(
          "answerKey",
          text.isEmpty() ? NullNode.instance : BooleanNode.valueOf(Boolean.parseBoolean(text)));
    } else if (type.equals("MULTIPLE_CHOICE") || type.equals("SHORT_ANSWER")) {
      ArrayNode a = JSON.createArrayNode();
      for (String v : text.split(type.equals("SHORT_ANSWER") ? "\\R" : ","))
        if (!v.isBlank()) a.add(v.trim());
      q.set("answerKey", a);
    } else q.put("answerKey", text);
    q.put("questionAssetId", questionAsset);
    q.put("explanationAssetId", explanationAsset);
    return q;
  }
}
