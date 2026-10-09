package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class LobbyView extends BorderPane {
  private JsonNode selectedQuiz, selectedOpponent;
  private Button challenge;

  public LobbyView(
      ClientState state, UiCommandSink sink, java.util.function.Consumer<JsonNode> detail) {
    this(state, sink, detail, null);
  }

  public LobbyView(
      ClientState state,
      UiCommandSink sink,
      java.util.function.Consumer<JsonNode> detail,
      Long categoryId) {
    setPadding(new Insets(24));
    var library = new VBox(16, Ui.title("Chọn chủ đề. Mời đối thủ. Bắt đầu!"));
    var topics = new HBox(10);
    topics
        .getChildren()
        .add(
            Ui.button(
                "Tất cả",
                () ->
                    sink.send(
                        MessageType.QUIZ_LIST_REQUEST,
                        null,
                        null,
                        new Payloads.QuizListRequest(null, 1, 20))));
    JsonNode list = state.payload(MessageType.QUIZ_LIST);
    if (list != null) {
      for (var c : list.path("categories"))
        topics
            .getChildren()
            .add(
                Ui.button(
                    c.path("categoryName").asText(),
                    () ->
                        sink.send(
                            MessageType.QUIZ_LIST_REQUEST,
                            null,
                            null,
                            new Payloads.QuizListRequest(c.path("categoryId").asLong(), 1, 20))));
    }
    library.getChildren().add(topics);
    var grid = new TilePane(16, 16);
    grid.setPrefColumns(2);
    if (list == null || list.path("items").isEmpty())
      grid.getChildren().add(Ui.label("Chưa có bộ câu hỏi. Nhấn tải lại để kiểm tra."));
    else
      for (var q : list.path("items")) {
        var card =
            Ui.stack(
                new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                    .view(q.path("coverAssetId").asText("placeholder"), 180),
                Ui.title(q.path("title").asText()),
                Ui.label(
                    q.path("categoryName").asText()
                        + " · "
                        + q.path("totalRounds").asInt(10)
                        + " câu · "
                        + q.path("availability").asText()));
        if (q.path("quizSource").asText().equals("COMMUNITY"))
          card.getChildren()
              .add(Ui.label("Cộng đồng · Không tính hạng · " + q.path("authorName").asText()));
        card.getStyleClass().add("quiz-card");
        card.setPrefWidth(280);
        var select =
            Ui.button(
                "Chọn bộ câu hỏi",
                () -> {
                  selectedQuiz = q;
                  detail.accept(q);
                });
        select.setDisable(!q.path("availability").asText().equals("AVAILABLE"));
        card.getChildren().add(select);
        grid.getChildren().add(card);
      }
    library.getChildren().add(grid);
    int page = list == null ? 1 : Math.max(1, list.path("page").asInt(1));
    int pageSize = list == null ? 20 : Math.max(1, list.path("pageSize").asInt(20));
    long total = list == null ? 0 : list.path("totalItems").asLong();
    var previous =
        Ui.button(
            "← Trước",
            () ->
                sink.send(
                    MessageType.QUIZ_LIST_REQUEST,
                    null,
                    null,
                    new Payloads.QuizListRequest(categoryId, page - 1, pageSize)));
    var next =
        Ui.button(
            "Tiếp →",
            () ->
                sink.send(
                    MessageType.QUIZ_LIST_REQUEST,
                    null,
                    null,
                    new Payloads.QuizListRequest(categoryId, page + 1, pageSize)));
    previous.setDisable(page <= 1);
    next.setDisable((long) page * pageSize >= total);
    library.getChildren().add(new HBox(12, previous, Ui.label("Trang " + page), next));
    setCenter(Ui.scroll(library));
    var online = new VBox(12, Ui.title("Người chơi trực tuyến"));
    online.setPrefWidth(300);
    challenge =
        Ui.button(
            "Gửi lời thách đấu",
            () -> {
              if (canChallenge(selectedQuiz, selectedOpponent, state.selfUserId(), false)) {
                sink.send(
                    MessageType.CHALLENGE,
                    null,
                    null,
                    new Payloads.Challenge(
                        selectedOpponent.path("userId").asLong(),
                        selectedQuiz.path("quizId").asLong()));
                challenge.setDisable(true);
              }
            });
    challenge.setDisable(true);
    JsonNode users = state.payload(MessageType.ONLINE_LIST);
    if (users != null)
      for (var user : users.path("users")) {
        if (user.path("userId").asLong() == state.selfUserId()) continue;
        var b =
            Ui.button(
                user.path("displayName").asText()
                    + " · "
                    + user.path("status").asText()
                    + " · "
                    + user.path("totalScore").asLong(),
                () -> {
                  selectedOpponent = user;
                  challenge.setDisable(
                      !canChallenge(selectedQuiz, user, state.selfUserId(), false));
                });
        b.setMaxWidth(Double.MAX_VALUE);
        online.getChildren().add(b);
      }
    online
        .getChildren()
        .addAll(
            challenge,
            Ui.button(
                "Tải lại",
                () -> {
                  sink.send(
                      MessageType.QUIZ_LIST_REQUEST,
                      null,
                      null,
                      new Payloads.QuizListRequest(categoryId, page, pageSize));
                  sink.send(
                      MessageType.ONLINE_LIST_REQUEST,
                      null,
                      null,
                      new Payloads.OnlineListRequest());
                }));
    setRight(online);
  }

  public void selectQuiz(JsonNode q) {
    selectedQuiz = q;
    challenge.setDisable(!canChallenge(q, selectedOpponent, 0, false));
  }

  public static boolean canChallenge(JsonNode q, JsonNode opponent, long self, boolean pending) {
    return q != null
        && q.path("availability").asText().equals("AVAILABLE")
        && opponent != null
        && opponent.path("userId").asLong() != self
        && opponent.path("status").asText().equals("FREE")
        && !pending;
  }
}
