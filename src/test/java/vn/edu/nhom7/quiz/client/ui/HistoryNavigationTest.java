package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.client.network.NetworkClient;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

class HistoryNavigationTest {
  private static final JsonNodeFactory json = JsonNodeFactory.instance;

  private record Request(UUID id, Payloads.MatchDetailRequest payload) {}

  @BeforeAll
  static void toolkit() throws Exception {
    var started = new CountDownLatch(1);
    try {
      Platform.startup(started::countDown);
    } catch (IllegalStateException already) {
      started.countDown();
    }
    assertTrue(started.await(5, TimeUnit.SECONDS));
    Platform.setImplicitExit(false);
  }

  private static void fx(Runnable work) throws Exception {
    var done = new CountDownLatch(1);
    var error = new AtomicReference<Throwable>();
    Platform.runLater(
        () -> {
          try {
            work.run();
          } catch (Throwable failure) {
            error.set(failure);
          } finally {
            done.countDown();
          }
        });
    assertTrue(done.await(10, TimeUnit.SECONDS));
    if (error.get() != null) throw new AssertionError(error.get());
  }

  @Test
  void clickNavigatesImmediatelyAndLateReplyCannotReplaceAnotherMatch() throws Exception {
    try (var store = new ClientStore();
        var network = new NetworkClient(e -> {}, reason -> {})) {
      var stateReady = new CountDownLatch(2);
      store.subscribe(s -> stateReady.countDown());
      var login = json.objectNode();
      login.putObject("profile").put("userId", 101).put("displayName", "Minh");
      store.accept(event(MessageType.LOGIN_RESULT, null, login));
      var first = summary(UUID.randomUUID());
      var second = summary(UUID.randomUUID());
      second.put("quizTitle", "Trận thứ hai");
      var history = json.objectNode().put("page", 2).put("pageSize", 10).put("totalItems", 12);
      history.putArray("items").add(first).add(second);
      store.accept(event(MessageType.HISTORY, null, history));
      assertTrue(stateReady.await(5, TimeUnit.SECONDS));
      fx(
          () -> {
            var requests = new ArrayList<Request>();
            try (var shell =
                new AppShell(
                    store,
                    network,
                    (type, match, round, payload) -> {
                      UUID id = UUID.randomUUID();
                      if (type == MessageType.MATCH_DETAIL_REQUEST)
                        requests.add(new Request(id, (Payloads.MatchDetailRequest) payload));
                      return id;
                    })) {
              var scene = new Scene(shell, 1280, 800);
              Theme.attach(scene);
              shell.render(store.state());
              button(shell, "Lịch sử").fire();
              var list = (HistoryView) ((ScrollPane) shell.getCenter()).getContent();
              button((Parent) list.getChildren().get(3), "Xem chi tiết").fire();
              var firstPage = assertInstanceOf(MatchHistoryDetailView.class, shell.getCenter());
              assertEquals(
                  UUID.fromString(first.path("matchId").asText()),
                  requests.getFirst().payload().historyMatchId());
              assertTrue(texts(firstPage).contains("Đang tải câu hỏi…"));
              button(firstPage, "← Lịch sử trận đấu").fire();
              list = (HistoryView) ((ScrollPane) shell.getCenter()).getContent();
              assertTrue(texts(list).contains("Trang 2"));
              button((Parent) list.getChildren().get(4), "Xem chi tiết").fire();
              var secondPage = assertInstanceOf(MatchHistoryDetailView.class, shell.getCenter());
              shell.onEvent(
                  event(MessageType.MATCH_DETAIL, requests.getFirst().id(), detail(first, 1, 2)));
              assertSame(secondPage, shell.getCenter());
              assertTrue(secondPage.lookupAll(".review-card").isEmpty());
              shell.onEvent(
                  event(MessageType.MATCH_DETAIL, requests.getLast().id(), detail(second, 1, 2)));
              // Store-driven renders must preserve the dedicated page and its loaded cards.
              shell.render(store.state());
              shell.applyCss();
              shell.layout();
              assertSame(secondPage, shell.getCenter());
              assertEquals(2, secondPage.lookupAll(".review-card").size());
              assertTrue(texts(secondPage).contains("Đã tải toàn bộ 2 câu hỏi"));
              assertTrue(button(shell, "Lịch sử").getStyleClass().contains("active"));
            }
          });
    }
  }

