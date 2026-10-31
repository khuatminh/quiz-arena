package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

/** A permutation is submitted once; arrows provide the same operation as drag and drop. */
public final class ReorderRenderer extends BaseQuestionRenderer {
  private final List<Payloads.Option> items = new ArrayList<>();
  private final VBox rows = new VBox(6);
  private boolean enabled;
  private Button submit;

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    items.clear();
    items.addAll(q.options());
    Collections.shuffle(items);
    enabled = true;
    submit =
        Ui.button(
            "Khóa thứ tự",
            () -> {
              if (!enabled) return;
              var value =
                  new ObjectMapper().valueToTree(items.stream().map(Payloads.Option::id).toList());
              setControlsEnabled(false);
              answer.accept(value);
            });
    submit.setId("reorder-submit");
    box.getChildren()
        .addAll(Ui.muted("Kéo thả hoặc dùng ↑ ↓ để sắp xếp từ trên xuống dưới"), rows, submit);
    redraw();
  }

  private void move(int from, int to) {
    if (!enabled || to < 0 || to >= items.size() || from < 0 || from == to) return;
    items.add(to, items.remove(from));
    redraw();
  }

  private void redraw() {
    rows.getChildren().clear();
    controls.clear();
    for (int i = 0; i < items.size(); i++) {
      int index = i;
      var item = items.get(i);
      var label = Ui.label((i + 1) + ". " + item.text());
      label.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(label, Priority.ALWAYS);
      var up = Ui.secondary("↑", () -> move(index, index - 1));
      var down = Ui.secondary("↓", () -> move(index, index + 1));
      up.setAccessibleText("Đưa lên: " + item.text());
      down.setAccessibleText("Đưa xuống: " + item.text());
      var row = new HBox(8, label, up, down);
      row.setId("reorder-row-" + i);
      row.getStyleClass().add("answer-card");
      row.setOnDragDetected(
          e -> {
            if (!enabled) return;
            var drag = row.startDragAndDrop(TransferMode.MOVE);
            var data = new ClipboardContent();
            data.putString(item.id());
            drag.setContent(data);
            e.consume();
          });
      row.setOnDragOver(
          e -> {
            if (enabled
                && items.stream().anyMatch(v -> v.id().equals(e.getDragboard().getString())))
              e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
          });
      row.setOnDragDropped(
          e -> {
            int from = -1;
            for (int n = 0; n < items.size(); n++)
              if (items.get(n).id().equals(e.getDragboard().getString())) from = n;
            boolean accepted = enabled && from >= 0;
            if (accepted) move(from, index);
            e.setDropCompleted(accepted);
            e.consume();
          });
      controls.addAll(List.of(up, down));
      rows.getChildren().add(row);
    }
    controls.add(submit);
    setControlsEnabled(enabled);
  }

  public void submit() {
    if (submit != null && !submit.isDisabled()) submit.fire();
  }

  @Override
  public void setControlsEnabled(boolean value) {
    enabled = value;
    super.setControlsEnabled(value);
    for (int i = 0; i < rows.getChildren().size(); i++) {
      var row = (HBox) rows.getChildren().get(i);
      row.getChildren().get(1).setDisable(!value || i == 0);
      row.getChildren().get(2).setDisable(!value || i == items.size() - 1);
    }
  }

  @Override
  public void showResult(Payloads.QuestionResult result, long self) {
    setControlsEnabled(false);
    var byId = new HashMap<String, Payloads.Option>();
    items.forEach(item -> byId.put(item.id(), item));
    items.clear();
    for (var id : result.correctAnswer())
      if (byId.containsKey(id.asText())) items.add(byId.get(id.asText()));
    redraw();
    box.getChildren().addFirst(Ui.label("Thứ tự đúng"));
  }
}
