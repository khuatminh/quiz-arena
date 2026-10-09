package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class HistoryView extends VBox {
  public HistoryView(JsonNode p, JsonNode detail, UiCommandSink sink) {
    setSpacing(16);
    setPadding(new Insets(28));
    getChildren()
        .addAll(
            Ui.title("Lịch sử của bạn"),
            Ui.muted("Xem kết quả và ôn lại câu hỏi từ những trận đã lưu."));
    if (detail != null) {
      JsonNode summary = detail.path("summary");
      int detailPage = detail.path("page").asInt(1);
      long total = detail.path("totalItems").asLong(detail.path("review").size());
      getChildren()
          .add(
              Ui.label(
                  summary.path("totalRounds").asInt(10)
                      + " câu · "
                      + (summary.path("ranked").asBoolean(true)
                          ? "Tính hạng"
                          : "Cộng đồng · Không tính hạng")));
      Button previous =
          Ui.button(
              "← Câu trước",
              () ->
                  sink.send(
                      MessageType.MATCH_DETAIL_REQUEST,
                      null,
                      null,
                      new Payloads.MatchDetailRequest(
                          UUID.fromString(summary.path("matchId").asText()), detailPage - 1)));
      Button next =
          Ui.button(
              "Câu tiếp →",
              () ->
                  sink.send(
                      MessageType.MATCH_DETAIL_REQUEST,
                      null,
                      null,
                      new Payloads.MatchDetailRequest(
                          UUID.fromString(summary.path("matchId").asText()), detailPage + 1)));
      previous.setDisable(detailPage <= 1);
      next.setDisable(detailPage * Payloads.REVIEW_PAGE_SIZE >= total);
      getChildren().add(new HBox(10, previous, Ui.label("Trang câu " + detailPage), next));
      var reviewPanel = new VBox(14);
      reviewPanel.getStyleClass().add("editor-panel");
      getChildren().add(reviewPanel);
      for (var q : detail.path("review")) {
        var players = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (var outcome : q.path("outcomes")) {
          long id = outcome.path("userId").asLong();
          players
              .addObject()
              .put("userId", id)
              .put(
                  "displayName",
                  id == summary.path("opponentUserId").asLong()
                      ? summary.path("opponentName").asText("Đối thủ")
                      : "Bạn");
        }
        reviewPanel.getChildren().add(new ReviewCard(q, players, -1));
      }
    }
    if (p == null) {
      getChildren().add(Ui.label("Đang tải…"));
      return;
    }
    if (p.path("items").isEmpty()) getChildren().add(Ui.label("Bạn chưa có trận đấu đã lưu."));
    for (var m : p.path("items")) {
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
      var outcome =
          new VBox(6, score, Ui.badge(PresentationText.outcome(m.path("outcome").asText())));
      outcome.setMinWidth(120);
      var row =
          new HBox(
              24,
              information,
              outcome,
              Ui.secondary(
                  "Xem chi tiết",
                  () ->
                      sink.send(
                          MessageType.MATCH_DETAIL_REQUEST,
                          null,
                          null,
                          new Payloads.MatchDetailRequest(
                              UUID.fromString(m.path("matchId").asText())))));
      row.setAlignment(Pos.CENTER_LEFT);
      row.setPadding(new Insets(20));
      row.getStyleClass().add("review-card");
      getChildren().add(row);
    }
    int page = p.path("page").asInt(1);
    var pagination =
        new HBox(
            12,
            Ui.button(
                "← Trước",
                () ->
                    sink.send(
                        MessageType.HISTORY_REQUEST,
                        null,
                        null,
                        new Payloads.HistoryRequest(Math.max(1, page - 1), 20))),
            Ui.label("Trang " + page),
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
}