  @Test
  void fiftyQuestionsAccumulateInOrderWithBothAnswersExpanded() throws Exception {
    var requests = new LinkedBlockingQueue<Request>();
    var view = new AtomicReference<MatchHistoryDetailView>();
    var summary = summary(UUID.randomUUID()).put("totalRounds", 50);
    fx(
        () -> {
          view.set(
              new MatchHistoryDetailView(
                  summary,
                  101,
                  (type, match, round, payload) -> {
                    UUID id = UUID.randomUUID();
                    requests.add(new Request(id, (Payloads.MatchDetailRequest) payload));
                    return id;
                  },
                  () -> {}));
          new Scene(view.get(), 1024, 720);
          view.get().load();
        });
    try {
      for (int page = 1; page <= 25; page++) {
        Request request = requests.poll(3, TimeUnit.SECONDS);
        assertNotNull(request, "Missing review page " + page);
        assertEquals(page, request.payload().page());
        var response = detail(summary, page, 50);
        fx(() -> view.get().onEvent(event(MessageType.MATCH_DETAIL, request.id(), response)));
      }
      fx(
          () -> {
            view.get().applyCss();
            view.get().layout();
            var cards =
                ((javafx.scene.layout.VBox) ((ScrollPane) view.get().getCenter()).getContent())
                    .getChildren();
            assertEquals(50, cards.size());
            for (int index = 0; index < 50; index++) {
              var card = (ReviewCard) cards.get(index);
              assertTrue(card.expanded());
              assertTrue(texts(card).contains("Câu " + (index + 1)));
              assertTrue(texts(card).stream().anyMatch(t -> t.startsWith("Bạn · Hà Nội")));
              assertTrue(texts(card).stream().anyMatch(t -> t.startsWith("Tuấn · Huế")));
            }
            assertTrue(texts(view.get()).contains("Đã tải toàn bộ 50 câu hỏi"));
            button(view.get(), "Thu gọn tất cả").fire();
            assertTrue(cards.stream().map(n -> (ReviewCard) n).noneMatch(ReviewCard::expanded));
          });
      assertTrue(requests.isEmpty(), "No request after the last review page");
    } finally {
      fx(() -> view.get().close());
    }
  }

  @Test
  void timeoutRetryIgnoresOldResponseAndDuplicatePages() throws Exception {
    fx(
        () -> {
          var requests = new ArrayList<Request>();
          var summary = summary(UUID.randomUUID());
          try (var view =
              new MatchHistoryDetailView(
                  summary,
                  101,
                  (type, match, round, payload) -> {
                    UUID id = UUID.randomUUID();
                    requests.add(new Request(id, (Payloads.MatchDetailRequest) payload));
                    return id;
                  },
                  () -> {})) {
            new Scene(view, 1024, 720);
            view.load();
            UUID old = requests.getLast().id();
            view.timeout();
            button(view, "Thử tải lại").fire();
            UUID retry = requests.getLast().id();
            view.onEvent(event(MessageType.MATCH_DETAIL, old, detail(summary, 1, 2)));
            assertTrue(view.lookupAll(".review-card").isEmpty());
            view.onEvent(event(MessageType.MATCH_DETAIL, retry, detail(summary, 1, 2)));
            view.applyCss();
            view.layout();
            assertEquals(2, view.lookupAll(".review-card").size());
            view.onEvent(event(MessageType.MATCH_DETAIL, retry, detail(summary, 1, 2)));
            assertEquals(2, view.lookupAll(".review-card").size(), "Ignore duplicate responses");
          }
          try (var view =
              new MatchHistoryDetailView(summary, 101, (t, m, r, p) -> null, () -> {})) {
            view.load();
            assertTrue(button(view, "Thử tải lại").isVisible());
          }
        });
  }

  @Test
  void retryContinuesFromFailedPageAndKeepsPreviouslyLoadedAnswers() throws Exception {
    var requests = new LinkedBlockingQueue<Request>();
    var summary = summary(UUID.randomUUID()).put("totalRounds", 4);
    var view = new AtomicReference<MatchHistoryDetailView>();
    fx(
        () -> {
          view.set(
              new MatchHistoryDetailView(
                  summary,
                  101,
                  (type, match, round, payload) -> {
                    UUID id = UUID.randomUUID();
                    requests.add(new Request(id, (Payloads.MatchDetailRequest) payload));
                    return id;
                  },
                  () -> {}));
          new Scene(view.get(), 1024, 720);
          view.get().load();
        });
    try {
      Request first = requests.poll(3, TimeUnit.SECONDS);
      assertNotNull(first);
      fx(
          () ->
              view.get()
                  .onEvent(event(MessageType.MATCH_DETAIL, first.id(), detail(summary, 1, 4))));
      Request second = requests.poll(3, TimeUnit.SECONDS);
      assertNotNull(second);
      fx(
          () -> {
            view.get()
                .onEvent(
                    event(
                        MessageType.ERROR,
                        second.id(),
                        json.objectNode().put("message", "Lỗi đọc dữ liệu")));
            view.get().applyCss();
            view.get().layout();
            assertEquals(2, view.get().lookupAll(".review-card").size());
            button(view.get(), "Thử tải lại").fire();
          });
      Request retry = requests.poll(3, TimeUnit.SECONDS);
      assertNotNull(retry);
      assertEquals(2, retry.payload().page());
      fx(
          () -> {
            view.get().onEvent(event(MessageType.MATCH_DETAIL, second.id(), detail(summary, 2, 4)));
            assertEquals(2, view.get().lookupAll(".review-card").size());
            view.get().onEvent(event(MessageType.MATCH_DETAIL, retry.id(), detail(summary, 2, 4)));
            view.get().applyCss();
            view.get().layout();
            assertEquals(4, view.get().lookupAll(".review-card").size());
          });
    } finally {
      fx(() -> view.get().close());
    }
  }

