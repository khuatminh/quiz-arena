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
  protected final Map<String, Pane> markSlots = new HashMap<>();

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

  /** Shape glyphs for the four answer colours, so an answer never depends on hue alone. */
  static final String[] SHAPES = {
    "M12 3L22 20H2Z",
    "M12 2.5A9.5 9.5 0 1 1 11.99 2.5Z",
    "M5 3H19Q21 3 21 5V19Q21 21 19 21H5Q3 21 3 19V5Q3 3 5 3Z",
    "M12 2L22 12L12 22L2 12Z"
  };

  /** Shape names read out by screen readers, matching {@link #SHAPES}. */
  static final String[] SHAPE_NAMES = {"tam giác", "tròn", "vuông", "thoi"};

  /** Key badges per answer, so a multiple-choice pick can show its tick there. */
  protected final Map<String, Label> keyLabels = new HashMap<>();

  /**
   * A large coloured answer tile: shape glyph and key badge on top, the answer text below with a
   * slot for result chips. {@code accent} picks the colour and shape.
   */
  protected Button tile(String id, String key, String text, int accent, Runnable action) {
    var shape = new javafx.scene.shape.SVGPath();
    shape.setContent(SHAPES[accent % 4]);
    shape.getStyleClass().add("tile-shape");
    var glyph = Effects.glyph(30, shape);
    var cap = new Label(key);
    cap.getStyleClass().add("tile-key");
    cap.setMinWidth(Region.USE_PREF_SIZE);
    var top = new HBox(glyph, Ui.spacer(), cap);
    top.setAlignment(Pos.CENTER_LEFT);
    var label = Ui.label(text);
    label.getStyleClass().add("tile-text");
    label.setMaxWidth(Double.MAX_VALUE);
    var slot = new FlowPane(6, 6);
    var gap = new Region();
    VBox.setVgrow(gap, Priority.ALWAYS);
    var content = new VBox(10, top, gap, label, slot);
    content.setFillWidth(true);
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
    b.setAccessibleText("Phím " + key + ", " + SHAPE_NAMES[accent % 4] + ". " + text);
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMaxHeight(Double.MAX_VALUE);
    b.setMinHeight(120);
    b.setAlignment(Pos.TOP_LEFT);
    b.getStyleClass().addAll("answer-card", "answer-tile", "tile-" + (accent % 4));
    b.setOnAction(e -> action.run());
    content.prefWidthProperty().bind(b.widthProperty().subtract(36));
    slot.prefWrapLengthProperty().bind(content.prefWidthProperty());
    controls.add(b);
    answerControls.put(id, b);
    markSlots.put(id, slot);
    keyLabels.put(id, cap);
    return b;
  }

  /** Marks {@code tile} as the locked answer and fades the other tiles back. */
  protected void lock(Button tile) {
    if (!tile.getStyleClass().contains("chosen")) tile.getStyleClass().add("chosen");
    if (!box.getStyleClass().contains("locked")) box.getStyleClass().add("locked");
    for (var entry : answerControls.entrySet())
      if (entry.getValue() == tile && markSlots.get(entry.getKey()) instanceof Pane slot)
        slot.getChildren().add(chip("ĐÃ KHÓA", "lock-stamp"));
  }

  /**
   * Up to four tiles share one row; more options wrap onto further rows. When the deck is too
   * narrow for four readable tiles (a small window, large text or the chat open) it falls back to
   * two.
   */
  protected GridPane tileGrid(List<Button> tiles) {
    var grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(16);
    int most = Math.max(1, Math.min(4, tiles.size()));
    arrange(grid, tiles, most);
    grid.widthProperty()
        .addListener(
            (o, b, width) -> {
              int columns = most > 2 && width.doubleValue() < most * MIN_TILE ? 2 : most;
              if (columns != grid.getColumnConstraints().size()) arrange(grid, tiles, columns);
            });
    return grid;
  }

  /** The narrowest a tile gets before the deck drops to two columns. */
  private static final double MIN_TILE = 190;

  private static void arrange(GridPane grid, List<Button> tiles, int columns) {
    grid.getChildren().clear();
    grid.getColumnConstraints().clear();
    for (int c = 0; c < columns; c++) {
      var column = new ColumnConstraints();
      column.setPercentWidth(100.0 / columns);
      grid.getColumnConstraints().add(column);
    }
    for (int i = 0; i < tiles.size(); i++) {
      grid.add(tiles.get(i), i % columns, i / columns);
      GridPane.setFillHeight(tiles.get(i), true);
    }
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
    var slot = (Pane) markSlots.get(id);
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
        if (outcome.userId() == self) control.getStyleClass().add("own-pick");
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
    keyLabels.clear();
    box.getStyleClass().remove("locked");
  }
}
