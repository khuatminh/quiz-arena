package vn.edu.nhom7.quiz.client.ui;

import java.util.function.DoubleConsumer;
import java.util.function.IntSupplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

/**
 * The Esc menu: sound levels, text size, light or dark mode and reduced motion. During a match it
 * reads "Tạm dừng" but the match keeps running, so it says how long the question has left and
 * offers leaving (a forfeit) as its last, clearly marked option.
 */
public final class SettingsOverlay extends StackPane {
  private final IntSupplier secondsLeft;
  private final Label clock = Ui.label("");
  private final Button resume;
  private final boolean inMatch;
  private final java.util.List<javafx.scene.Node> stops = new java.util.ArrayList<>();

  /**
   * @param secondsLeft seconds left on the open question, or a negative number when none is open
   * @param leave forfeits the match; {@code null} outside a match
   */
  public SettingsOverlay(IntSupplier secondsLeft, Runnable close, Runnable leave) {
    this.secondsLeft = secondsLeft;
    this.inMatch = leave != null;
    getStyleClass().add("settings-backdrop");
    setPickOnBounds(true);
    var card = new VBox(14);
    card.getStyleClass().add("settings-card");
    card.setMaxWidth(520);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    var kicker = Ui.kicker(inMatch ? "TẠM DỪNG" : "CÀI ĐẶT");
    kicker.getStyleClass().add("settings-kicker");
    card.getChildren()
        .addAll(
            kicker, Ui.title(inMatch ? "Trận đấu › Tạm dừng" : "Cài đặt giao diện và âm thanh"));
    if (inMatch) {
      clock.getStyleClass().add("settings-clock");
      clock.setMaxWidth(Double.MAX_VALUE);
      card.getChildren().add(clock);
    }
    resume = Ui.button(inMatch ? "Tiếp tục" : "Xong", close);
    resume.setMaxWidth(Double.MAX_VALUE);
    resume.getStyleClass().add("settings-resume");
    card.getChildren().add(resume);
    stops.add(resume);

    for (var level :
        java.util.List.of(
            level(
                "Nhạc nền",
                Sound.musicOn() ? Sound.musicLevel() : 0,
                v -> {
                  // 0% is the mute switch; any other level also turns the sound back on.
                  if (v > 0) Sound.setMusicLevel(v);
                  if (Sound.musicOn() != v > 0) Sound.setMusicOn(v > 0);
                }),
            level(
                "Hiệu ứng",
                Sound.effectsOn() ? Sound.effectsLevel() : 0,
                v -> {
                  if (v > 0) Sound.setEffectsLevel(v);
                  if (Sound.effectsOn() != v > 0) Sound.setEffectsOn(v > 0);
                }))) {
      card.getChildren().add(level);
      stops.add((javafx.scene.Node) level.getProperties().get("focus"));
    }
    var sizes = new ToggleGroup();
    var sizeRow = segmented();
    for (var size : Theme.TextSize.values())
      sizeRow
          .getChildren()
          .add(
              choice(
                  size.label,
                  sizes,
                  Theme.textSize() == size,
                  () -> {
                    if (Theme.textSize() != size) Theme.setTextSize(size);
                  }));
    card.getChildren().add(row("Cỡ chữ", sizeRow));
    stops.add(sizeRow);
    var modes = new ToggleGroup();
    var modeRow = segmented();
    modeRow
        .getChildren()
        .addAll(
            choice(
                "Tối",
                modes,
                Theme.mode() == Theme.Mode.DARK,
                () -> {
                  if (Theme.mode() != Theme.Mode.DARK) Theme.setMode(Theme.Mode.DARK);
                }),
            choice(
                "Sáng",
                modes,
                Theme.mode() == Theme.Mode.LIGHT,
                () -> {
                  if (Theme.mode() != Theme.Mode.LIGHT) Theme.setMode(Theme.Mode.LIGHT);
                }));
    card.getChildren().add(row("Giao diện", modeRow));
    stops.add(modeRow);
    var motion = new CheckBox("Giảm chuyển động và nháy màn hình");
    motion.setSelected(Theme.reduceMotion());
    motion.setWrapText(true);
    motion.selectedProperty().addListener((o, b, on) -> Theme.setReduceMotion(on));
    motion.getStyleClass().add("settings-check");
    card.getChildren().add(motion);
    stops.add(motion);
    if (inMatch) {
      var forfeit =
          Ui.button(
              "Rời trận · bị xử thua",
              () -> {
                if (Ui.confirmLeaveMatch(this)) leave.run();
              });
      forfeit.getStyleClass().add("danger");
      forfeit.setMaxWidth(Double.MAX_VALUE);
      card.getChildren().add(forfeit);
      stops.add(forfeit);
    }
    var prompts =
        new FlowPane(
            18,
            8,
            Ui.prompt("↑ ↓", "Chọn"),
            Ui.prompt("← →", "Chỉnh"),
            Ui.prompt("Esc", inMatch ? "Về trận" : "Đóng"));
    prompts.getStyleClass().add("prompt-bar");
    card.getChildren().add(prompts);
    var scroll = Ui.scroll(card);
    scroll.setMaxWidth(560);
    scroll.setMaxHeight(Region.USE_PREF_SIZE);
    scroll.setFitToHeight(false);
    scroll.getStyleClass().add("settings-scroll");
    StackPane.setMargin(scroll, new Insets(24));
    getChildren().add(scroll);
    setAlignment(Pos.CENTER);
    addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            close.run();
            e.consume();
          } else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
            // Up and down always walk the menu, even from a slider or a row of choices.
            move(e.getCode() == KeyCode.UP ? -1 : 1);
            e.consume();
          } else if (e.getCode() == KeyCode.ENTER
              && getScene() != null
              && getScene().getFocusOwner() instanceof ButtonBase b) {
            b.fire();
            e.consume();
          }
        });
    tick();
  }

  /** Puts focus on "Tiếp tục" so Enter or Space resumes straight away. */
  public void focus() {
    resume.requestFocus();
  }

  private void move(int direction) {
    var node = getScene() == null ? null : getScene().getFocusOwner();
    while (node != null && !stops.contains(node)) node = node.getParent();
    int i = node == null ? (direction > 0 ? -1 : 0) : stops.indexOf(node);
    var next = stops.get(Math.floorMod(i + direction, stops.size()));
    if (next instanceof HBox choices)
      next =
          choices.getChildren().stream()
              .filter(c -> c instanceof ToggleButton t && t.isSelected())
              .findFirst()
              .orElse(choices.getChildren().getFirst());
    next.requestFocus();
  }

  /** Refreshes the countdown note; called by the shell's clock. */
  public void tick() {
    if (!inMatch) return;
    int left = secondsLeft.getAsInt();
    clock.setText(
        left >= 0
            ? "Đồng hồ vẫn chạy: còn " + left + " giây cho câu này"
            : "Đồng hồ vẫn chạy: trận đấu không dừng khi bạn mở menu này");
    clock.getStyleClass().remove("urgent");
    if (left >= 0 && left <= 5) clock.getStyleClass().add("urgent");
  }

  private static HBox level(String name, double value, DoubleConsumer apply) {
    var slider = new Slider(0, 100, Math.round(value * 100));
    slider.setBlockIncrement(10);
    slider.setMajorTickUnit(10);
    slider.setMinorTickCount(0);
    slider.setSnapToTicks(true);
    slider.setAccessibleText(name);
    HBox.setHgrow(slider, Priority.ALWAYS);
    slider.setMaxWidth(Double.MAX_VALUE);
    var percent = Ui.label(Math.round(value * 100) + "%");
    percent.getStyleClass().add("settings-value");
    percent.setWrapText(false);
    percent.setMinWidth(Region.USE_PREF_SIZE);
    slider
        .valueProperty()
        .addListener(
            (o, b, v) -> {
              percent.setText(Math.round(v.doubleValue()) + "%");
              apply.accept(v.doubleValue() / 100);
            });
    var box = new HBox(12, slider, percent);
    box.setAlignment(Pos.CENTER_LEFT);
    var row = row(name, box);
    row.getProperties().put("focus", slider);
    return row;
  }

  private static HBox row(String name, Region control) {
    var label = Ui.label(name);
    label.getStyleClass().add("settings-label");
    label.setMinWidth(Region.USE_PREF_SIZE);
    label.setPrefWidth(110);
    label.setWrapText(false);
    HBox.setHgrow(control, Priority.ALWAYS);
    var row = new HBox(14, label, control);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("settings-row");
    return row;
  }

  private static HBox segmented() {
    var box = new HBox(6);
    box.getStyleClass().add("segmented");
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private static ToggleButton choice(
      String text, ToggleGroup group, boolean selected, Runnable apply) {
    var b = new ToggleButton(text);
    b.setToggleGroup(group);
    b.setSelected(selected);
    b.setMinWidth(Region.USE_PREF_SIZE);
    // A segmented choice is never empty: clicking the chosen one keeps it chosen.
    b.setOnAction(
        e -> {
          b.setSelected(true);
          apply.run();
        });
    return b;
  }
}
