package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class SingleChoiceRenderer extends BaseQuestionRenderer {
  public void activateOption(char key) {
    int i = Character.isDigit(key) ? key - '1' : Character.toUpperCase(key) - 'A';
    if (i >= 0 && i < controls.size() && controls.get(i) instanceof Button b && !b.isDisabled())
      b.fire();
  }

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    var tiles = new ArrayList<Button>();
    int i = 0;
    for (var o : q.options()) {
      var tile = new Button[1];
      tile[0] =
          tile(
              o.id(),
              Integer.toString(i + 1),
              o.text(),
              i++,
              () -> {
                lock(tile[0]);
                setControlsEnabled(false);
                answer.accept(AnswerIntentFactory.single(o.id()));
              });
      tiles.add(tile[0]);
    }
    box.getChildren().add(tileGrid(tiles));
  }
}
