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
  private final List<VBox> cards = new ArrayList<>();
  private final List<Button> opponents = new ArrayList<>();
  private final ClientState state;

  record Selection(long quiz, long opponent) {}

  Selection selection() {
    return new Selection(
        selectedQuiz == null ? 0 : selectedQuiz.path("quizId").asLong(),
        selectedOpponent == null ? 0 : selectedOpponent.path("userId").asLong());
  }

  void restore(Selection selection) {
    if (selection == null) return;
    JsonNode quizzes = state.payload(MessageType.QUIZ_LIST),
        users = state.payload(MessageType.ONLINE_LIST);
    if (quizzes != null)
      for (var quiz : quizzes.path("items"))
        if (quiz.path("quizId").asLong() == selection.quiz()
            && quiz.path("availability").asText().equals("AVAILABLE")) selectedQuiz = quiz;
    if (users != null)
      for (var user : users.path("users"))
        if (user.path("userId").asLong() == selection.opponent()
            && user.path("status").asText().equals("FREE")) selectedOpponent = user;
    if (selectedQuiz != null)
      for (var card : cards)
        if (((Long) card.getProperties().get("quizId")) == selection.quiz())
          card.getStyleClass().add("selected");
    if (selectedOpponent != null)
      for (var button : opponents)
        if (((JsonNode) button.getUserData()).path("userId").asLong() == selection.opponent())
          button.getStyleClass().add("selected");
    challenge.setDisable(!canChallenge(selectedQuiz, selectedOpponent, state.selfUserId(), false));
  }

  public LobbyView(
      ClientState state, UiCommandSink sink, java.util.function.Consumer<JsonNode> detail) {
    this(state, sink, detail, null);
  }

  public LobbyView(
      ClientState state,
      UiCommandSink sink,
      java.util.function.Consumer<JsonNode> detail,
      Long categoryId) {
    this.state = state;
    setPadding(new Insets(24));
    var library = new VBox(18, Ui.title("Khám phá bộ câu hỏi"));
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
    for (int i = 0; i < topics.getChildren().size(); i++) {
      var b = (Button) topics.getChildren().get(i);
      b.getStyleClass().add("topic");
      if ((i == 0 && categoryId == null)
          || (i > 0
              && list != null
              && categoryId != null
              && list.path("categories").get(i - 1).path("categoryId").asLong() == categoryId))
        b.getStyleClass().add("active");
    }
    // Category labels remain on one row and scroll rather than clip at minimum width.
    var categoriesScroll = new ScrollPane(topics);
    categoriesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    categoriesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    categoriesScroll.setFitToHeight(true);
    categoriesScroll.setFocusTraversable(false);
    library.getChildren().add(categoriesScroll);
    var search = new TextField();
    search.setPromptText("Tìm tên quiz trên trang này…");
    search.setAccessibleText("Tìm quiz trên trang hiện tại");
    library
        .getChildren()
        .addAll(Ui.muted("Chọn một quiz, sau đó chọn người chơi để gửi lời thách đấu."), search);
    var grid = new TilePane(16, 16);
    grid.setPrefColumns(2);
    grid.setMinWidth(0);
    if (list == null || list.path("items").isEmpty())
      grid.getChildren().add(Ui.label("Chưa có bộ câu hỏi. Nhấn tải lại để kiểm tra."));
    else
      for (var q : list.path("items")) {
        var cover =
            new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                .view(q.path("coverAssetId").asText("placeholder"), 235);
        cover.setFitHeight(145);
        var card =
            Ui.stack(
                cover,
                Ui.label(q.path("title").asText()),
                Ui.label(
                    q.path("categoryName").asText()
                        + " · "
                        + q.path("totalRounds").asInt(10)
                        + " câu · "
                        + (q.path("availability").asText().equals("AVAILABLE")
                            ? "Sẵn sàng"
                            : "Chưa khả dụng")));
        if (q.path("quizSource").asText().equals("COMMUNITY"))
          card.getChildren()
              .add(Ui.label("Cộng đồng · Không tính hạng · " + q.path("authorName").asText()));
        card.getStyleClass().add("quiz-card");
        card.setPrefWidth(250);
        card.setMaxWidth(250);
        card.setMinHeight(Region.USE_PREF_SIZE);
        ((Label) card.getChildren().get(1)).getStyleClass().add("card-title");
        cards.add(card);
        card.getProperties().put("quizId", q.path("quizId").asLong());
        card.setUserData(q.path("title").asText().toLowerCase(java.util.Locale.ROOT));
        var select =
            Ui.button(
                "Chọn bộ câu hỏi",
                () -> {
                  selectedQuiz = q;
                  grid.getChildren().forEach(n -> n.getStyleClass().remove("selected"));
                  card.getStyleClass().add("selected");
                  detail.accept(q);
                });
        select.setDisable(!q.path("availability").asText().equals("AVAILABLE"));
        select.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(select);
        grid.getChildren().add(card);
      }
    var noResults = Ui.muted("Không tìm thấy quiz trên trang này. Thử tên khác hoặc chuyển trang.");
    noResults.setVisible(false);
    noResults.setManaged(false);
    search
        .textProperty()
        .addListener(
            (o, old, value) -> {
              String term = value.strip().toLowerCase(java.util.Locale.ROOT);
              for (var node : grid.getChildren()) {
                boolean show =
                    node.getUserData() == null || node.getUserData().toString().contains(term);
                node.setVisible(show);
                node.setManaged(show);
              }
              boolean empty = grid.getChildren().stream().noneMatch(Node::isManaged);
              noResults.setVisible(empty);
              noResults.setManaged(empty);
            });
    library.getChildren().addAll(grid, noResults);
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
    var libraryScroll = Ui.scroll(library);
    setCenter(libraryScroll);
    javafx.application.Platform.runLater(() -> libraryScroll.setVvalue(0));
    var online = new VBox(14, Ui.title("Cùng đấu trí"), Ui.muted("Chọn một đối thủ đang rảnh"));
    online.setPrefWidth(260);
    online.setMinWidth(250);
    online.getStyleClass().add("online-panel");
    BorderPane.setMargin(online, new Insets(0, 0, 0, 24));
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
                    + (user.path("status").asText().equals("FREE") ? "Sẵn sàng" : "Đang bận")
                    + " · "
                    + user.path("totalScore").asLong(),
                () -> {
                  selectedOpponent = user;
                  for (var node : online.getChildren()) node.getStyleClass().remove("selected");
                  for (var node : online.getChildren())
                    if (node instanceof Button row && row.getUserData() == user)
                      row.getStyleClass().add("selected");
                  challenge.setDisable(
                      !canChallenge(selectedQuiz, user, state.selfUserId(), false));
                });
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMinWidth(0);
        b.setWrapText(true);
        b.setUserData(user);
        b.getStyleClass().add("opponent");
        b.setGraphic(
            new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                .view(user.path("avatarId").asText(), 36));
        b.setDisable(!user.path("status").asText().equals("FREE"));
        opponents.add(b);
        online.getChildren().add(b);
      }
    var controls =
        new VBox(
            10,
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
    challenge.setMaxWidth(Double.MAX_VALUE);
    var onlineScroll = Ui.scroll(online);
    VBox.setVgrow(onlineScroll, Priority.ALWAYS);
    controls.setPadding(new Insets(12, 20, 0, 20));
    var sidebar = new VBox(onlineScroll, controls);
    sidebar.setPrefWidth(284);
    sidebar.setMinWidth(284);
    BorderPane.setMargin(sidebar, new Insets(0, 0, 0, 20));
    setRight(sidebar);
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
