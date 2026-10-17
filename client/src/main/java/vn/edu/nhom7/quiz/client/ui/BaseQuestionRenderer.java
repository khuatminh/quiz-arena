package vn.edu.nhom7.quiz.client.ui;

import java.util.*;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

abstract class BaseQuestionRenderer implements QuestionRenderer {
  protected final VBox box = new VBox(12);
  protected final List<Control> controls = new ArrayList<>();
  protected final Map<String, Control> answerControls = new LinkedHashMap<>();

  /** Answer tiles carry their own slot for result chips; other controls get chips below them. */
  protected final Map<String, HBox> markSlots = new HashMap<>();

  /** Options a lifeline removed: they stay visible but greyed out and never re-enable. */
  protected final Set<String> eliminated = new HashSet<>();

  private com.fasterxml.jackson.databind.JsonNode players;

  BaseQuestionRenderer() {
    box.getStyleClass().add("answers");
  }

  public void setPlayers(com.fasterxml.jackson.databind.JsonNode p) {
    players = p;
  }

  public Node view() {
    return box;
  }

  protected Button option(String text, Runnable action) {
    var b = new Button(text);
    b.setWrapText(true);
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMinHeight(56);
    b.getStyleClass().add("answer-card");
    b.setOnAction(e -> action.run());
    controls.add(b);
    box.getChildren().add(b);
    return b;
  }

  /** A large answer tile: coloured key cap, answer text and a slot for result chips. */
  protected Button tile(String id, String key, String text, int accent, Runnable action) {
    var cap = new Label(key);
    cap.getStyleClass().addAll("keycap", "keycap-" + (accent % 4));
    cap.setMinSize(48, 48);
    cap.setAlignment(Pos.CENTER);
    var label = Ui.label(text);
    label.getStyleClass().add("tile-text");
    label.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(label, Priority.ALWAYS);
    var slot = new HBox(6);
    slot.setAlignment(Pos.CENTER_RIGHT);
    var content = new HBox(16, cap, label, slot);
    content.setAlignment(Pos.CENTER_LEFT);
    // A plain button measures its graphic without a width, so a wrapping answer was measured one
    // word per line; measure the content at the width the tile really gets instead.
    var b =
        new Button() {
          @Override
          public javafx.geometry.Orientation getContentBias() {
            return javafx.geometry.Orientation.HORIZONTAL;
          }

          @Override
          protected double computePrefHeight(double width) {
            if (width <= 0) return super.computePrefHeight(width);
            return snappedTopInset()
                + snappedBottomInset()
                + content.prefHeight(width - snappedLeftInset() - snappedRightInset());
          }
        };
    b.setGraphic(content);
    b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    b.setAccessibleText(key + ". " + text);
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMaxHeight(Double.MAX_VALUE);
    b.setMinHeight(112);
    b.setAlignment(Pos.CENTER_LEFT);
    b.getStyleClass().addAll("answer-card", "answer-tile");
    b.setOnAction(
        e -> {
          b.getStyleClass().add("chosen");
          action.run();
        });
    content.prefWidthProperty().bind(b.widthProperty().subtract(48));
    controls.add(b);
    answerControls.put(id, b);
    markSlots.put(id, slot);
    return b;
  }

  /** Lays tiles out two per row; extra options simply add rows. */
  protected GridPane tileGrid(List<Button> tiles) {
    var grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(16);
    for (int c = 0; c < 2; c++) {
      var column = new ColumnConstraints();
      column.setPercentWidth(50);
      grid.getColumnConstraints().add(column);
    }
    for (int i = 0; i < tiles.size(); i++) {
      grid.add(tiles.get(i), i % 2, i / 2);
      GridPane.setFillHeight(tiles.get(i), true);
    }
    return grid;
  }

  public void setControlsEnabled(boolean enabled) {
    controls.forEach(c -> c.setDisable(!enabled));
    eliminated.forEach(id -> answerControls.get(id).setDisable(true));
  }

  /**
   * Greys out options removed by a lifeline. Returns the controls newly removed, so the caller can
   * animate only those.
   */
  public List<Control> eliminate(Collection<String> ids) {
    List<Control> fresh = new ArrayList<>();
    for (String id : ids) {
      Control control = answerControls.get(id);
      if (control == null || !eliminated.add(id)) continue;
      control.setDisable(true);
      control.getStyleClass().add("eliminated");
      control.setAccessibleText("Đã loại: " + id);
      var slot = markSlots.get(id);
      if (slot != null) slot.getChildren().add(chip("Đã loại", "chip-eliminated"));
      fresh.add(control);
    }
    return fresh;
  }

  private void addMark(String id, Control control, Node chip) {
    var slot = markSlots.get(id);
    if (slot != null) {
      slot.getChildren().add(chip);
      return;
    }
    int index = box.getChildren().indexOf(control);
    box.getChildren().add(index + 1, chip);
  }

  private static Label chip(String text, String kind) {
    var l = new Label(text);
    l.getStyleClass().addAll("answer-chip", kind);
    return l;
  }

  public void showResult(Payloads.QuestionResult r, long self) {
    setControlsEnabled(false);
    var correct = new java.util.HashSet<String>();
    if (r.correctAnswer() != null) {
      if (r.correctAnswer().isArray()) r.correctAnswer().forEach(n -> correct.add(n.asText()));
      else correct.add(r.correctAnswer().asText());
    }
    for (var entry : answerControls.entrySet())
      if (correct.contains(entry.getKey())) {
        entry.getValue().getStyleClass().add("correct");
        entry.getValue().setAccessibleText("Đáp án đúng: " + entry.getKey());
        if (!markSlots.containsKey(entry.getKey()))
          addMark(entry.getKey(), entry.getValue(), chip("✓ Đáp án đúng", "chip-correct"));
      }
    for (var outcome : r.outcomes()) {
      if (outcome.answer() == null) continue;
      var selected = new java.util.HashSet<String>();
      if (outcome.answer().isArray()) outcome.answer().forEach(n -> selected.add(n.asText()));
      else selected.add(outcome.answer().asText());
      for (String id : selected) {
        Control control = answerControls.get(id);
        if (control == null) continue;
        if (!correct.contains(id)) control.getStyleClass().add("incorrect");
        String name = outcome.userId() == self ? "Bạn" : "Đối thủ";
        String avatar = "avatar-01";
        if (players != null)
          for (var player : players)
            if (player.path("userId").asLong() == outcome.userId()) {
              name =
                  player.path("displayName").asText() + (outcome.userId() == self ? " · Bạn" : "");
              avatar = player.path("avatarId").asText();
            }
        boolean right = Boolean.TRUE.equals(outcome.correct());
        var mark =
            chip(
                (right ? "✓ " : "✗ ")
                    + name
                    + (markSlots.containsKey(id)
                        ? ""
                        : " · chọn "
                            + (id.equals("true") ? "Đúng" : id.equals("false") ? "Sai" : id)),
                right ? "chip-correct" : "chip-wrong");
        mark.setGraphic(new vn.edu.nhom7.quiz.client.assets.AssetLoader().view(avatar, 22));
        addMark(id, control, mark);
      }
    }
    // Options nobody chose and that are wrong fade back so the answer stands out.
    for (var entry : answerControls.entrySet())
      if (!entry.getValue().getStyleClass().contains("correct")
          && !entry.getValue().getStyleClass().contains("incorrect"))
        entry.getValue().getStyleClass().add("dimmed");
  }

  protected void clear() {
    eliminated.clear();
    box.getChildren().clear();
    controls.clear();
    answerControls.clear();
    markSlots.clear();
  }
}
