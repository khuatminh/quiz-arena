package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

class EditorInteractionTest {
  @BeforeAll
  static void toolkit() throws Exception {
    var latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException e) {
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

  private Object field(Object o, String name) throws Exception {
    var f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  @Test
  void choicesPreserveArbitraryIdsAndTextAndSingleSelection() throws Exception {
    fx(
        () -> {
          try {
            var json = new ObjectMapper();
            var view = new CommunityQuizView((t, m, r, p) -> UUID.randomUUID(), null);
            view.timeout();
            var q =
                json.createObjectNode()
                    .put("questionType", "SINGLE_CHOICE")
                    .put("content", "Câu hỏi")
                    .put("answerKey", "A,one|two");
            q.set(
                "options",
                json.createArrayNode()
                    .add(
                        json.createObjectNode()
                            .put("id", "A,one|two")
                            .put("text", "Một | lựa chọn"))
                    .add(json.createObjectNode().put("id", "B").put("text", "Hai")));
            var fill = CommunityQuizView.class.getDeclaredMethod("fillQuestion", JsonNode.class);
            fill.setAccessible(true);
            fill.invoke(view, q);
            assertEquals(q.path("options"), view.question().path("options"));
            assertEquals("A,one|two", view.question().path("answerKey").asText());
            var editor = (VBox) field(view, "answerEditor");
            var rows = (VBox) editor.getChildren().get(2);
            var first = (HBox) rows.getChildren().get(0);
            var second = (HBox) rows.getChildren().get(1);
            ((TextField) first.getChildren().get(1)).setText("Nội dung mới | giữ nguyên");
            ((CheckBox) second.getChildren().get(2)).fire();
            assertFalse(((CheckBox) first.getChildren().get(2)).isSelected());
            assertEquals("B", view.question().path("answerKey").asText());
            assertEquals(
                "Nội dung mới | giữ nguyên",
                view.question().path("options").get(0).path("text").asText());
            @SuppressWarnings("unchecked")
            var type = (ComboBox<String>) field(view, "type");
            type.setValue("MULTIPLE_CHOICE");
            rows = (VBox) editor.getChildren().get(2);
            first = (HBox) rows.getChildren().get(0);
            ((CheckBox) first.getChildren().get(2)).fire();
            var keys = view.question().path("answerKey");
            assertEquals(2, keys.size());
            assertEquals("A,one|two", keys.get(0).asText());
            assertEquals("B", keys.get(1).asText());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void switchingToBooleanClearsIncompatibleAnswerAndUsesVietnameseChoice() throws Exception {
    fx(
        () -> {
          try {
            var view = new CommunityQuizView((t, m, r, p) -> UUID.randomUUID(), null);
            @SuppressWarnings("unchecked")
            var type = (ComboBox<String>) field(view, "type");
            ((TextArea) field(view, "answer")).setText("A");
            type.setValue("TRUE_FALSE");
            assertTrue(view.question().path("answerKey").isNull());
            var editor = (VBox) field(view, "answerEditor");
            @SuppressWarnings("unchecked")
            var value =
                (ComboBox<String>) ((VBox) editor.getChildren().getFirst()).getChildren().get(1);
            value.setValue("Sai");
            value.fireEvent(new javafx.event.ActionEvent());
            assertTrue(view.question().path("answerKey").isBoolean());
            assertFalse(view.question().path("answerKey").asBoolean());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void lobbyKeepsValidSelectionsAndExplainsEmptySearch() throws Exception {
    fx(
        () -> {
          try {
            var json = new ObjectMapper();
            var data = new EnumMap<MessageType, JsonNode>(MessageType.class);
            data.put(
                MessageType.QUIZ_LIST,
                json.createObjectNode()
                    .set(
                        "items",
                        json.createArrayNode()
                            .add(
                                json.createObjectNode()
                                    .put("quizId", 5)
                                    .put("title", "Khoa học")
                                    .put("availability", "AVAILABLE"))));
            data.put(
                MessageType.ONLINE_LIST,
                json.createObjectNode()
                    .set(
                        "users",
                        json.createArrayNode()
                            .add(
                                json.createObjectNode()
                                    .put("userId", 202)
                                    .put("displayName", "Tuấn")
                                    .put("status", "FREE"))));
            var state =
                new ClientState(
                    0, null, 1, null, null, null, Map.of(), "LOBBY", 0, null, false, false, data,
                    Map.of(), Map.of(), "", 0);
            var view = new LobbyView(state, (t, m, r, p) -> UUID.randomUUID(), q -> {});
            view.restore(new LobbyView.Selection(5, 202));
            assertEquals(new LobbyView.Selection(5, 202), view.selection());
            assertFalse(((Button) field(view, "challenge")).isDisabled());
            var library = (VBox) ((ScrollPane) view.getCenter()).getContent();
            var search =
                (TextField)
                    library.getChildren().stream()
                        .filter(TextField.class::isInstance)
                        .findFirst()
                        .orElseThrow();
            search.setText("Không có tên này");
            assertTrue(
                library.getChildren().stream()
                    .anyMatch(
                        n ->
                            n instanceof Label l
                                && l.getText().startsWith("Không tìm thấy")
                                && l.isManaged()));
            search.clear();
            assertFalse(
                library.getChildren().stream()
                    .anyMatch(
                        n ->
                            n instanceof Label l
                                && l.getText().startsWith("Không tìm thấy")
                                && l.isManaged()));
            ((com.fasterxml.jackson.databind.node.ObjectNode)
                    data.get(MessageType.ONLINE_LIST).path("users").get(0))
                .put("status", "PLAYING");
            var updated = new LobbyView(state, (t, m, r, p) -> UUID.randomUUID(), q -> {});
            updated.restore(view.selection());
            assertEquals(0, updated.selection().opponent());
            assertTrue(((Button) field(updated, "challenge")).isDisabled());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
