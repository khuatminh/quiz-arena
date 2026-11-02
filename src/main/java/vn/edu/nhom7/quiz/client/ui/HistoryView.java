package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class HistoryView extends VBox {
  public HistoryView(
      JsonNode p, UiCommandSink sink, java.util.function.Consumer<JsonNode> openDetail) {
    setSpacing(16);
    setPadding(new Insets(28));
    getChildren()
        .addAll(
            Ui.kicker("LỊCH SỬ"),
            Ui.title("Lịch sử của bạn"),
            Ui.muted("Xem kết quả và ôn lại câu hỏi từ những trận đã lưu."));
    if (p == null) {
      getChildren().add(Ui.label("Đang tải…"));
      return;
    }
    if (p.path("items").isEmpty()) getChildren().add(Ui.label("Bạn chưa có trận đấu đã lưu."));
    for (var match : p.path("items")) getChildren().add(matchRow(match, openDetail));
    int page = p.path("page").asInt(1);
    var pagination =
        Ui.pager(
            Ui.button(
                "← Trước",
                () ->
                    sink.send(
                        MessageType.HISTORY_REQUEST,
                        null,
                        null,
                        new Payloads.HistoryRequest(Math.max(1, page - 1), 20))),
            "Trang " + page,
            Ui.button(
                "Tiếp →",
                () ->
                    sink.send(
                        MessageType.HISTORY_REQUEST,
                        null,
                        null,
                        new Payloads.HistoryRequest(page + 1, 20))));
    ((Button) pagination.getChildren().get(0)).setDisable(page <= 1);
    ((Button) pagination.getChildren().get(2))
        .setDisable((long) page * p.path("pageSize").asInt(10) >= p.path("totalItems").asLong());
    getChildren().add(pagination);
  }

  private HBox matchRow(JsonNode m, java.util.function.Consumer<JsonNode> openDetail) {
    String date =
        m.path("endedAtMs").asLong() > 0
            ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(m.path("endedAtMs").asLong()))
            : "";
    var information =
        new VBox(
            6,
            Ui.title(m.path("quizTitle").asText()),
            Ui.muted("Đối thủ: " + m.path("opponentName").asText() + " · " + date),
            Ui.badge(
                m.path("ranked").asBoolean(true) ? "Tính hạng" : "Cộng đồng · Không tính hạng"));
    if (m.hasNonNull("reasonCode") && !m.path("reasonCode").asText().isBlank())
      information
          .getChildren()
          .add(Ui.muted(PresentationText.reason(m.path("reasonCode").asText())));
    HBox.setHgrow(information, Priority.ALWAYS);
    information.setMinWidth(0);
    var score = Ui.title(m.path("ownScore").asInt() + " : " + m.path("opponentScore").asInt());
    score.getStyleClass().add("history-score");
    var result = Ui.badge(PresentationText.outcome(m.path("outcome").asText()));
    result
        .getStyleClass()
        .add(
            switch (m.path("outcome").asText()) {
              case "WIN", "WON" -> "outcome-win";
              case "LOSS", "LOST" -> "outcome-lose";
              default -> "outcome-other";
            });
    var outcome = new VBox(6, score, result);
    outcome.setAlignment(Pos.CENTER_RIGHT);
    outcome.setMinWidth(120);
    var row =
        new HBox(
            24, information, outcome, Ui.secondary("Xem chi tiết", () -> openDetail.accept(m)));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(20));
    row.getStyleClass().add("review-card");
    return row;
  }
}
