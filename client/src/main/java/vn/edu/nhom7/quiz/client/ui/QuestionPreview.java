package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

/** Displays an isolated copy of a draft using the same answer renderer as a live match. */
final class QuestionPreview {
  private static final ObjectMapper JSON = new ObjectMapper();

  private QuestionPreview() {}

  static void show(ObjectNode draft, boolean reveal, Window owner) throws Exception {
    ObjectNode q = draft.deepCopy();
    String questionAsset =
        q.hasNonNull("questionAssetId") ? q.path("questionAssetId").asText() : null;
    String explanationAsset =
        q.hasNonNull("explanationAssetId") ? q.path("explanationAssetId").asText() : null;
    q.put("questionId", 0);
    ObjectNode publicQ = q.deepCopy();
    publicQ.remove(List.of("answerKey", "explanation", "explanationAssetId"));
    publicQ.put("timeLimitMs", 15000);
    QuestionRenderer renderer = QuestionRenderer.forType(q.path("questionType").asText());
    renderer.bind(JSON.treeToValue(publicQ, Payloads.PublicQuestion.class), a -> {});
    renderer.setControlsEnabled(!reveal);
    if (reveal)
      renderer.showResult(
          new Payloads.QuestionResult(
              0,
              q.path("answerKey"),
              q.path("explanation").asText(),
              explanationAsset,
              List.of(),
              0,
              0,
              0),
          0);
    VBox body = new VBox(12, Ui.title(q.path("content").asText()));
    if (questionAsset != null) body.getChildren().add(new AssetLoader().view(questionAsset, 180));
    body.getChildren().add(renderer.view());
    if (reveal) {
      body.getChildren()
          .addAll(
              Ui.label("Đáp án: " + PresentationText.answer(publicQ, q.path("answerKey"))),
              Ui.label(q.path("explanation").asText()));
      if (explanationAsset != null)
        body.getChildren().add(new AssetLoader().view(explanationAsset, 180));
    }
    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle(reveal ? "Xem trước công bố đáp án" : "Xem trước câu hỏi");
    dialog.getDialogPane().setContent(Ui.scroll(body));
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.getDialogPane().setPrefSize(700, 600);
    dialog.initOwner(owner);
    dialog.getDialogPane().getStylesheets().setAll(Theme.stylesheets());
    body.getStyleClass().add("question-panel");
    dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setAccessibleText("Đóng xem trước");
    ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
    dialog.show();
  }
}
