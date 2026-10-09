package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ResultView extends VBox {
  private final Label save = Ui.label("");

  public ResultView(ClientState s, UiCommandSink sink) {
    setSpacing(16);
    setPadding(new Insets(28));
    JsonNode r = s.payload(MessageType.MATCH_RESULT);
    if (r == null) {
      getChildren().add(Ui.title("Kết quả trận đấu"));
      return;
    }
    String title =
        r.path("finishReason").asText().equals("ABORTED")
            ? "Trận đấu bị hủy"
            : r.path("winnerUserId").isNull()
                ? "Hòa!"
                : r.path("winnerUserId").asLong() == s.selfUserId()
                    ? "Bạn chiến thắng!"
                    : "Trận đấu đã kết thúc";
    getChildren()
        .addAll(
            Ui.title(title),
            Ui.label(
                PresentationText.outcome(r.path("finishReason").asText())
                    + " · "
                    + PresentationText.reason(r.path("reasonCode").asText())),
            save);
    for (var player : r.path("players"))
      getChildren()
          .add(
              Ui.label(
                  player.path("displayName").asText()
                      + " · "
                      + player.path("totalScore").asInt()
                      + " điểm · "
                      + player.path("correctCount").asInt()
                      + " câu đúng"));
    var rematch =
        Ui.button(
            "Đấu lại",
            () ->
                sink.send(
                    MessageType.REMATCH_REQUEST,
                    s.activeMatchId(),
                    null,
                    new Payloads.RematchRequest()));
    var accept =
        Ui.button(
            "Chấp nhận đấu lại",
            () ->
                sink.send(
                    MessageType.REMATCH_RESPONSE,
                    s.activeMatchId(),
                    null,
                    new Payloads.RematchResponse(true)));
    var reject =
        Ui.button(
            "Từ chối",
            () ->
                sink.send(
                    MessageType.REMATCH_RESPONSE,
                    s.activeMatchId(),
                    null,
                    new Payloads.RematchResponse(false)));
    boolean detached = !s.resultAttached();
    rematch.setDisable(detached);
    accept.setDisable(detached || s.payload(MessageType.REMATCH_STATUS) == null);
    reject.setDisable(accept.isDisabled());
    getChildren()
        .addAll(
            new HBox(
                12,
                rematch,
                accept,
                reject,
                Ui.button(
                    "Về sảnh",
                    () ->
                        sink.send(
                            MessageType.EXIT_MATCH,
                            s.activeMatchId(),
                            null,
                            new Payloads.ExitMatch()))),
            Ui.title("Xem lại câu hỏi"));
    for (var review : r.path("review")) {
      var box =
          Ui.stack(
              Ui.label(
                  "Câu "
                      + review.path("roundIndex").asInt()
                      + " · "
                      + review.path("question").path("content").asText()));
      box.getChildren()
          .add(
              Ui.label(
                  review.path("revealed").asBoolean()
                      ? "Đáp án: "
                          + PresentationText.answer(
                              review.path("question"), review.path("correctAnswer"))
                      : "Câu chưa được chấm"));
      for (var o : review.path("outcomes"))
        box.getChildren()
            .add(
                Ui.label(
                    PresentationText.playerName(
                            r.path("players"), o.path("userId").asLong(), s.selfUserId())
                        + " · "
                        + PresentationText.answer(review.path("question"), o.path("answer"))
                        + " · "
                        + PresentationText.outcome(o.path("outcome").asText())));
      if (review.hasNonNull("explanation"))
        box.getChildren().add(Ui.label(review.path("explanation").asText()));
      getChildren().add(box);
    }
    render(s);
  }

  public void render(ClientState s) {
    save.setText(
        switch (s.persistenceByMatch().getOrDefault(s.resultMatchId(), "PENDING")) {
          case "SAVED" -> "✓ Kết quả đã được lưu";
          case "FAILED" -> "Lưu chưa thành công · server có thể thử lại";
          default -> "Đang lưu kết quả…";
        });
  }
}
