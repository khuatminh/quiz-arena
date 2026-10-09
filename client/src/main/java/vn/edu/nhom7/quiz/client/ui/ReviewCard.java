package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;

/** A shared, read-only question review for live results and saved history. */
final class ReviewCard extends VBox {
  ReviewCard(JsonNode review, JsonNode players, long self) {
    setSpacing(14);
    setPadding(new Insets(24));
    getStyleClass().add("review-card");
    JsonNode q = review.path("question");
    getChildren()
        .addAll(
            Ui.badge("Câu " + review.path("roundIndex").asInt()),
            Ui.title(q.path("content").asText()));
    if (q.hasNonNull("questionAssetId"))
      getChildren().add(new AssetLoader().view(q.path("questionAssetId").asText(), 260));
    var keys = new HashSet<String>();
    JsonNode answer = review.path("correctAnswer");
    if (answer.isArray()) answer.forEach(v -> keys.add(v.asText()));
    else keys.add(answer.asText());
    for (var option : q.path("options")) {
      var label =
          Ui.label(
              option.path("id").asText()
                  + ". "
                  + option.path("text").asText()
                  + (review.path("revealed").asBoolean()
                          && keys.contains(option.path("id").asText())
                      ? "  ✓"
                      : ""));
      label.getStyleClass().add("answer-card");
      label.setMaxWidth(Double.MAX_VALUE);
      if (review.path("revealed").asBoolean() && keys.contains(option.path("id").asText()))
        label.getStyleClass().add("correct");
      getChildren().add(label);
    }
    if (!review.path("revealed").asBoolean()) {
      getChildren().add(Ui.muted("Câu chưa được chấm"));
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
    getChildren().add(explanation);
  }
}
