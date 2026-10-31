package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.common.protocol.*;

class InteractiveViewsTest {
  private static final ObjectMapper JSON = new ObjectMapper();

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

  private void fx(Runnable run) throws Exception {
    var task =
        new FutureTask<Void>(
            () -> {
              run.run();
              return null;
            });
    Platform.runLater(task);
    task.get(10, TimeUnit.SECONDS);
  }

  private static JsonNode range() {
    return JSON.createObjectNode()
        .put("min", 0)
        .put("max", 10)
        .put("step", .1)
        .put("unit", "ms")
        .put("acceptedMin", 4.9)
        .put("acceptedMax", 5.1);
  }

  private Payloads.PublicQuestion question(String type, JsonNode config) {
    return new Payloads.PublicQuestion(
        1,
        type,
        "Câu hỏi tương tác",
        List.of(
            new Payloads.Option("a", "SYN"),
            new Payloads.Option("b", "SYN-ACK"),
            new Payloads.Option("c", "ACK")),
        "question-1",
        30000,
        config);
  }

  @Test
  void rangeRequiresInteractionValidatesStepAndLocksOneSubmission() throws Exception {
    fx(
        () -> {
          var answers = new ArrayList<JsonNode>();
          var renderer = new RangeRenderer();
          renderer.bind(
              question("RANGE", InteractiveQuestions.publicConfig(QuestionType.RANGE, range())),
              answers::add);
          var field = (TextField) renderer.view().lookup("#range-answer");
          var submit = (Button) renderer.view().lookup("#range-submit");
          renderer.setControlsEnabled(true);
          assertTrue(submit.isDisabled());
          field.setText("5.01");
          assertTrue(submit.isDisabled());
          field.setText("11");
          assertTrue(submit.isDisabled());
          field.setText("5,1");
          assertFalse(submit.isDisabled());
          submit.fire();
          submit.fire();
          assertEquals(1, answers.size());
          assertEquals(
              0, answers.getFirst().decimalValue().compareTo(new java.math.BigDecimal("5.1")));
          assertTrue(field.isDisabled());
        });
  }

  @Test
  void reorderArrowsChangePermutationAndLock() throws Exception {
    fx(
        () -> {
          var answers = new ArrayList<JsonNode>();
          var renderer = new ReorderRenderer();
          renderer.bind(question("REORDER", null), answers::add);
          var first = (HBox) renderer.view().lookup("#reorder-row-0");
          String text = ((Label) first.getChildren().getFirst()).getText().substring(3);
          ((Button) first.getChildren().get(2)).fire();
          var second = (HBox) renderer.view().lookup("#reorder-row-1");
          assertEquals("2. " + text, ((Label) second.getChildren().getFirst()).getText());
          renderer.submit();
          renderer.submit();
          assertEquals(1, answers.size());
          assertEquals(3, answers.getFirst().size());
          var ids = new HashSet<String>();
          answers.getFirst().forEach(v -> ids.add(v.asText()));
          assertEquals(Set.of("a", "b", "c"), ids);
        });
  }

