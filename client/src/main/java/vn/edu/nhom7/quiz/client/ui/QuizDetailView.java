package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class QuizDetailView extends VBox {
  public QuizDetailView(JsonNode quiz, UiCommandSink sink) {
    setSpacing(12);
    setPadding(new Insets(24));
    getChildren()
        .addAll(
            Ui.title(quiz.path("title").asText()),
            Ui.label("10 câu: 4 một đáp án · 2 nhiều đáp án · 2 đúng/sai · 2 trả lời ngắn"),
            Ui.label("15 giây mỗi câu · Điểm theo server: 3 / 2 / 1 · Không trừ điểm khi sai"));
    sink.send(
        MessageType.QUIZ_DETAIL_REQUEST,
        null,
        null,
        new Payloads.QuizDetailRequest(quiz.path("quizId").asLong()));
  }

  public void render(JsonNode details) {
    getChildren().add(Ui.label(details.path("description").asText()));
    var counts = details.path("typeCounts");
    getChildren()
        .add(
            Ui.label(
                "Một đáp án: "
                    + counts.path("SINGLE_CHOICE").asInt()
                    + " · Nhiều đáp án: "
                    + counts.path("MULTIPLE_CHOICE").asInt()
                    + " · Đúng/sai: "
                    + counts.path("TRUE_FALSE").asInt()
                    + " · Trả lời ngắn: "
                    + counts.path("SHORT_ANSWER").asInt()));
  }
}
