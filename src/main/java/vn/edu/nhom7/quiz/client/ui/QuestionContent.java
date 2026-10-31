package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.client.state.ClientState;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Question and answer presentation shared across the preparation, answering and reveal phases. */
final class QuestionContent {
  // Results stored by the reducer also carry client-only streak counts.
  private static final ObjectMapper RESULT_JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  final VBox stage = new VBox(14);
  final VBox deck = new VBox(14);
  final Label kicker;
  final QuestionRenderer renderer;

  QuestionContent(ClientState state, UiCommandSink sink, Runnable onAnswer) {
    JsonNode question = state.question();
    JsonNode start = state.payload(MessageType.MATCH_START);
    String type = question.path("questionType").asText();

    String content = question.path("content").asText();
    var title = Ui.label(content);
    title.getStyleClass().add("question-title");
    title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    if (content.codePointCount(0, content.length()) > 160)
      title.getStyleClass().add("long-question");
    String kind =
        switch (type) {
          case "MULTIPLE_CHOICE" -> "NHIỀU ĐÁP ÁN · CHỌN TẤT CẢ Ý ĐÚNG";
          case "TRUE_FALSE" -> "ĐÚNG HAY SAI";
          case "SHORT_ANSWER" -> "TRẢ LỜI NGẮN";
          case "REORDER" -> "SẮP XẾP THỨ TỰ";
          case "RANGE" -> "ƯỚC LƯỢNG SỐ";
          case "PINPOINT" -> "GHIM TRÊN ẢNH";
          default -> "MỘT ĐÁP ÁN";
        };
    String quiz = start == null ? "" : start.path("quiz").path("title").asText("");
    kicker = Ui.label(quiz.isBlank() ? kind : kind + " · " + quiz.toUpperCase(Locale.ROOT));
    kicker.getStyleClass().add("question-kicker");
    if (state.phase().equals("REVEAL")) {
      kicker.setText("ĐÁP ÁN · " + PresentationText.answer(question, revealAnswer(state)));
      kicker.getStyleClass().add("answer-kicker");
    } else if (state.phase().equals("QUESTION_PREPARE")) kicker.setText(kind + " · ĐANG TẢI");
    stage.getChildren().addAll(kicker, title);
    stage.getStyleClass().add("question-stage");
    stage.setAlignment(Pos.CENTER);
    if (question.hasNonNull("questionAssetId") && !type.equals("PINPOINT"))
      stage
          .getChildren()
          .add(new AssetLoader().view(question.path("questionAssetId").asText(), 220));
    renderer = QuestionRenderer.forType(type);
    deck.getStyleClass().add("answer-deck");
    try {
      var publicQuestion = new ObjectMapper().treeToValue(question, Payloads.PublicQuestion.class);
      renderer.bind(
          publicQuestion,
          a -> {
            if (state.canAnswer())
              sink.send(
                  MessageType.ANSWER,
                  state.activeMatchId(),
                  state.roundId(),
                  new Payloads.Answer(publicQuestion.questionId(), a));
            onAnswer.run();
          });
      if (renderer instanceof BaseQuestionRenderer base && start != null)
        base.setPlayers(start.path("players"));
      renderer.setControlsEnabled(state.canAnswer());
      if (state.phase().equals("REVEAL")) {
        var result = state.payload(MessageType.QUESTION_RESULT);
        renderer.showResult(
            RESULT_JSON.treeToValue(result, Payloads.QuestionResult.class), state.selfUserId());
        if (result.hasNonNull("explanation") && !result.path("explanation").asText().isBlank()) {
          var why = Ui.label(result.path("explanation").asText());
          why.getStyleClass().add("explanation");
          why.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
          stage.getChildren().add(why);
        }
        if (result.hasNonNull("explanationAssetId"))
          stage
              .getChildren()
              .add(new AssetLoader().view(result.path("explanationAssetId").asText(), 160));
        // Typed answers have no tiles to carry the players' picks, so list them.
        if (java.util.Set.of("SHORT_ANSWER", "REORDER", "RANGE", "PINPOINT").contains(type))
          stage
              .getChildren()
              .add(
                  new RevealPanel(
                      result,
                      question,
                      state.selfUserId(),
                      start == null ? null : start.path("players")));
      }
      deck.getChildren().add(renderer.view());
    } catch (Exception e) {
      deck.getChildren().add(Ui.label("Không thể hiển thị câu hỏi; chờ server kết thúc an toàn."));
    }
  }

  static JsonNode revealAnswer(ClientState state) {
    var result = state.payload(MessageType.QUESTION_RESULT);
    return result == null ? null : result.path("correctAnswer");
  }
}
