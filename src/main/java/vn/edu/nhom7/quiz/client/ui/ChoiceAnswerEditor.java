package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import java.util.function.BiConsumer;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Editable choices preserve arbitrary option IDs and keep single/multiple selection in sync. */
final class ChoiceAnswerEditor {
  private static final ObjectMapper JSON = new ObjectMapper();

  private record OptionRow(TextField id, TextField text, CheckBox correct) {}

  private final VBox target;
  private final String kind;
  private final BiConsumer<ArrayNode, List<String>> changed;
  private final List<OptionRow> choices = new ArrayList<>();
  private final List<String> selected;
  private ArrayNode draft;

  private ChoiceAnswerEditor(
      VBox target,
      String kind,
      ArrayNode options,
      List<String> selectedAnswers,
      BiConsumer<ArrayNode, List<String>> changed) {
    this.target = target;
    this.kind = kind;
    this.draft = options;
    this.selected = new ArrayList<>(selectedAnswers);
    this.changed = changed;
  }

  static void render(
      VBox target,
      String kind,
      ArrayNode options,
      List<String> selectedAnswers,
      BiConsumer<ArrayNode, List<String>> changed) {
    new ChoiceAnswerEditor(target, kind, options, selectedAnswers, changed).build();
  }

  private void build() {
    target
        .getChildren()
        .setAll(
            Ui.label("Lựa chọn và đáp án đúng"),
            Ui.muted(
                kind.equals("SINGLE_CHOICE")
                    ? "2–6 lựa chọn · Nội dung ≤120 ký tự · Mã ≤64 ký tự · Chọn một đáp án đúng"
                    : "2–6 lựa chọn · Nội dung ≤120 ký tự · Mã ≤64 ký tự · Chọn tất cả đáp án"
                        + " đúng"));
    var rows = new VBox(8);
    ArrayNode options = draft.deepCopy();
    if (options.isEmpty()) {
      for (String id : List.of("A", "B", "C", "D"))
        options.add(JSON.createObjectNode().put("id", id).put("text", ""));
    }
    for (JsonNode option : options) rows.getChildren().add(optionRow(option, options.size()));
    var add = Ui.secondary("+ Thêm lựa chọn", this::addOption);
    add.setDisable(options.size() >= 6);
    target.getChildren().addAll(rows, add);
  }

  private HBox optionRow(JsonNode option, int optionCount) {
    String key = option.path("id").asText();
    var id = new TextField(key);
    id.setPrefWidth(65);
    id.setMinWidth(65);
    id.setMaxWidth(65);
    id.setAccessibleText("Mã lựa chọn");
    var text = new TextField(option.path("text").asText());
    text.setPromptText("Nội dung lựa chọn · tối đa 120 ký tự");
    HBox.setHgrow(text, Priority.ALWAYS);
    var correct = new CheckBox("Đúng");
    correct.setSelected(selected.contains(key));
    correct.setMinWidth(70);
    var row = new OptionRow(id, text, correct);
    choices.add(row);
    id.textProperty().addListener((observable, before, value) -> sync());
    text.textProperty().addListener((observable, before, value) -> sync());
    correct.setOnAction(
        event -> {
          if (kind.equals("SINGLE_CHOICE") && correct.isSelected()) {
            for (var other : choices) if (other != row) other.correct().setSelected(false);
          }
          sync();
        });
    var remove =
        Ui.secondary(
            "×",
            () -> {
              choices.remove(row);
              sync();
              rebuild();
            });
    remove.setAccessibleText("Xóa lựa chọn " + key);
    remove.setDisable(optionCount <= 2);
    return Ui.actions(id, text, correct, remove);
  }

  private void sync() {
    ArrayNode options = JSON.createArrayNode();
    selected.clear();
    for (var row : choices) {
      options.add(
          JSON.createObjectNode().put("id", row.id().getText()).put("text", row.text().getText()));
      if (row.correct().isSelected()) selected.add(row.id().getText());
    }
    draft = options;
    changed.accept(options, List.copyOf(selected));
  }

  private void addOption() {
    sync();
    Set<String> used = new HashSet<>();
    choices.forEach(row -> used.add(row.id().getText()));
    String next =
        java.util.stream.IntStream.range(0, 26)
            .mapToObj(index -> Character.toString('A' + index))
            .filter(id -> !used.contains(id))
            .findFirst()
            .orElse("G");
    draft.add(JSON.createObjectNode().put("id", next).put("text", ""));
    changed.accept(draft, List.copyOf(selected));
    rebuild();
  }

  private void rebuild() {
    render(target, kind, draft, selected, changed);
  }
}