  @Test
  void authorEditorsRoundTripAllThreeTypesWithoutRawJson() throws Exception {
    fx(
        () -> {
          try {
            var view = new CommunityQuizView((t, m, r, p) -> null, JSON.createArrayNode());
            var fill = CommunityQuizView.class.getDeclaredMethod("fillQuestion", JsonNode.class);
            fill.setAccessible(true);
            var q =
                JSON.createObjectNode().put("questionType", "RANGE").put("content", "Ước lượng");
            q.set("options", JSON.createArrayNode());
            q.set("answerKey", range());
            fill.invoke(view, q);
            var formField = CommunityQuizView.class.getDeclaredField("form");
            formField.setAccessible(true);
            var form = (VBox) formField.get(view);
            ((TextField) form.lookup("#range-editor-acceptedMin")).setText("4.8");
            assertEquals(4.8, view.question().path("answerKey").path("acceptedMin").asDouble());
            q.put("questionType", "PINPOINT").put("questionAssetId", "question-1");
            q.set("answerKey", ImageTarget.rectangle(.2, .3, .6, .5));
            fill.invoke(view, q);
            ((TextField) form.lookup("#pinpoint-editor-width")).setText("30");
            assertEquals(.3, view.question().path("answerKey").path("width").asDouble());
            assertEquals("question-1", view.question().path("questionAssetId").asText());
            q.put("questionType", "REORDER");
            q.set("options", JSON.valueToTree(question("REORDER", null).options()));
            q.set("answerKey", JSON.createArrayNode().add("c").add("a").add("b"));
            fill.invoke(view, q);
            assertEquals("ACK", ((TextField) form.lookup("#reorder-editor-0")).getText());
            ((TextField) form.lookup("#reorder-editor-0")).setText("ACK cuối");
            assertEquals("c", view.question().path("answerKey").get(0).asText());
            assertTrue(view.question().path("options").toString().contains("ACK cuối"));
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
  }

  @Test
  void pinpointKeyboardCoordinatesAndRevealSurviveResize() throws Exception {
    var renderer = new AtomicReference<PinpointRenderer>();
    var answers = new ArrayList<JsonNode>();
    fx(
        () -> {
          var r = new PinpointRenderer();
          renderer.set(r);
          r.bind(question("PINPOINT", null), answers::add);
          assertTrue(((Button) r.view().lookup("#pinpoint-submit")).isDisabled());
        });
    // Image decoding completes on the next FX task for bundled fixtures.
    fx(
        () -> {
          var r = renderer.get();
          var target = (ImageTarget) r.view().lookup("#pinpoint-image");
          target.resize(600, 250);
          target.layout();
          target.fireEvent(
              new KeyEvent(
                  KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
          r.submit();
          assertEquals(1, answers.size());
          assertEquals(.51, answers.getFirst().path("x").asDouble());
          r.showResult(
              new Payloads.QuestionResult(
                  1, ImageTarget.rectangle(.2, .3, .6, .5), "", null, List.of(), 0, 0, 0),
              1);
          target.resize(300, 250);
          target.layout();
          assertTrue(
              target.getChildren().stream()
                  .anyMatch(n -> n instanceof javafx.scene.shape.Rectangle && n.isVisible()));
          assertEquals(
              "Vùng được khoanh trên ảnh",
              PresentationText.answer(
                  JSON.valueToTree(question("PINPOINT", null)),
                  ImageTarget.rectangle(.2, .3, .6, .5)));
        });
  }

  @Test
  void interactionViewsRenderAtSmallAndLargeWidths() throws Exception {
    var savedRoot = new AtomicReference<VBox>();
    var savedScene = new AtomicReference<Scene>();
    fx(
        () -> {
          var root = new VBox(16);
          savedRoot.set(root);
          root.setStyle("-fx-padding: 20;");
          for (String type : List.of("REORDER", "RANGE", "PINPOINT")) {
            var r = QuestionRenderer.forType(type);
            r.bind(
                question(
                    type,
                    type.equals("RANGE")
                        ? InteractiveQuestions.publicConfig(QuestionType.RANGE, range())
                        : null),
                a -> {});
            root.getChildren()
                .addAll(
                    Ui.title(
                        switch (type) {
                          case "REORDER" -> "Sắp xếp thứ tự";
                          case "RANGE" -> "Ước lượng số";
                          default -> "Ghim trên ảnh";
                        }),
                    r.view());
          }
          var scene = new Scene(root, 960, 1100);
          Theme.attach(scene);
          savedScene.set(scene);
          for (double width : new double[] {600, 960}) {
            root.resize(width, 1100);
            root.applyCss();
            root.layout();
            assertTrue(root.prefHeight(width) > 0);
          }
        });
    fx(
        () -> {
          VBox root = savedRoot.get();
          root.applyCss();
          root.layout();
          String evidence = System.getProperty("quiz.interactiveEvidence");
          if (evidence != null) {
            var image = root.snapshot(null, null);
            var pixels = image.getPixelReader();
            var out =
                new java.awt.image.BufferedImage(
                    (int) image.getWidth(),
                    (int) image.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < out.getHeight(); y++)
              for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, pixels.getArgb(x, y));
            try {
              javax.imageio.ImageIO.write(out, "png", new java.io.File(evidence));
            } catch (java.io.IOException e) {
              throw new AssertionError(e);
            }
          }
          Theme.detach(savedScene.get());
        });
  }

  @Test
  void fullGameKeepsInteractiveDeckScrollableAndRevealStable() throws Exception {
    fx(
        () -> {
          for (String type : List.of("REORDER", "RANGE", "PINPOINT")) {
            var match = UUID.randomUUID();
            var round = UUID.randomUUID();
            var q =
                JSON.valueToTree(
                    question(
                        type,
                        type.equals("RANGE")
                            ? InteractiveQuestions.publicConfig(QuestionType.RANGE, range())
                            : null));
            var result =
                JSON.createObjectNode()
                    .put("questionId", 1)
                    .put("explanation", "Lời giải")
                    .putNull("explanationAssetId")
                    .put("remainingMs", 2000)
                    .put("phaseEndsAtMs", 2000)
                    .put("serverTimeMs", 0);
            result.set(
                "correctAnswer",
                type.equals("RANGE")
                    ? range()
                    : type.equals("PINPOINT")
                        ? ImageTarget.rectangle(.2, .3, .6, .5)
                        : JSON.createArrayNode().add("a").add("b").add("c"));
            result.set("outcomes", JSON.createArrayNode());
            for (String phase : List.of("OPEN", "REVEAL")) {
              var state =
                  new vn.edu.nhom7.quiz.client.state.ClientState(
                      0,
                      JSON.createObjectNode().put("userId", 1),
                      0,
                      match,
                      null,
                      round,
                      Map.of(),
                      phase,
                      0,
                      q,
                      false,
                      false,
                      Map.of(
                          MessageType.QUESTION_RESULT,
                          result,
                          MessageType.QUESTION_OPEN,
                          JSON.createObjectNode().put("remainingMs", 30000)),
                      Map.of(),
                      Map.of(),
                      "",
                      System.nanoTime());
              var game = new GameView(state, (t, m, r, p) -> null, true);
              var scene = new Scene(game, 600, 720);
              Theme.attach(scene);
              game.resize(600, 720);
              game.applyCss();
              game.layout();
              assertNull(game.getBottom());
              assertInstanceOf(ScrollPane.class, game.getCenter());
              game.render(state);
              game.tick();
              if (phase.equals("OPEN")) assertTrue(game.secondsLeft() >= 29);
              game.dispose();
              Theme.detach(scene);
            }
          }
        });
  }
}
