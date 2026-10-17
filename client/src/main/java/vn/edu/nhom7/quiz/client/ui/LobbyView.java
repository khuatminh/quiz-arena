package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/**
 * The lobby as a game menu: three steps across the top (pick a quiz, pick an opponent, send the
 * challenge), quiz cards in the middle, players online on the right and a key hint bar below.
 */
public final class LobbyView extends BorderPane {
  private JsonNode selectedQuiz, selectedOpponent;
  private Button challenge;
  private final Label hint = Ui.label("");
  private final List<VBox> cards = new ArrayList<>();
  private final List<Button> picks = new ArrayList<>();
  private final List<Button> opponents = new ArrayList<>();
  private final List<HBox> steps = new ArrayList<>();
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
    refreshHint();
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
    getStyleClass().add("lobby");
    setPadding(new Insets(20, 24, 12, 24));
    var stepRow =
        new HBox(
            12,
            step(1, "Chọn bộ câu hỏi"),
            stepLine(),
            step(2, "Chọn đối thủ"),
            stepLine(),
            step(3, "Gửi lời mời"));
    stepRow.getStyleClass().add("lobby-steps");
    stepRow.setAlignment(Pos.CENTER_LEFT);
    BorderPane.setMargin(stepRow, new Insets(0, 0, 18, 0));
    setTop(stepRow);
    var library = new VBox(16, Ui.title("Khám phá bộ câu hỏi"));
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
    library.getChildren().add(search);
    var grid = new TilePane(16, 16);
    grid.setPrefColumns(3);
    grid.setMinWidth(0);
    grid.getStyleClass().add("quiz-grid");
    if (list == null || list.path("items").isEmpty())
      grid.getChildren().add(Ui.label("Chưa có bộ câu hỏi. Nhấn tải lại để kiểm tra."));
    else for (var q : list.path("items")) grid.getChildren().add(card(q, detail));
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
        Ui.secondary(
            "← Trước",
            () ->
                sink.send(
                    MessageType.QUIZ_LIST_REQUEST,
                    null,
                    null,
                    new Payloads.QuizListRequest(categoryId, page - 1, pageSize)));
    var next =
        Ui.secondary(
            "Tiếp →",
            () ->
                sink.send(
                    MessageType.QUIZ_LIST_REQUEST,
                    null,
                    null,
                    new Payloads.QuizListRequest(categoryId, page + 1, pageSize)));
    previous.setDisable(page <= 1);
    next.setDisable((long) page * pageSize >= total);
    var pageLabel = Ui.label("Trang " + page);
    pageLabel.getStyleClass().add("page-label");
    var pager = new HBox(12, previous, pageLabel, next);
    pager.setAlignment(Pos.CENTER_LEFT);
    pager.getStyleClass().add("pager");
    library.getChildren().add(pager);
    var libraryScroll = Ui.scroll(library);
    libraryScroll.setMinHeight(0);
    setCenter(libraryScroll);
    javafx.application.Platform.runLater(() -> libraryScroll.setVvalue(0));

