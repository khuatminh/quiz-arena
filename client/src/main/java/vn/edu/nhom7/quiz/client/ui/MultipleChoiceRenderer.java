package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.control.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class MultipleChoiceRenderer extends BaseQuestionRenderer {
  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    var selected = new LinkedHashSet<String>();
    var submit = new Button("Gửi lựa chọn");
    submit.setDisable(true);
    for (var o : q.options()) {
      var c = new CheckBox(o.id() + ".  " + o.text());
      c.setWrapText(true);
      c.getStyleClass().add("answer-card");
      c.setMaxWidth(Double.MAX_VALUE);
      c.setMinHeight(60);
      c.setOnAction(
          e -> {
            if (c.isSelected()) selected.add(o.id());
            else selected.remove(o.id());
            submit.setDisable(selected.isEmpty());
          });
      answerControls.put(o.id(), c);
      controls.add(c);
      box.getChildren().add(c);
    }
    submit.setOnAction(
        e -> {
          if (!selected.isEmpty()) {
            setControlsEnabled(false);
            answer.accept(AnswerIntentFactory.multiple(selected));
          }
        });
    controls.add(submit);
    box.getChildren().add(submit);
  }

  @Override
  public void setControlsEnabled(boolean enabled) {
    super.setControlsEnabled(enabled);
    if (enabled && controls.getLast() instanceof Button b)
      b.setDisable(
          box.getChildren().stream()
              .filter(CheckBox.class::isInstance)
              .map(CheckBox.class::cast)
              .noneMatch(CheckBox::isSelected));
  }
}
