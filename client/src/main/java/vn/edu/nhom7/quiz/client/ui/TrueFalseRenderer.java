package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class TrueFalseRenderer extends BaseQuestionRenderer {
  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    answerControls.put(
        "true",
        option(
            "✓  Đúng",
            () -> {
              setControlsEnabled(false);
              answer.accept(AnswerIntentFactory.trueFalse(true));
            }));
    answerControls.put(
        "false",
        option(
            "✗  Sai",
            () -> {
              setControlsEnabled(false);
              answer.accept(AnswerIntentFactory.trueFalse(false));
            }));
  }
}
