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

  public ChatPanel(java.util.function.Consumer<String> send) {
    setSpacing(10);
    setPadding(new Insets(16));
    setPrefWidth(270);
    getChildren().addAll(Ui.title("Trò chuyện"), Ui.scroll(messages));
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

  public void render(ClientState s) {
    for (var e : s.chatById().entrySet())
      if (ids.add(e.getKey()))
        messages
            .getChildren()
            .add(
                Ui.label(
                    e.getValue().path("displayName").asText()
                        + ": "
                        + e.getValue().path("text").asText()));
    boolean detached = !s.resultAttached();
    setDisable(detached);
  }
}