  @Test
  void emptyAndAbandonedMatchesDoNotInventAnswers() throws Exception {
    fx(
        () -> {
          UUID request = UUID.randomUUID();
          var summary = summary(UUID.randomUUID());
          try (var empty =
              new MatchHistoryDetailView(summary, 101, (t, m, r, p) -> request, () -> {})) {
            new Scene(empty, 1024, 720);
            empty.load();
            empty.onEvent(event(MessageType.MATCH_DETAIL, request, detail(summary, 1, 0)));
            empty.applyCss();
            empty.layout();
            assertTrue(texts(empty).contains("Trận này chưa có câu hỏi được lưu."));
          }
          try (var abandoned =
              new MatchHistoryDetailView(summary, 101, (t, m, r, p) -> request, () -> {})) {
            new Scene(abandoned, 1024, 720);
            abandoned.load();
            var response = detail(summary, 1, 1);
            var review = (ObjectNode) response.path("review").get(0);
            review.put("revealed", false);
            review.remove(List.of("correctAnswer", "explanation"));
            abandoned.onEvent(event(MessageType.MATCH_DETAIL, request, response));
            abandoned.applyCss();
            abandoned.layout();
            assertEquals(1, abandoned.lookupAll(".review-card").size());
            assertTrue(texts(abandoned).contains("Câu chưa được chấm"));
            assertFalse(texts(abandoned).stream().anyMatch(t -> t.startsWith("Đáp án:")));
          }
        });
  }

  private static ObjectNode summary(UUID match) {
    return json.objectNode()
        .put("matchId", match.toString())
        .put("quizTitle", "Kiến thức chung")
        .put("opponentUserId", 102)
        .put("opponentName", "Tuấn")
        .put("ownScore", 24)
        .put("opponentScore", 18)
        .put("outcome", "WIN")
        .put("totalRounds", 10)
        .put("ranked", true)
        .put("reasonCode", "NORMAL");
  }

  private static ObjectNode detail(JsonNode summary, int page, int total) {
    var detail = json.objectNode().put("page", page).put("totalItems", total);
    detail.set("summary", summary);
    var reviews = detail.putArray("review");
    for (int i = (page - 1) * 2 + 1; i <= Math.min(page * 2, total); i++) {
      var review =
          reviews
              .addObject()
              .put("roundId", UUID.randomUUID().toString())
              .put("roundIndex", i)
              .put("revealed", true)
              .put("correctAnswer", "A")
              .put("explanation", "Hà Nội là thủ đô Việt Nam.");
      var question =
          review
              .putObject("question")
              .put("questionId", i)
              .put("questionType", "SINGLE_CHOICE")
              .put("content", "Thủ đô Việt Nam là gì?");
      question
          .putArray("options")
          .add(json.objectNode().put("id", "A").put("text", "Hà Nội"))
          .add(json.objectNode().put("id", "B").put("text", "Huế"));
      review
          .putArray("outcomes")
          .add(
              json.objectNode()
                  .put("userId", 101)
                  .put("answer", "A")
                  .put("outcome", "ANSWERED")
                  .put("correct", true)
                  .put("earnedPoints", 3))
          .add(
              json.objectNode()
                  .put("userId", 102)
                  .put("answer", "B")
                  .put("outcome", "ANSWERED")
                  .put("correct", false)
                  .put("earnedPoints", 0));
    }
    return detail;
  }

  private static Envelope event(MessageType type, UUID request, JsonNode payload) {
    return new Envelope(1, type, request, null, null, null, payload);
  }

  private static List<Button> buttons(Parent view, String text) {
    return view.lookupAll(".button").stream()
        .map(n -> (Button) n)
        .filter(b -> b.getText().equals(text))
        .toList();
  }

  private static Button button(Parent view, String text) {
    return buttons(view, text).getFirst();
  }

  private static List<String> texts(Parent view) {
    return view.lookupAll(".label").stream().map(n -> ((Label) n).getText()).toList();
  }
}
