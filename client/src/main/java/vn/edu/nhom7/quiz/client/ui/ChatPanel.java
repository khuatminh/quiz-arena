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
    getChildren().addAll(input, Ui.button("Gửi", submit));
  }

  private VBox bubble(JsonNode message, long self) {
    boolean mine = message.path("senderUserId").asLong() == self;
    var name = Ui.badge(message.path("displayName").asText() + (mine ? " · Bạn" : ""));
    var box = new VBox(6, name, Ui.label(message.path("text").asText()));
    box.getStyleClass().add("chat-bubble");
    if (mine) box.getStyleClass().add("mine");
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
    boolean detached = !s.resultAttached();
    setDisable(detached);
  }
}
