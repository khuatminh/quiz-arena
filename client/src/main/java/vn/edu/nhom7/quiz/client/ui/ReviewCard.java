package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;

/**
 * A shared, read-only question review for live results and saved history. The header row shows the
 * question and how {@code self} did; the answers and explanation fold out below it. Questions
 * {@code self} got wrong or let time out start open, the rest start folded.
 */
final class ReviewCard extends VBox {
  private final VBox body = new VBox(12);
  private final javafx.scene.control.Label chevron = Ui.label("");
  private final HBox header = new HBox(12);

  ReviewCard(JsonNode review, JsonNode players, long self) {
    setSpacing(0);
    getStyleClass().add("review-card");
    JsonNode q = review.path("question");
    boolean revealed = review.path("revealed").asBoolean();
    JsonNode own = null;
    for (var outcome : review.path("outcomes"))
      if (outcome.path("userId").asLong() == self) own = outcome;
    var result =
        Ui.label(
            !revealed
                ? "Chưa chấm"
                : own == null
                    ? ""
                    : own.path("outcome").asText().equals("TIMEOUT")
                        ? "Hết giờ · +0"
                        : (own.path("correct").asBoolean() ? "✓ Đúng" : "✗ Sai")
                            + " · +"
                            + own.path("earnedPoints").asInt());
    result.setWrapText(false);
    result.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    result
        .getStyleClass()
        .addAll(
            "review-result",
            !revealed || own == null
                ? "neutral"
                : own.path("correct").asBoolean() ? "right" : "wrong");
    result.setVisible(!result.getText().isEmpty());
    result.setManaged(result.isVisible());
    var number = Ui.badge("Câu " + review.path("roundIndex").asInt());
    number.setWrapText(false);
    number.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    var question = Ui.label(q.path("content").asText());
    question.getStyleClass().add("review-question");
    question.setMinWidth(0);
    HBox.setHgrow(question, Priority.ALWAYS);
    question.setMaxWidth(Double.MAX_VALUE);
    chevron.getStyleClass().add("review-chevron");
    chevron.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    header.getChildren().addAll(number, question, result, chevron);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("review-header");
    header.setFocusTraversable(true);
    header.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
    header.setOnMouseClicked(e -> setExpanded(!body.isVisible()));
    header.setOnKeyPressed(
        e -> {
          if (e.getCode() == javafx.scene.input.KeyCode.ENTER
              || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
            setExpanded(!body.isVisible());
            e.consume();
          }
        });
    body.setPadding(new Insets(4, 18, 18, 18));
    body.getStyleClass().add("review-body");
    getChildren().addAll(header, body);

    if (q.hasNonNull("questionAssetId"))
      body.getChildren().add(new AssetLoader().view(q.path("questionAssetId").asText(), 260));
    var keys = new HashSet<String>();
    JsonNode answer = review.path("correctAnswer");
    if (answer.isArray()) answer.forEach(v -> keys.add(v.asText()));
    else keys.add(answer.asText());
    var picked = new HashSet<String>();
    if (own != null) {
      JsonNode mine = own.path("answer");
      if (mine.isArray()) mine.forEach(v -> picked.add(v.asText()));
      else if (!mine.isMissingNode() && !mine.isNull()) picked.add(mine.asText());
    }
    for (var option : q.path("options")) {
      String id = option.path("id").asText();
      boolean correct = revealed && keys.contains(id);
      var label =
          Ui.label(
              id
                  + ". "
                  + option.path("text").asText()
                  + (correct ? "  ✓" : "")
                  + (picked.contains(id) ? "  · bạn chọn" : ""));
      label.getStyleClass().add("answer-card");
      label.setMaxWidth(Double.MAX_VALUE);
      if (correct) label.getStyleClass().add("correct");
      else if (revealed && picked.contains(id)) label.getStyleClass().add("incorrect");
      body.getChildren().add(label);
    }
    if (!revealed) {
      body.getChildren().add(Ui.muted("Câu chưa được chấm"));
      setExpanded(false);
      return;
    }
    var explanation = new VBox(10, Ui.title("Đáp án: " + PresentationText.answer(q, answer)));
    explanation.getStyleClass().add("reveal-panel");
    if (review.hasNonNull("explanation") && !review.path("explanation").asText().isBlank())
      explanation.getChildren().add(Ui.label(review.path("explanation").asText()));
    if (review.hasNonNull("explanationAssetId"))
      explanation
          .getChildren()
          .add(new AssetLoader().view(review.path("explanationAssetId").asText(), 260));
    for (var outcome : review.path("outcomes")) {
      String name =
          players != null && !players.isMissingNode()
              ? PresentationText.playerName(players, outcome.path("userId").asLong(), self)
              : outcome.path("userId").asLong() == self ? "Bạn" : "Đối thủ";
      String value =
          outcome.path("outcome").asText().equals("TIMEOUT")
              ? "Không trả lời"
              : PresentationText.answer(q, outcome.path("answer"));
      explanation
          .getChildren()
          .add(
              Ui.label(
                  name
                      + " · "
                      + value
                      + " · "
                      + (outcome.path("correct").asBoolean() ? "✓ Đúng" : "✗ Sai")
                      + " · +"
                      + outcome.path("earnedPoints").asInt()));
    }
    body.getChildren().add(explanation);
    setExpanded(own != null && !own.path("correct").asBoolean());
  }

  boolean expanded() {
    return body.isVisible();
  }

  void setExpanded(boolean open) {
    body.setVisible(open);
    body.setManaged(open);
    chevron.setText(open ? "▾" : "▸");
    header.setAccessibleText(
        ((javafx.scene.control.Label) header.getChildren().get(1)).getText()
            + (open ? ", đang mở" : ", đang thu gọn"));
    getStyleClass().remove("open");
    if (open) getStyleClass().add("open");
  }
}
