package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import vn.edu.nhom7.quiz.client.network.NetworkClient;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Opt-in visual evidence from real controls, not hand-built image mockups. */
@EnabledIfSystemProperty(named = "quiz.uiEvidence", matches = ".+")
class InterfaceEvidenceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final UiCommandSink sink = (t, m, r, p) -> UUID.randomUUID();
  private Stage stage;
  private Scene scene;
  private final List<AutoCloseable> cleanup = new ArrayList<>();
  private final List<Map.Entry<String, Parent>> views = new ArrayList<>();

  private void fx(Runnable r) throws Exception {
    var latch = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    Platform.runLater(
        () -> {
          try {
            r.run();
          } catch (Throwable t) {
            failure.set(t);
          } finally {
            latch.countDown();
          }
        });
    assertTrue(latch.await(20, TimeUnit.SECONDS));
    if (failure.get() != null) throw new AssertionError(failure.get());
  }

  @Test
  void renderAllSurfacesAtBothDesktopSizes() throws Exception {
    var started = new CountDownLatch(1);
    try {
      Platform.startup(started::countDown);
    } catch (IllegalStateException e) {
      started.countDown();
    }
    assertTrue(started.await(10, TimeUnit.SECONDS));
    Platform.setImplicitExit(false);
    fx(
        () -> {
          stage = new Stage();
          scene = new Scene(new VBox(), 1280, 800);
          scene.getStylesheets().add(getClass().getResource("/ui/quiz-arena.css").toExternalForm());
          stage.setScene(scene);
          stage.show();
          build();
        });
    try {
      for (int[] size : List.of(new int[] {1280, 800}, new int[] {1024, 720})) {
        for (var entry : views) {
          fx(
              () -> {
                scene.setRoot(entry.getValue());
                stage.setWidth(size[0]);
                stage.setHeight(size[1]);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
              });
          // Second FX dispatch lets asynchronous bundled-image assignments settle.
          fx(() -> snapshot(entry.getKey(), size[0], size[1]));
        }
        for (var entry : views)
          if (entry.getKey().startsWith("editor-")) {
            fx(
                () -> {
                  scene.setRoot(entry.getValue());
                  stage.setWidth(size[0]);
                  stage.setHeight(size[1]);
                  scene.getRoot().applyCss();
                  scene.getRoot().layout();
                });
            for (boolean reveal : List.of(false, true)) {
              var dialog = new AtomicReference<Window>();
              fx(
                  () -> {
                    findButton(entry.getValue(), reveal ? "Xem công bố đáp án" : "Xem trước câu")
                        .fire();
                    dialog.set(
                        Window.getWindows().stream()
                            .filter(w -> w != stage && w.isShowing())
                            .findFirst()
                            .orElseThrow());
                  });
              fx(
                  () -> {
                    var original = scene;
                    scene = dialog.get().getScene();
                    try {
                      snapshot(
                          "preview-" + (reveal ? "reveal-" : "question-") + entry.getKey(),
                          size[0],
                          size[1]);
                    } finally {
                      scene = original;
                      dialog.get().hide();
                    }
                  });
            }
          }
      }
    } finally {
      fx(
          () -> {
            stage.close();
            for (var c : cleanup)
              try {
                c.close();
              } catch (Exception ignored) {
              }
          });
    }
  }

  private void add(String name, Parent view) {
    views.add(Map.entry(name, view));
  }

  private void build() {
    try {
      var auth = new AuthView(sink, (h, p) -> {});
      auth.connected(true);
      add("01-login", auth);
      var registration = new AuthView(sink, (h, p) -> {});
      registration.connected(true);
      registration.lookupAll(".button");
      findButton(registration, "Đăng ký").fire();
      add("02-registration", registration);
      var codec = new ProtocolCodec();
      var reducer = new ClientStateReducer();
      var state = ClientState.initial();
      var profile =
          json.createObjectNode()
              .put("userId", 101)
              .put("displayName", "Minh")
              .put("avatarId", "avatar-01")
              .put("totalScore", 1250);
      state =
          reducer.apply(
              state,
              envelope(
                  MessageType.LOGIN_RESULT,
                  null,
                  null,
                  null,
                  null,
                  json.createObjectNode().set("profile", profile)),
              System.nanoTime());
      var categories = json.createArrayNode();
      categories.addObject().put("categoryId", 1).put("categoryName", "Tổng hợp");
      categories.addObject().put("categoryId", 2).put("categoryName", "Khoa học");
      categories.addObject().put("categoryId", 3).put("categoryName", "Lịch sử");
      var quizzes = json.createArrayNode();
      for (int i = 0; i < 6; i++)
        quizzes
            .addObject()
            .put("quizId", i + 1)
            .put(
                "title",
                List.of("Kiến thức chung", "Khám phá khoa học", "Dấu ấn lịch sử").get(i % 3))
            .put("categoryId", i % 3 + 1)
            .put("categoryName", List.of("Tổng hợp", "Khoa học", "Lịch sử").get(i % 3))
            .put(
                "coverAssetId",
                List.of("quiz-cover-general", "quiz-cover-science", "quiz-cover-history")
                    .get(i % 3))
            .put("availability", "AVAILABLE")
            .put("totalRounds", 10)
            .put("quizSource", i == 5 ? "COMMUNITY" : "SYSTEM")
            .put("authorName", "Minh");
      ObjectNode quizList =
          json.createObjectNode()
              .put("page", 1)
              .put("pageSize", 10)
              .put("totalItems", 6)
              .set("categories", categories);
      quizList.set("items", quizzes);
      state =
          reducer.apply(
              state,
              envelope(MessageType.QUIZ_LIST, null, null, null, null, quizList),
              System.nanoTime());
      var online = json.createArrayNode();
      for (int i = 1; i <= 6; i++)
        online
            .addObject()
            .put("userId", 101 + i)
            .put("displayName", List.of("Tuấn", "Phúc", "Lan", "Huy", "Trang", "Nam").get(i - 1))
            .put("avatarId", "avatar-%02d".formatted(i))
            .put("status", i == 4 ? "PLAYING" : "FREE")
            .put("totalScore", 900 - i * 45);
      state =
          reducer.apply(
              state,
              envelope(
                  MessageType.ONLINE_LIST,
                  null,
                  null,
                  null,
                  null,
                  json.createObjectNode().put("revision", 1).set("users", online)),
              System.nanoTime());
      var store = new ClientStore();
      var network = new NetworkClient(e -> {}, r -> {});
      var shell = new AppShell(store, network, sink);
      cleanup.add(shell);
      cleanup.add(network);
      cleanup.add(store);
      shell.render(state);
      add("03-lobby", shell);
      var selectedLobby = new LobbyView(state, sink, q -> {});
      selectedLobby.restore(new LobbyView.Selection(1, 102));
      add("lobby-selected", selectedLobby);
      var emptySearch = new LobbyView(state, sink, q -> {});
      var library = (VBox) ((ScrollPane) emptySearch.getCenter()).getContent();
      ((TextField)
              library.getChildren().stream()
                  .filter(TextField.class::isInstance)
                  .findFirst()
                  .orElseThrow())
          .setText("Không tìm thấy");
      add("lobby-search-empty", emptySearch);
      var detail = new QuizDetailView(quizzes.get(0), sink);
      detail.render(
          json.createObjectNode()
              .put(
                  "description",
                  "Khám phá kiến thức về thế giới, văn hóa và khoa học qua các câu hỏi thú vị.")
              .set(
                  "typeCounts",
                  json.createObjectNode()
                      .put("SINGLE_CHOICE", 4)
                      .put("MULTIPLE_CHOICE", 2)
                      .put("TRUE_FALSE", 2)
                      .put("SHORT_ANSWER", 2)));
      add("04-quiz-detail", beside(detail, new LobbyView(state, sink, q -> {})));
      ObjectNode invitation =
          json.createObjectNode()
              .put("challengeId", UUID.randomUUID().toString())
              .put("expiresAtMs", System.currentTimeMillis() + 18000)
              .set("quiz", quizzes.get(0));
      invitation.set("challengerProfile", profile);
      for (boolean incoming : List.of(true, false)) {
        var challenge = new ChallengeDialog(invitation, incoming, sink);
        challenge.tick(System.currentTimeMillis());
        add(
            incoming ? "05-invitation" : "06-outgoing",
            beside(challenge, new LobbyView(state, sink, q -> {})));
      }
      var expired = new ChallengeDialog(invitation, true, sink);
      expired.tick(System.currentTimeMillis() + 20000);
      add("07-expired", new HBox(expired));
      JsonNode fixture =
          json.readTree(getClass().getResourceAsStream("/protocol/demo-match-v1.json"));
      var captured = new HashSet<String>();
      ClientState open = null;
      for (JsonNode entry : fixture.path("events")) {
        if (entry.hasNonNull("participantUserId")
            && entry.path("participantUserId").asLong() != 101) continue;
        var event = codec.decode(json.writeValueAsBytes(entry.path("envelope")));
        state = reducer.apply(state, event, System.nanoTime());
        String key =
            state.phase()
                + (state.question() == null
                    ? ""
                    : "-" + state.question().path("questionType").asText());
        if (state.phase().equals("OPEN") && open == null) open = state;
        if (!captured.add(key)) continue;
        if (state.phase().equals("WAITING_READY"))
          add("08-ready", Ui.scroll(new WaitingView(state, sink)));
        else if (state.phase().equals("RESULT"))
          add("16-result", Ui.scroll(new ResultView(state, sink)));
        else {
          var game = new GameView(state, sink, true);
          cleanup.add(game::dispose);
          add("game-" + key.toLowerCase(), game);
        }
      }
      if (open != null) {
        ObjectNode pictureQuestion = open.question().deepCopy();
        pictureQuestion.put("content", "Quan sát hình ảnh. Thủ đô của Việt Nam là gì?");
        pictureQuestion.put("questionAssetId", "question-sample");
        var imageState =
            new ClientState(
                open.connectionEpoch(),
                open.profile(),
                open.onlineRevision(),
                open.activeMatchId(),
                open.resultMatchId(),
                open.roundId(),
                open.lastSeqByMatch(),
                open.phase(),
                open.presentationVersion(),
                pictureQuestion,
                false,
                false,
                open.data(),
                open.persistenceByMatch(),
                Map.of(),
                "",
                System.nanoTime());
        var withImage = new GameView(imageState, sink, true);
        cleanup.add(withImage::dispose);
        add("game-question-image", withImage);
        var messages = new LinkedHashMap<UUID, JsonNode>();
        messages.put(
            UUID.randomUUID(),
            json.createObjectNode().put("displayName", "Tuấn").put("text", "Câu này thú vị quá!"));
        messages.put(
            UUID.randomUUID(),
            json.createObjectNode().put("displayName", "Minh").put("text", "Cùng cố gắng nhé."));
        var chatState =
            new ClientState(
                open.connectionEpoch(),
                open.profile(),
                open.onlineRevision(),
                open.activeMatchId(),
                open.resultMatchId(),
                open.roundId(),
                open.lastSeqByMatch(),
                open.phase(),
                open.presentationVersion(),
                pictureQuestion,
                false,
                false,
                open.data(),
                open.persistenceByMatch(),
                messages,
                "",
                System.nanoTime());
        add("game-shell-with-chat", shell(chatState, true));
        ObjectNode longQuestion = pictureQuestion.deepCopy();
        longQuestion.put(
            "content",
            "Câu hỏi dài để kiểm tra khả năng đọc và cuộn nội dung. ".repeat(10).substring(0, 500));
        var longOptions = json.createArrayNode();
        for (int i = 0; i < 6; i++)
          longOptions
              .addObject()
              .put("id", "" + (char) ('A' + i))
              .put(
                  "text",
                  "Nội dung lựa chọn dài, người chơi vẫn cần đọc đầy đủ và thao tác bằng bàn phím. "
                      .repeat(2)
                      .substring(0, 120));
        longQuestion.set("options", longOptions);
        var longState =
            new ClientState(
                open.connectionEpoch(),
                open.profile(),
                open.onlineRevision(),
                open.activeMatchId(),
                open.resultMatchId(),
                open.roundId(),
                open.lastSeqByMatch(),
                open.phase(),
                open.presentationVersion(),
                longQuestion,
                false,
                false,
                open.data(),
                open.persistenceByMatch(),
                messages,
                "",
                System.nanoTime());
        add("game-long-question", shell(longState, true));
      }
      add("result-shell", shell(state, false));
      var invalidLogin = new AuthView(sink, (h, p) -> {});
      invalidLogin.connected(true);
      var primaryLogin = findButtons(invalidLogin, "Đăng nhập");
      primaryLogin.getLast().fire();
      add("auth-validation", invalidLogin);
      var disconnected = new AuthView(sink, (h, p) -> {});
      add("auth-disconnected", disconnected);
      var rankEntries = json.createArrayNode();
      for (int i = 1; i <= 7; i++)
        rankEntries
            .addObject()
            .put("rank", i)
            .put("userId", 100 + i)
            .put("displayName", i == 1 ? "Minh" : "Người chơi " + i)
            .put("avatarId", "avatar-%02d".formatted(i))
            .put("totalScore", 8500 - i * 280)
            .put("totalMatches", 210 - i * 10)
            .put("wins", 150 - i * 5);
      ObjectNode ranks =
          json.createObjectNode()
              .put("page", 1)
              .put("pageSize", 20)
              .put("totalItems", 7)
              .set("entries", rankEntries);
      ranks.set("myRank", rankEntries.get(0));
      add("17-ranking", Ui.scroll(new RankingView(ranks, sink)));
      add(
          "ranking-empty",
          Ui.scroll(
              new RankingView(
                  json.createObjectNode().set("entries", json.createArrayNode()), sink)));
      add("ranking-loading", new RankingView(null, sink));
      var matches = json.createArrayNode();
      for (int i = 0; i < 5; i++)
        matches
            .addObject()
            .put("matchId", UUID.randomUUID().toString())
            .put("quizTitle", "Kiến thức chung")
            .put("opponentName", "Tuấn")
            .put("opponentUserId", 102)
            .put("ownScore", 24)
            .put("opponentScore", 18)
            .put("outcome", i % 2 == 0 ? "WIN" : "LOSS")
            .put("ranked", i % 2 == 0)
            .put("totalRounds", 10)
            .put("endedAtMs", 1791570000000L - i * 3600000L);
      var history =
          json.createObjectNode()
              .put("page", 1)
              .put("pageSize", 10)
              .put("totalItems", 5)
              .set("items", matches);
      add("18-history", Ui.scroll(new HistoryView(history, null, sink)));
      add(
          "history-empty",
          new HistoryView(
              json.createObjectNode().set("items", json.createArrayNode()), null, sink));
      var result = state.payload(MessageType.MATCH_RESULT);
      if (result != null)
        add(
            "19-history-review",
            Ui.scroll(
                new HistoryView(
                    history,
                    json.createObjectNode()
                        .put("page", 1)
                        .put("totalItems", 10)
                        .<ObjectNode>set("summary", matches.get(0))
                        .set("review", result.path("review")),
                    sink)));
      var readyData = new EnumMap<MessageType, JsonNode>(MessageType.class);
      readyData.putAll(state.data());
      readyData.put(
          MessageType.READY_STATUS,
          json.createObjectNode().set("readyUserIds", json.createArrayNode().add(101)));
      var oneReady =
          new ClientState(
              state.connectionEpoch(),
              state.profile(),
              state.onlineRevision(),
              state.activeMatchId(),
              state.resultMatchId(),
              state.roundId(),
              state.lastSeqByMatch(),
              "WAITING_READY",
              state.presentationVersion(),
              null,
              false,
              false,
              readyData,
              state.persistenceByMatch(),
              Map.of(),
              "",
              System.nanoTime());
      add("ready-one-player", Ui.scroll(new WaitingView(oneReady, sink)));
      var chat = new ChatPanel(t -> {});
      add("20-chat", new HBox(new LobbyView(state.lobby(), sink, q -> {}), chat));
      var requests = new ArrayList<UUID>();
      var editor =
          new CommunityQuizView(
              (t, m, r, p) -> {
                var id = UUID.randomUUID();
                requests.add(id);
                return id;
              },
              categories);
      editor.onEvent(
          envelope(
              MessageType.AUTHOR_RESULT,
              requests.getLast(),
              null,
              null,
              null,
              json.createObjectNode()
                  .put("action", "LIST")
                  .set(
                      "data",
                      json.createObjectNode()
                          .put("page", 1)
                          .put("total", 0)
                          .set("quizzes", json.createArrayNode()))));
      add("21-my-quizzes", editor);
      for (String kind :
          List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER")) {
        var draft = new CommunityQuizView(sink, categories);
        draft.timeout();
        set(draft, "quizId", 1L);
        set(draft, "count", 1);
        ((TextField) get(draft, "title")).setText("Khám phá Việt Nam");
        ((ComboBox<?>) get(draft, "category")).getSelectionModel().select(0);
        var edit = CommunityQuizView.class.getDeclaredMethod("editor");
        edit.setAccessible(true);
        edit.invoke(draft);
        var fill = CommunityQuizView.class.getDeclaredMethod("fillQuestion", JsonNode.class);
        fill.setAccessible(true);
        var question =
            json.createObjectNode()
                .put("questionType", kind)
                .put("content", "Thủ đô của Việt Nam là gì?")
                .put("explanation", "Hà Nội là thủ đô của Việt Nam.");
        question.set(
            "options",
            json.createArrayNode()
                .add(json.createObjectNode().put("id", "A").put("text", "Hà Nội"))
                .add(json.createObjectNode().put("id", "B").put("text", "Huế")));
        question.set(
            "answerKey",
            kind.equals("TRUE_FALSE")
                ? BooleanNode.TRUE
                : kind.equals("MULTIPLE_CHOICE")
                    ? json.createArrayNode().add("A")
                    : kind.equals("SHORT_ANSWER")
                        ? json.createArrayNode().add("Hà Nội")
                        : TextNode.valueOf("A"));
        fill.invoke(draft, question);
        add("editor-" + kind.toLowerCase(), draft);
        // Keep metadata in view and a separate scrolled question-form capture.
        add("question-editor-" + kind.toLowerCase(), draft);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private BorderPane beside(javafx.scene.Node left, javafx.scene.Node center) {
    var pane = new BorderPane();
    pane.setLeft(left);
    pane.setCenter(center);
    return pane;
  }

  private AppShell shell(ClientState state, boolean showChat) throws Exception {
    var store = new ClientStore();
    set(store, "state", state);
    var network = new NetworkClient(e -> {}, r -> {});
    var shell = new AppShell(store, network, sink);
    set(shell, "showChat", showChat);
    cleanup.add(shell);
    cleanup.add(network);
    cleanup.add(store);
    shell.render(state);
    return shell;
  }

  private List<Button> findButtons(Parent root, String name) {
    var found = new ArrayList<Button>();
    if (root instanceof ScrollPane scroll && scroll.getContent() instanceof Parent p)
      return findButtons(p, name);
    for (var node : root.getChildrenUnmodifiable()) {
      if (node instanceof Button b && b.getText().equals(name)) found.add(b);
      else if (node instanceof Parent p) found.addAll(findButtons(p, name));
    }
    return found;
  }

  private Envelope envelope(
      MessageType type, UUID request, UUID match, UUID round, Long seq, JsonNode payload) {
    return new Envelope(1, type, request, match, round, seq, payload);
  }

  private Object get(Object o, String name) throws Exception {
    var f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  private void set(Object o, String name, Object value) throws Exception {
    var f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, value);
  }

  private Button findButton(Parent p, String name) {
    if (p instanceof ScrollPane scroll && scroll.getContent() instanceof Parent content)
      return findButton(content, name);
    if (p instanceof TitledPane titled && titled.getContent() instanceof Parent content)
      return findButton(content, name);
    for (var n : p.getChildrenUnmodifiable()) {
      if (n instanceof Button b && b.getText().equals(name)) return b;
      if (n instanceof Parent child) {
        var b = findButton(child, name);
        if (b != null) return b;
      }
    }
    return null;
  }

  private void snapshot(String name, int width, int height) {
    try {
      var root = scene.getRoot();
      root.applyCss();
      root.layout();
      for (var node : root.lookupAll(".scroll-pane"))
        if (node instanceof ScrollPane scroll)
          scroll.setVvalue(name.startsWith("question-editor-") ? 1 : 0);
      root.layout();
      if (root instanceof AppShell app && app.getTop() instanceof HBox header) {
        for (var node : header.getChildren())
          if (node instanceof Button b) {
            assertTrue(
                b.getBoundsInParent().getMaxX() <= root.getLayoutBounds().getWidth(),
                "Navigation clipped: " + b.getText());
          }
      }
      var shot = root.snapshot(null, null);
      var image =
          new java.awt.image.BufferedImage(
              (int) shot.getWidth(),
              (int) shot.getHeight(),
              java.awt.image.BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < image.getHeight(); y++)
        for (int x = 0; x < image.getWidth(); x++)
          image.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
      Path file =
          Path.of(System.getProperty("quiz.uiEvidence"), width + "x" + height, name + ".png");
      Files.createDirectories(file.getParent());
      javax.imageio.ImageIO.write(image, "png", file.toFile());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
