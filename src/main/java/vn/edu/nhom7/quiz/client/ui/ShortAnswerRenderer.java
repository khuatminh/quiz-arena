package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import javafx.scene.control.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class ShortAnswerRenderer extends BaseQuestionRenderer {
  private javafx.scene.control.TextField answerField;
  private javafx.scene.control.Button submitButton;

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    var field = new TextField();
    answerField = field;
    field.setPromptText("Câu trả lời của bạn (tối đa 120 ký tự)");
    field.setTextFormatter(
        new TextFormatter<String>(
            change ->
                change.getControlNewText().codePointCount(0, change.getControlNewText().length())
                        <= 120
                    ? change
                    : null));
    var submit = new Button("Gửi câu trả lời");
    submitButton = submit;
    submit
        .disableProperty()
        .bind(
            javafx.beans.binding.Bindings.createBooleanBinding(
                () -> field.getText().isBlank(), field.textProperty()));
    Runnable send =
        () -> {
          try {
            var value = AnswerIntentFactory.shortAnswer(field.getText());
            field.setDisable(true);
            submit.disableProperty().unbind();
            submit.setDisable(true);
            answer.accept(value);
          } catch (IllegalArgumentException e) {
            field.setPromptText(e.getMessage());
          }
        };
    submit.setOnAction(e -> send.run());
    field.setOnAction(e -> send.run());
    controls.add(field);
    controls.add(submit);
    box.getChildren().addAll(field, submit);
  }

  @Override
  public void setControlsEnabled(boolean enabled) {
    for (var c : controls) {
      if (c.disableProperty().isBound()) c.disableProperty().unbind();
      c.setDisable(!enabled);
    }
    if (enabled && answerField != null && submitButton != null)
      submitButton
          .disableProperty()
          .bind(
              javafx.beans.binding.Bindings.createBooleanBinding(
                  () -> answerField.getText().isBlank(), answerField.textProperty()));
  }
}
