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
    var tiles = new javafx.scene.control.Button[2];
    tiles[0] =
        tile(
            "true",
            "1",
            "Đúng",
            1,
            () -> {
              lock(tiles[0]);
              setControlsEnabled(false);
              answer.accept(AnswerIntentFactory.trueFalse(true));
            });
    tiles[1] =
        tile(
            "false",
            "2",
            "Sai",
            0,
            () -> {
              lock(tiles[1]);
              setControlsEnabled(false);
              answer.accept(AnswerIntentFactory.trueFalse(false));
            });
    var yes = tiles[0];
    var no = tiles[1];
    box.getChildren().add(tileGrid(List.of(yes, no)));
  }
}
