package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.common.protocol.*;

class NativeViewsTest {
  @BeforeAll
  static void toolkit() throws Exception {
    var latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    Platform.setImplicitExit(false);
  }

  private void fx(Runnable work) throws Exception {
    var latch = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    Platform.runLater(
        () -> {
          try {
            work.run();
          } catch (Throwable t) {
            failure.set(t);
          } finally {
            latch.countDown();
          }
        });
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    if (failure.get() != null) throw new AssertionError(failure.get());
  }

  @Test
  void communityEditorSerializesAllFourAnswerTypesAndPreservesOnError() throws Exception {
    fx(
        () -> {
          var requests = new ArrayList<UUID>();
          var view =
              new CommunityQuizView(
                  (t, m, r, p) -> {
                    var id = UUID.randomUUID();
                    requests.add(id);
                    return id;
                  },
                  null);
          try {
            var typeField = CommunityQuizView.class.getDeclaredField("type");
            typeField.setAccessible(true);
            var answerField = CommunityQuizView.class.getDeclaredField("answer");
            answerField.setAccessible(true);
            var contentField = CommunityQuizView.class.getDeclaredField("content");
            contentField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var type = (ComboBox<String>) typeField.get(view);
            var answer = (TextArea) answerField.get(view);
            var content = (TextArea) contentField.get(view);
            content.setText("Nội dung đang nhập");
            type.setValue("SINGLE_CHOICE");
            answer.setText("A");
            assertEquals("A", view.question().path("answerKey").asText());
            type.setValue("MULTIPLE_CHOICE");
            answer.setText("A,B");
            assertEquals(2, view.question().path("answerKey").size());
            type.setValue("TRUE_FALSE");
            answer.setText("false");
            assertTrue(view.question().path("answerKey").isBoolean());
            assertFalse(view.question().path("answerKey").asBoolean());
            type.setValue("SHORT_ANSWER");
            answer.setText("Hà Nội\nHa Noi");
            assertEquals(2, view.question().path("answerKey").size());
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            view.onEvent(
                new Envelope(
                    2,
                    MessageType.ERROR,
                    requests.getFirst(),
                    null,
                    null,
                    null,
                    json.createObjectNode().put("message", "Lỗi lưu")));
            assertEquals("Nội dung đang nhập", content.getText());
            assertEquals("Hà Nội\nHa Noi", answer.getText());
          } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
          }
        });
  }

  @Test
  void communityEditorIgnoresTimedOutResponsesAndAcceptsFreshRetry() throws Exception {
    fx(
        () -> {
          var requests = new ArrayList<UUID>();
          var view =
              new CommunityQuizView(
                  (t, m, r, p) -> {
                    var id = UUID.randomUUID();
                    requests.add(id);
                    return id;
                  },
                  null);
          var json = new com.fasterxml.jackson.databind.ObjectMapper();
          try {
            var request =
                CommunityQuizView.class.getDeclaredMethod("request", String.class, JsonNode.class);
            request.setAccessible(true);
            var titleField = CommunityQuizView.class.getDeclaredField("title");
            titleField.setAccessible(true);
            var title = (TextField) titleField.get(view);
            var formField = CommunityQuizView.class.getDeclaredField("form");
            formField.setAccessible(true);
            var form = (VBox) formField.get(view);
            view.timeout(); // Initial list is no longer allowed to update this editor.
            request.invoke(view, "CREATE", json.createObjectNode());
            UUID expired = requests.getLast();
            assertTrue(title.isDisabled());
            view.timeout();
            title.setText("Nội dung giữ lại");
            request.invoke(view, "CREATE", json.createObjectNode());
            UUID fresh = requests.getLast();
            view.onEvent(
                new Envelope(
                    2,
                    MessageType.AUTHOR_RESULT,
                    expired,
                    null,
                    null,
                    null,
                    json.createObjectNode()
                        .put("action", "CREATE")
                        .set(
                            "data",
                            json.createObjectNode()
                                .put("quizId", 17)
                                .put("title", "Phản hồi cũ"))));
            assertEquals("Nội dung giữ lại", title.getText());
            assertTrue(title.isDisabled()); // Old response must not release the new request's lock.
            view.onEvent(
                new Envelope(
                    2,
                    MessageType.AUTHOR_RESULT,
                    fresh,
                    null,
                    null,
                    null,
                    json.createObjectNode()
                        .put("action", "CREATE")
                        .set(
                            "data",
                            json.createObjectNode().put("quizId", 18).put("title", "Quiz mới"))));
            assertEquals("Quiz mới", title.getText());
            assertFalse(title.isDisabled());
            request.invoke(view, "GET_QUESTION", json.createObjectNode().put("index", 0));
            UUID oldQuestion = requests.getLast();
            assertTrue(form.isDisabled());
            view.timeout();
            assertFalse(form.isDisabled());
            var contentField = CommunityQuizView.class.getDeclaredField("content");
            contentField.setAccessible(true);
            var content = (TextArea) contentField.get(view);
            content.setText("Đang soạn sau timeout");
            view.onEvent(
                new Envelope(
                    2,
                    MessageType.AUTHOR_RESULT,
                    oldQuestion,
                    null,
                    null,
                    null,
                    json.createObjectNode()
                        .put("action", "GET_QUESTION")
                        .set(
                            "data",
                            json.createObjectNode()
                                .put("index", 0)
                                .set(
                                    "question",
                                    json.createObjectNode().put("content", "Câu cũ")))));
            assertEquals("Đang soạn sau timeout", content.getText());
          } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
          }
        });
  }

  @Test
  void lobbyPagerPreservesCategoryAndDisablesUnavailablePages() throws Exception {
    fx(
        () -> {
          var json = new com.fasterxml.jackson.databind.ObjectMapper();
          var payload =
              json.createObjectNode().put("page", 2).put("pageSize", 20).put("totalItems", 45);
          payload.set("items", json.createArrayNode());
          payload.set("categories", json.createArrayNode());
          var state =
              new vn.edu.nhom7.quiz.client.state.ClientStateReducer()
                  .apply(
                      vn.edu.nhom7.quiz.client.state.ClientState.initial(),
                      new Envelope(2, MessageType.QUIZ_LIST, null, null, null, null, payload),
                      0);
          var sent = new ArrayList<Payloads.QuizListRequest>();
          UiCommandSink sink =
              (type, m, r, p) -> {
                if (type == MessageType.QUIZ_LIST_REQUEST) sent.add((Payloads.QuizListRequest) p);
                return UUID.randomUUID();
              };
          var lobby = new LobbyView(state, sink, q -> {}, 9L);
          var library = (VBox) ((ScrollPane) lobby.getCenter()).getContent();
          var pager = (HBox) library.getChildren().getLast();
          ((Button) pager.getChildren().getFirst()).fire();
          ((Button) pager.getChildren().getLast()).fire();
          assertEquals(new Payloads.QuizListRequest(9L, 1, 20), sent.get(0));
          assertEquals(new Payloads.QuizListRequest(9L, 3, 20), sent.get(1));
          payload.put("page", 3);
          state =
              new vn.edu.nhom7.quiz.client.state.ClientStateReducer()
                  .apply(
                      state,
                      new Envelope(2, MessageType.QUIZ_LIST, null, null, null, null, payload),
                      0);
          lobby = new LobbyView(state, sink, q -> {}, 9L);
          library = (VBox) ((ScrollPane) lobby.getCenter()).getContent();
          pager = (HBox) library.getChildren().getLast();
          assertTrue(((Button) pager.getChildren().getLast()).isDisabled());
        });
  }

  @Test
  void shellRefreshesPublishedQuizzesAndWaitsForCategoriesBeforeEditorEntry() throws Exception {
    fx(
        () -> {
          var store = new vn.edu.nhom7.quiz.client.state.ClientStore();
          var network = new vn.edu.nhom7.quiz.client.network.NetworkClient(e -> {}, reason -> {});
          var requests = new ArrayList<Payloads.QuizListRequest>();
          var shell =
              new AppShell(
                  store,
                  network,
                  (type, m, r, p) -> {
                    if (type == MessageType.QUIZ_LIST_REQUEST)
                      requests.add((Payloads.QuizListRequest) p);
                    return UUID.randomUUID();
                  });
          try {
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            var reducer = new vn.edu.nhom7.quiz.client.state.ClientStateReducer();
            var login =
                json.createObjectNode()
                    .set(
                        "profile",
                        json.createObjectNode().put("userId", 1).put("displayName", "Tác giả"));
            var state =
                reducer.apply(
                    vn.edu.nhom7.quiz.client.state.ClientState.initial(),
                    new Envelope(2, MessageType.LOGIN_RESULT, null, null, null, null, login),
                    0);
            shell.render(state);
            assertTrue(headerButton(shell, "Quiz của tôi").isDisabled());
            var list =
                json.createObjectNode().put("page", 1).put("pageSize", 20).put("totalItems", 0);
            list.set("items", json.createArrayNode());
            list.set(
                "categories",
                json.createArrayNode()
                    .add(
                        json.createObjectNode()
                            .put("categoryId", 9)
                            .put("categoryName", "Khoa học")));
            state =
                reducer.apply(
                    state, new Envelope(2, MessageType.QUIZ_LIST, null, null, null, null, list), 0);
            shell.render(state);
            assertFalse(headerButton(shell, "Quiz của tôi").isDisabled());
            var lobby = (LobbyView) shell.getCenter();
            var library = (VBox) ((ScrollPane) lobby.getCenter()).getContent();
            ((Button)
                    ((HBox) ((ScrollPane) library.getChildren().get(1)).getContent())
                        .getChildren()
                        .get(1))
                .fire();
            assertEquals(new Payloads.QuizListRequest(9L, 1, 20), requests.getLast());
            for (String action : List.of("PUBLISH", "UNPUBLISH")) {
              shell.onEvent(
                  new Envelope(
                      2,
                      MessageType.AUTHOR_RESULT,
                      UUID.randomUUID(),
                      null,
                      null,
                      null,
                      json.createObjectNode().put("action", action)));
              assertEquals(new Payloads.QuizListRequest(9L, 1, 20), requests.getLast());
            }
            int before = requests.size();
            headerButton(shell, "Sảnh").fire();
            assertEquals(before + 1, requests.size());
            headerButton(shell, "Quiz của tôi").fire();
            var editor = shell.getCenter();
            headerButton(shell, "Sảnh").fire();
            shell.render(state);
            headerButton(shell, "Quiz của tôi").fire();
            assertSame(editor, shell.getCenter());
          } finally {
            shell.close();
            network.close();
            store.close();
          }
        });
  }

  private Button headerButton(AppShell shell, String text) {
    return ((HBox) shell.getTop())
        .getChildren().stream()
            .filter(n -> n instanceof Button b && b.getText().equals(text))
            .map(n -> (Button) n)
            .findFirst()
            .orElseThrow();
  }

  private Payloads.PublicQuestion q(String type) {
    return new Payloads.PublicQuestion(
        1,
        type,
        "Nội dung tiếng Việt 😀",
        List.of(new Payloads.Option("A", "Một"), new Payloads.Option("B", "Hai")),
        null,
        15000);
  }

  @Test
  void singleTapAndKeyboardLockImmediately() throws Exception {
    fx(
        () -> {
          var r = new SingleChoiceRenderer();
          var answers = new ArrayList<JsonNode>();
          r.bind(q("SINGLE_CHOICE"), answers::add);
          r.setControlsEnabled(true);
          r.activateOption('A');
          r.activateOption('B');
          assertEquals(1, answers.size());
          assertEquals("A", answers.getFirst().asText());
        });
  }

  @Test
  void multipleRequiresNonemptySelection() throws Exception {
    fx(
        () -> {
          var r = new MultipleChoiceRenderer();
          var answers = new ArrayList<JsonNode>();
          r.bind(q("MULTIPLE_CHOICE"), answers::add);
          r.setControlsEnabled(true);
          var box = (VBox) r.view();
          var submit = (Button) box.getChildren().getLast();
          assertTrue(submit.isDisabled());
          var first = (CheckBox) box.getChildren().getFirst();
          first.fire();
          assertFalse(submit.isDisabled());
          submit.fire();
          assertEquals(
              List.of("A"),
              new com.fasterxml.jackson.databind.ObjectMapper()
                  .convertValue(answers.getFirst(), List.class));
        });
  }

  @Test
  void trueFalseSendsBoolean() throws Exception {
    fx(
        () -> {
          var r = new TrueFalseRenderer();
          var answers = new ArrayList<JsonNode>();
          r.bind(q("TRUE_FALSE"), answers::add);
          r.setControlsEnabled(true);
          ((Button) r.view().lookup(".answer-tile")).fire();
          assertTrue(answers.getFirst().isBoolean());
        });
  }

  @Test
  void shortAnswerRejectsLongInputAndPreservesRawText() throws Exception {
    fx(
        () -> {
          var r = new ShortAnswerRenderer();
          var answers = new ArrayList<JsonNode>();
          r.bind(q("SHORT_ANSWER"), answers::add);
          r.setControlsEnabled(true);
          var box = (VBox) r.view();
          var field = (TextField) box.getChildren().getFirst();
          field.setText("😀".repeat(121));
          assertTrue(field.getText().isEmpty());
          field.setText("  Hà Nội  ");
          ((Button) box.getChildren().getLast()).fire();
          assertEquals("  Hà Nội  ", answers.getFirst().asText());
        });
  }

  @Test
  void revealWithStreaksStillShowsTheAnswerTilesAndFlame() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var reducer = new vn.edu.nhom7.quiz.client.state.ClientStateReducer();
    UUID match = UUID.randomUUID();
    var state = vn.edu.nhom7.quiz.client.state.ClientState.initial();
    var profile = json.createObjectNode().put("userId", 101).put("displayName", "Minh");
    state =
        reducer.apply(
            state,
            new Envelope(
                1,
                MessageType.LOGIN_RESULT,
                null,
                null,
                null,
                null,
                json.createObjectNode().set("profile", profile)),
            0);
    var start = json.createObjectNode().put("totalRounds", 10);
    start
        .putArray("players")
        .add(profile)
        .addObject()
        .put("userId", 102)
        .put("displayName", "Tuấn");
    state =
        reducer.apply(
            state, new Envelope(1, MessageType.MATCH_START, null, match, null, 1L, start), 0);
    long seq = 2;
    for (int q = 1; q <= 3; q++) {
      UUID round = UUID.randomUUID();
      var question = json.createObjectNode();
      question
          .putObject("question")
          .put("questionId", q)
          .put("questionType", "SINGLE_CHOICE")
          .put("content", "Thủ đô Việt Nam?")
          .put("timeLimitMs", 15000)
          .putArray("options")
          .add(json.createObjectNode().put("id", "A").put("text", "Hà Nội"))
          .add(json.createObjectNode().put("id", "B").put("text", "Huế"));
      state =
          reducer.apply(
              state, new Envelope(1, MessageType.QUESTION, null, match, round, seq++, question), 0);
      var result = json.createObjectNode().put("questionId", q).put("correctAnswer", "A");
      result
          .putArray("outcomes")
          .addObject()
          .put("userId", 101)
          .put("answer", "A")
          .put("correct", true)
          .put("earnedPoints", 3);
      state =
          reducer.apply(
              state,
              new Envelope(1, MessageType.QUESTION_RESULT, null, match, round, seq++, result),
              0);
    }
    var reveal = state;
    assertEquals("REVEAL", reveal.phase());
    fx(
        () -> {
          var view = new GameView(reveal, (t, m, r, p) -> UUID.randomUUID(), true);
          new javafx.scene.Scene(view, 1280, 800);
          view.applyCss();
          view.layout();
          assertNotNull(view.lookup(".answer-tile.correct"));
          assertNotNull(view.lookup(".streak-badge.tier-1"));
          view.dispose();
        });
  }

  @Test
  void lifelinesSendOnceGreyOutRemovedOptionsAndShowTheOpponentsUse() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var reducer = new vn.edu.nhom7.quiz.client.state.ClientStateReducer();
    UUID match = UUID.randomUUID(), round = UUID.randomUUID();
    var profile = json.createObjectNode().put("userId", 101).put("displayName", "Minh");
    var start = json.createObjectNode().put("totalRounds", 10);
    start
        .putArray("players")
        .add(profile)
        .addObject()
        .put("userId", 102)
        .put("displayName", "Tuấn");
    var question = json.createObjectNode();
    var options =
        question
            .putObject("question")
            .put("questionId", 7)
            .put("questionType", "SINGLE_CHOICE")
            .put("content", "Sông nào dài nhất Việt Nam?")
            .put("timeLimitMs", 15000)
            .putArray("options");
    for (String id : List.of("A", "B", "C", "D"))
      options.addObject().put("id", id).put("text", "Sông " + id);
    var state = vn.edu.nhom7.quiz.client.state.ClientState.initial();
    long seq = 1;
    for (var e :
        List.of(
            new Envelope(
                1,
                MessageType.LOGIN_RESULT,
                null,
                null,
                null,
                null,
                json.createObjectNode().set("profile", profile)),
            new Envelope(1, MessageType.MATCH_START, null, match, null, seq++, start),
            new Envelope(1, MessageType.QUESTION, null, match, round, seq++, question),
            new Envelope(
                1,
                MessageType.QUESTION_OPEN,
                null,
                match,
                round,
                seq++,
                json.createObjectNode().put("questionId", 7).put("remainingMs", 15000))))
      state = reducer.apply(state, e, 0);
    var open = state;
    var sent = new ArrayList<Object>();
    UiCommandSink sink =
        (t, m, r, p) -> {
          if (t == MessageType.LIFELINE_USE) sent.add(p);
          return UUID.randomUUID();
        };
    var result = json.createObjectNode().put("questionId", 7).put("kind", "FIFTY_FIFTY");
    result.putArray("removedOptionIds").add("B").add("D");
    result.putArray("remaining").add("DOUBLE_POINTS").add("REMOVE_ONE");
    var used =
        reducer.apply(
            open,
            new Envelope(1, MessageType.LIFELINE_RESULT, null, match, round, seq++, result),
            0);
    for (long user : List.of(102L, 101L))
      used =
          reducer.apply(
              used,
              new Envelope(
                  1,
                  MessageType.LIFELINE_STATUS,
                  null,
                  match,
                  round,
                  seq++,
                  json.createObjectNode()
                      .put("userId", user)
                      .put("questionId", 7)
                      .put("kind", user == 102 ? "DOUBLE_POINTS" : "FIFTY_FIFTY")),
              0);
    var after = used;
    fx(
        () -> {
          var view = new GameView(open, sink, true);
          new javafx.scene.Scene(view, 1280, 800);
          view.applyCss();
          view.layout();
          var buttons = view.lookupAll(".button.lifeline").stream().toList();
          assertEquals(3, buttons.size());
          assertTrue(buttons.stream().noneMatch(javafx.scene.Node::isDisabled));
          ((Button) buttons.get(2)).fire();
          assertEquals(List.of(new Payloads.LifelineUse(7, "FIFTY_FIFTY")), sent);
          // Waiting for the server: no second lifeline can be sent.
          assertTrue(buttons.stream().allMatch(javafx.scene.Node::isDisabled));
          view.render(after);
          view.applyCss();
          var removed = view.lookupAll(".answer-tile.eliminated");
          assertEquals(2, removed.size());
          assertTrue(removed.stream().allMatch(javafx.scene.Node::isDisabled));
          assertTrue(buttons.get(2).getStyleClass().contains("active"));
          var opponent = (Label) view.lookup(".opponent-lifeline");
          assertTrue(opponent.isVisible());
          assertEquals("dùng x2 điểm", opponent.getText());
          view.dispose();
        });
    var next = json.createObjectNode();
    next.putObject("question")
        .put("questionId", 8)
        .put("questionType", "TRUE_FALSE")
        .put("content", "Hà Nội là thủ đô?")
        .put("timeLimitMs", 15000)
        .putArray("options");
    UUID round2 = UUID.randomUUID();
    var second =
        reducer.apply(
            after, new Envelope(1, MessageType.QUESTION, null, match, round2, seq++, next), 0);
    second =
        reducer.apply(
            second,
            new Envelope(
                1,
                MessageType.QUESTION_OPEN,
                null,
                match,
                round2,
                seq++,
                json.createObjectNode().put("questionId", 8).put("remainingMs", 15000)),
            0);
    var trueFalse = second;
    fx(
        () -> {
          var view = new GameView(trueFalse, sink, true);
          new javafx.scene.Scene(view, 1280, 800);
          view.applyCss();
          view.layout();
          var buttons = view.lookupAll(".button.lifeline").stream().toList();
          assertFalse(buttons.get(0).isDisabled());
          assertTrue(buttons.get(1).getStyleClass().contains("unavailable"));
          assertTrue(buttons.get(2).getStyleClass().contains("used"));
          assertFalse(view.lookup(".opponent-lifeline").isVisible());
          view.dispose();
        });
  }
}
