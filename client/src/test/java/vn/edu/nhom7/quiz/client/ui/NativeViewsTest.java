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
          ((Button) ((VBox) r.view()).getChildren().getFirst()).fire();
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
}
