package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ChatPanel extends VBox {
  private final VBox messages = new VBox(8);
  private final TextArea input = new TextArea();
  private final Set<UUID> ids = new HashSet<>();
  private final ScrollPane scroll;
  private final TilePane tray = new TilePane(6, 6);
  private boolean primed;

  public ChatPanel(java.util.function.Consumer<String> send) {
    getStyleClass().add("chat-panel");
    setSpacing(14);
    setPadding(new Insets(16));
    setPrefWidth(270);
    scroll = Ui.scroll(messages);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren()
        .addAll(Ui.title("Trò chuyện"), Ui.muted("Enter để gửi · Shift+Enter xuống dòng"), scroll);
    input.setPrefRowCount(2);
    input.setPromptText("Tin nhắn (1–300 ký tự)");
    Runnable submit =
        () -> {
          String text = input.getText().strip();
          if (text.isEmpty() || text.codePointCount(0, text.length()) > 300) return;
          send.accept(text);
          input.clear();
        };
    input.setOnKeyPressed(
        e -> {
          if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !e.isShiftDown()) {
            e.consume();
            submit.run();
          }
        });
    for (var sticker : Stickers.ALL) {
      var pick = new Button();
      pick.setGraphic(Stickers.view(sticker, 44, false));
      pick.getStyleClass().add("sticker-pick");
      pick.setAccessibleText("Gửi sticker " + sticker.caption());
      pick.setTooltip(new Tooltip(sticker.caption()));
      pick.setOnAction(
          e -> {
            send.accept(Stickers.token(sticker));
            showTray(false);
          });
      tray.getChildren().add(pick);
    }
    tray.setPrefColumns(4);
    tray.getStyleClass().add("sticker-tray");
    showTray(false);
    var smile = new Button();
    smile.setGraphic(
        Effects.glyph(
            22,
            Effects.stroke(
                "M12 2.5 A9.5 9.5 0 1 1 12 21.5 A9.5 9.5 0 1 1 12 2.5 Z", "#224C4B", 1.8),
            Effects.fill("M8.6 8.4 A1.3 1.3 0 1 1 8.6 11 A1.3 1.3 0 1 1 8.6 8.4 Z", "#224C4B"),
            Effects.fill("M15.4 8.4 A1.3 1.3 0 1 1 15.4 11 A1.3 1.3 0 1 1 15.4 8.4 Z", "#224C4B"),
            Effects.stroke("M7.8 14 C9.6 17.2 14.4 17.2 16.2 14", "#224C4B", 1.8)));
    smile.getStyleClass().addAll("secondary", "sticker-toggle");
    smile.setMinHeight(40);
    smile.setAccessibleText("Chọn sticker");
    smile.setTooltip(new Tooltip("Sticker"));
    smile.setOnAction(e -> showTray(!tray.isVisible()));
    var sendButton = Ui.button("Gửi", submit);
    sendButton.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(sendButton, Priority.ALWAYS);
    getChildren().addAll(tray, input, new HBox(8, smile, sendButton));
  }

  private void showTray(boolean show) {
    tray.setVisible(show);
    tray.setManaged(show);
  }

  private VBox bubble(JsonNode message, long self) {
    boolean mine = message.path("senderUserId").asLong() == self;
    var name = Ui.badge(message.path("displayName").asText() + (mine ? " · Bạn" : ""));
    var sticker = Stickers.parse(message.path("text").asText());
    if (sticker.isPresent()) {
      var art = Stickers.view(sticker.get(), 96);
      var box = new VBox(6, name, art);
      box.getStyleClass().addAll("chat-bubble", "sticker-bubble");
      if (mine) box.getStyleClass().add("mine");
      box.setAlignment(mine ? Pos.TOP_RIGHT : Pos.TOP_LEFT);
      if (primed) {
        var pop = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(420), art);
        pop.setFromX(0.2);
        pop.setFromY(0.2);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Effects.BACK_OUT);
        var wiggle = new javafx.animation.RotateTransition(javafx.util.Duration.millis(140), art);
        wiggle.setFromAngle(-8);
        wiggle.setToAngle(8);
        wiggle.setAutoReverse(true);
        wiggle.setCycleCount(4);
        wiggle.setOnFinished(e -> art.setRotate(0));
        Effects.run(
            new javafx.animation.SequentialTransition(pop, wiggle),
            null,
            javafx.util.Duration.millis(420));
      }
      return box;
    }
    var box = new VBox(6, name, Ui.label(message.path("text").asText()));
    box.getStyleClass().add("chat-bubble");
    if (mine) box.getStyleClass().add("mine");
    if (primed) {
      box.setOpacity(0);
      var fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), box);
      fade.setToValue(1);
      var rise = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(220), box);
      rise.setFromY(10);
      rise.setToY(0);
      Effects.run(
          new javafx.animation.ParallelTransition(fade, rise),
          null,
          javafx.util.Duration.millis(220));
    }
    return box;
  }

  public void render(ClientState s) {
    boolean added = false;
    for (var e : s.chatById().entrySet())
      if (ids.add(e.getKey())) {
        messages.getChildren().add(bubble(e.getValue(), s.selfUserId()));
        added = true;
      }
    // Keep the newest message in view.
    if (added) javafx.application.Platform.runLater(() -> scroll.setVvalue(1));
    primed = true;
    boolean detached = !s.resultAttached();
    setDisable(detached);
  }
}