    JsonNode users = state.payload(MessageType.ONLINE_LIST);
    int free = 0;
    if (users != null)
      for (var user : users.path("users"))
        if (user.path("userId").asLong() != state.selfUserId()
            && user.path("status").asText().equals("FREE")) free++;
    var freeCount = Ui.label(free + " người rảnh");
    freeCount.getStyleClass().add("online-count");
    var online = new VBox(10, Ui.kicker("ĐANG TRỰC TUYẾN"), freeCount);
    online.getStyleClass().add("online-panel");
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
                refreshHint();
              }
            });
    challenge.getStyleClass().add("challenge-button");
    challenge.setDisable(true);
    hint.getStyleClass().add("challenge-hint");
    hint.setMinHeight(Region.USE_PREF_SIZE);
    boolean anyoneElse = false;
    if (users != null)
      for (var user : users.path("users")) {
        if (user.path("userId").asLong() == state.selfUserId()) continue;
        anyoneElse = true;
        online.getChildren().add(opponent(user, online));
      }
    if (!anyoneElse)
      online
          .getChildren()
          .add(Ui.muted("Chưa có người chơi nào khác trực tuyến. Nhấn Tải lại sau ít phút."));
    var reload =
        Ui.secondary(
            "Tải lại",
            () -> {
              sink.send(
                  MessageType.QUIZ_LIST_REQUEST,
                  null,
                  null,
                  new Payloads.QuizListRequest(categoryId, page, pageSize));
              sink.send(
                  MessageType.ONLINE_LIST_REQUEST, null, null, new Payloads.OnlineListRequest());
            });
    reload.setMaxWidth(Double.MAX_VALUE);
    var controls = new VBox(10, hint, challenge, reload);
    challenge.setMaxWidth(Double.MAX_VALUE);
    challenge.setMinHeight(Region.USE_PREF_SIZE);
    reload.setMinHeight(Region.USE_PREF_SIZE);
    controls.getStyleClass().add("challenge-controls");
    var onlineScroll = Ui.scroll(online);
    onlineScroll.setMinHeight(0);
    VBox.setVgrow(onlineScroll, Priority.ALWAYS);
    var sidebar = new VBox(12, onlineScroll, controls);
    sidebar.setPrefWidth(284);
    sidebar.setMinWidth(284);
    sidebar.setMaxWidth(284);
    sidebar.setMinHeight(0);
    BorderPane.setMargin(sidebar, new Insets(0, 0, 0, 20));
    setRight(sidebar);

    var prompts =
        new HBox(
            20,
            Ui.prompt("← →", "Đổi bộ câu hỏi"),
            Ui.prompt("↑ ↓", "Đổi đối thủ"),
            Ui.prompt("Enter", "Chọn"),
            Ui.prompt("Tab", "Sang vùng khác"));
    prompts.getStyleClass().add("prompt-bar");
    prompts.setAlignment(Pos.CENTER_LEFT);
    prompts.setMinWidth(0);
    var clip = new javafx.scene.shape.Rectangle();
    clip.widthProperty().bind(prompts.widthProperty());
    clip.heightProperty().bind(prompts.heightProperty());
    prompts.setClip(clip);
    BorderPane.setMargin(prompts, new Insets(12, 0, 0, 0));
    setBottom(prompts);
    addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    refreshHint();
  }

  private VBox card(JsonNode q, java.util.function.Consumer<JsonNode> detail) {
    boolean available = q.path("availability").asText().equals("AVAILABLE");
    var cover =
        new vn.edu.nhom7.quiz.client.assets.AssetLoader()
            .view(q.path("coverAssetId").asText("placeholder"), 226);
    cover.setFitHeight(132);
    var coverBox = new StackPane(cover);
    coverBox.getStyleClass().add("quiz-cover");
    if (q.path("quizSource").asText().equals("COMMUNITY")) {
      var community = Ui.badge("Cộng đồng");
      StackPane.setAlignment(community, Pos.TOP_LEFT);
      StackPane.setMargin(community, new Insets(8));
      coverBox.getChildren().add(community);
    }
    var title = Ui.label(q.path("title").asText());
    title.getStyleClass().add("card-title");
    var meta =
        Ui.muted(
            q.path("categoryName").asText()
                + " · "
                + q.path("totalRounds").asInt(10)
                + " câu · "
                + (q.path("quizSource").asText().equals("COMMUNITY")
                    ? "Không tính hạng · " + q.path("authorName").asText()
                    : "Tính hạng"));
    var card = new VBox(10, coverBox, title, meta);
    card.getStyleClass().add("quiz-card");
    card.setPrefWidth(258);
    card.setMaxWidth(258);
    card.setMinHeight(Region.USE_PREF_SIZE);
    cards.add(card);
    card.getProperties().put("quizId", q.path("quizId").asLong());
    card.setUserData(q.path("title").asText().toLowerCase(java.util.Locale.ROOT));
    var spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);
    var pick = Ui.button(available ? "Chọn" : "Chưa khả dụng", () -> {});
    pick.setOnAction(
        e -> {
          selectedQuiz = q;
          cards.forEach(n -> n.getStyleClass().remove("selected"));
          card.getStyleClass().add("selected");
          detail.accept(q);
          refreshHint();
        });
    pick.setDisable(!available);
    pick.setMaxWidth(Double.MAX_VALUE);
    pick.setAccessibleText("Chọn bộ câu hỏi " + q.path("title").asText());
    pick.getProperties().put("card", card);
    picks.add(pick);
    card.getChildren().addAll(spacer, pick);
    if (available) card.setOnMouseClicked(e -> pick.fire());
    else card.getStyleClass().add("unavailable");
    return card;
  }

  private Button opponent(JsonNode user, VBox online) {
    boolean free = user.path("status").asText().equals("FREE");
    var name = Ui.label(user.path("displayName").asText());
    name.getStyleClass().add("opponent-name");
    var meta =
        Ui.label(
            (free ? "Sẵn sàng" : "Đang bận") + " · " + user.path("totalScore").asLong() + " điểm");
    meta.getStyleClass().addAll("opponent-meta", free ? "free" : "busy");
    // Wrapping labels inside a button graphic get no width to wrap against and grow very tall.
    name.setWrapText(false);
    meta.setWrapText(false);
    var text = new VBox(2, name, meta);
    text.setMinWidth(0);
    var avatar =
        new vn.edu.nhom7.quiz.client.assets.AssetLoader().view(user.path("avatarId").asText(), 36);
    var row = new HBox(10, avatar, text);
    row.setAlignment(Pos.CENTER_LEFT);
    var b = new Button();
    b.setGraphic(row);
    b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    b.setAccessibleText(
        user.path("displayName").asText() + ", " + (free ? "sẵn sàng" : "đang bận"));
    b.setOnAction(
        e -> {
          selectedOpponent = user;
          for (var node : online.getChildren()) node.getStyleClass().remove("selected");
          b.getStyleClass().add("selected");
          challenge.setDisable(!canChallenge(selectedQuiz, user, state.selfUserId(), false));
          refreshHint();
        });
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMinWidth(0);
    b.setUserData(user);
    b.getStyleClass().add("opponent");
    b.setDisable(!free);
    opponents.add(b);
    return b;
  }

  private HBox step(int number, String text) {
    var badge = Ui.label(Integer.toString(number));
    badge.getStyleClass().add("step-number");
    badge.setWrapText(false);
    badge.setMinWidth(Region.USE_PREF_SIZE);
    var label = Ui.label(text);
    label.getStyleClass().add("step-text");
    label.setWrapText(false);
    var step = new HBox(10, badge, label);
    step.getStyleClass().add("lobby-step");
    step.setAlignment(Pos.CENTER_LEFT);
    step.setMinWidth(0);
    steps.add(step);
    return step;
  }

  private static Region stepLine() {
    var line = new Region();
    line.getStyleClass().add("step-line");
    line.setPrefWidth(48);
    line.setMinWidth(12);
    line.setMaxHeight(2);
    HBox.setHgrow(line, Priority.SOMETIMES);
    return line;
  }

  /** Arrow keys move between quiz cards and between players; Enter picks what has focus. */
  private void onKey(KeyEvent e) {
    if (!(getScene() != null && getScene().getFocusOwner() instanceof Button focused)) return;
    if (e.getCode() == KeyCode.ENTER) {
      if (!focused.isDisabled()) focused.fire();
      e.consume();
      return;
    }
    List<Button> group = picks.contains(focused) ? picks : opponents;
    if (!group.contains(focused)) return;
    int direction =
        switch (e.getCode()) {
          case LEFT -> group == picks ? -1 : 0;
          case RIGHT -> group == picks ? 1 : 0;
          case UP -> group == opponents ? -1 : 0;
          case DOWN -> group == opponents ? 1 : 0;
          default -> 0;
        };
    if (direction == 0) return;
    e.consume();
    int i = group.indexOf(focused);
    for (int n = 1; n < group.size(); n++) {
      var candidate = group.get(Math.floorMod(i + direction * n, group.size()));
      var owner = candidate.getProperties().get("card");
      if (!candidate.isDisabled() && (owner == null || ((Node) owner).isManaged())) {
        candidate.requestFocus();
        return;
      }
    }
  }

  public void selectQuiz(JsonNode q) {
    selectedQuiz = q;
    challenge.setDisable(!canChallenge(q, selectedOpponent, 0, false));
    refreshHint();
  }

  private void refreshHint() {
    hint.setText(
        selectedQuiz == null && selectedOpponent == null
            ? "Chọn quiz và đối thủ để thách đấu."
            : selectedQuiz == null
                ? "Còn thiếu: chọn một quiz."
                : selectedOpponent == null
                    ? "Còn thiếu: chọn một đối thủ."
                    : challenge.isDisabled()
                        ? "Đã gửi lời thách đấu."
                        : "Sẵn sàng: "
                            + selectedQuiz.path("title").asText()
                            + " · đấu với "
                            + selectedOpponent.path("displayName").asText());
    boolean sent = selectedQuiz != null && selectedOpponent != null && challenge.isDisabled();
    boolean[] done = {selectedQuiz != null, selectedOpponent != null, sent};
    int current = selectedQuiz == null ? 0 : selectedOpponent == null ? 1 : sent ? -1 : 2;
    for (int i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      step.getStyleClass().removeAll("done", "current");
      if (done[i]) step.getStyleClass().add("done");
      else if (i == current) step.getStyleClass().add("current");
      ((Label) step.getChildren().getFirst()).setText(done[i] ? "✓" : Integer.toString(i + 1));
    }
    for (var pick : picks)
      if (!pick.isDisabled()) {
        boolean chosen =
            ((Node) pick.getProperties().get("card")).getStyleClass().contains("selected");
        pick.setText(chosen ? "✓ Đã chọn" : "Chọn");
      }
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
