package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

/** Tiles that toggle on and off; the pick is sent with "Khóa đáp án" (or Enter). */
public final class MultipleChoiceRenderer extends BaseQuestionRenderer {
  private final Set<String> selected = new LinkedHashSet<>();
  private final List<String> order = new ArrayList<>();
  private Button submit;

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    selected.clear();
    order.clear();
    submit = new Button("Khóa đáp án · Enter");
    submit.getStyleClass().add("lock-answer");
    submit.setDisable(true);
    var tiles = new ArrayList<Button>();
    int i = 0;
    for (var o : q.options()) {
      String key = Integer.toString(i + 1);
      order.add(o.id());
      tiles.add(tile(o.id(), key, o.text(), i++, () -> toggle(o.id(), key)));
    }
    submit.setOnAction(
        e -> {
          if (selected.isEmpty()) return;
          answerControls.forEach(
              (id, c) -> {
                if (selected.contains(id)) c.getStyleClass().add("chosen");
              });
          if (!box.getStyleClass().contains("locked")) box.getStyleClass().add("locked");
          setControlsEnabled(false);
          answer.accept(AnswerIntentFactory.multiple(selected));
        });
    controls.add(submit);
    var actions = new HBox(submit);
    actions.setAlignment(Pos.CENTER_RIGHT);
    box.getChildren().addAll(tileGrid(tiles), actions);
  }

  /** Keyboard: the number keys toggle the options in order. */
  public void toggleOption(char key) {
    int index = Character.isDigit(key) ? key - '1' : Character.toUpperCase(key) - 'A';
    if (index < 0 || index >= order.size()) return;
    var tile = answerControls.get(order.get(index));
    if (tile instanceof Button b && !b.isDisabled()) b.fire();
  }

  /** Enter: sends the current pick. */
  public void submit() {
    if (submit != null && !submit.isDisabled()) submit.fire();
  }

  private void toggle(String id, String key) {
    var tile = answerControls.get(id);
    var badge = keyLabels.get(id);
    if (selected.remove(id)) {
      tile.getStyleClass().remove("picked");
      badge.setText(key);
    } else {
      selected.add(id);
      tile.getStyleClass().add("picked");
      badge.setText("✓");
    }
    submit.setDisable(selected.isEmpty());
  }

  @Override
  public void setControlsEnabled(boolean enabled) {
    super.setControlsEnabled(enabled);
    if (enabled && submit != null) submit.setDisable(selected.isEmpty());
  }
}
