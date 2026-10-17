package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.function.Consumer;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class TrueFalseRenderer extends BaseQuestionRenderer {
  /** Keyboard: 1 = Đúng, 2 = Sai. */
  public void activate(int index) {
    if (index >= 0
        && index < controls.size()
        && controls.get(index) instanceof javafx.scene.control.Button b
        && !b.isDisabled()) b.fire();
  }

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    var yes =
        tile(
            "true",
            "1",
            "Đúng",
            0,
            () -> {
              setControlsEnabled(false);
              answer.accept(AnswerIntentFactory.trueFalse(true));
            });
    var no =
        tile(
            "false",
            "2",
            "Sai",
            3,
            () -> {
              setControlsEnabled(false);
              answer.accept(AnswerIntentFactory.trueFalse(false));
            });
    box.getChildren().add(tileGrid(List.of(yes, no)));
  }
}
