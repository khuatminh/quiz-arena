package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class RevealPanel extends VBox {
  public RevealPanel(JsonNode p, JsonNode question, long self) {
    setSpacing(12);
    getStyleClass().add("reveal-panel");
    setPadding(new Insets(20));
    getChildren()
        .addAll(
            Ui.title("Đáp án: " + PresentationText.answer(question, p.path("correctAnswer"))),
            Ui.label(p.path("explanation").isNull() ? "" : p.path("explanation").asText()));
    if (p.hasNonNull("explanationAssetId"))
      getChildren()
          .add(
              new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                  .view(p.path("explanationAssetId").asText(), 180));
    for (var o : p.path("outcomes")) {
      String value =
          o.path("outcome").asText().equals("TIMEOUT")
              ? "Không trả lời"
              : o.path("outcome").asText().equals("ABANDONED")
                  ? "Câu chưa được chấm"
                  : PresentationText.answer(question, o.path("answer"));
      getChildren()
          .add(
              Ui.label(
                  (o.path("userId").asLong() == self ? "Bạn" : "Đối thủ")
                      + " · "
                      + value
                      + " · "
                      + (o.path("correct").asBoolean() ? "✓ Đúng" : "✗ Sai")
                      + " · +"
                      + o.path("earnedPoints").asInt()));
    }
  }
}
