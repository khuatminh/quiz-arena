package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class QuizDetailView extends VBox {
  private final Label description = Ui.label("Đang tải thông tin…");
  private final VBox types = new VBox(10);

  public QuizDetailView(JsonNode quiz, UiCommandSink sink) {
    setSpacing(16);
    setPadding(new Insets(24));
    setPrefWidth(285);
    setMinWidth(285);
    setMaxWidth(285);
    getStyleClass().add("detail-panel");
    var body =
        new VBox(
            16,
            Ui.badge("Bộ câu hỏi đã chọn"),
            new AssetLoader().view(quiz.path("coverAssetId").asText(), 220),
            Ui.title(quiz.path("title").asText()),
            Ui.label(quiz.path("totalRounds").asInt(10) + " câu · 15 giây mỗi câu"),
            Ui.badge(
                quiz.path("quizSource").asText().equals("COMMUNITY")
                    ? "Cộng đồng · Không tính hạng"
                    : "Quiz hệ thống · Tính hạng"),
            description,
            types,
            Ui.muted("Trả lời nhanh để nhận 3 / 2 / 1 điểm. Không trừ điểm khi sai."),
            Ui.muted("Chọn người chơi trong danh sách, rồi gửi lời thách đấu."));
    if (quiz.path("quizSource").asText().equals("COMMUNITY"))
      body.getChildren().add(3, Ui.muted("Tác giả: " + quiz.path("authorName").asText()));
    body.setMinWidth(0);
    var scroll = Ui.scroll(body);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren().add(scroll);
    sink.send(
        MessageType.QUIZ_DETAIL_REQUEST,
        null,
        null,
        new Payloads.QuizDetailRequest(quiz.path("quizId").asLong()));
  }

  public void render(JsonNode details) {
    description.setText(details.path("description").asText());
    var counts = details.path("typeCounts");
    types.getChildren().clear();
    String[][] labels = {
      {"SINGLE_CHOICE", "Một đáp án"},
      {"MULTIPLE_CHOICE", "Nhiều đáp án"},
      {"TRUE_FALSE", "Đúng / Sai"},
      {"SHORT_ANSWER", "Trả lời ngắn"}
    };
    for (var type : labels) {
      var row =
          Ui.actions(Ui.label(type[1]), Ui.spacer(), Ui.badge(counts.path(type[0]).asInt() + ""));
      types.getChildren().add(row);
    }
  }
}
