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
    getChildren().add(Ui.title("Lịch sử của bạn"));
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
      for (var q : detail.path("review")) {
        getChildren()
            .add(
                Ui.label(
                    "Câu "
                        + q.path("roundIndex").asInt()
                        + ": "
                        + q.path("question").path("content").asText()
                        + " · "
                        + (q.path("revealed").asBoolean()
                            ? PresentationText.answer(q.path("question"), q.path("correctAnswer"))
                            : "Câu chưa được chấm")));
        if (q.hasNonNull("explanation"))
          getChildren().add(Ui.label(q.path("explanation").asText()));
        if (q.path("question").hasNonNull("questionAssetId"))
          getChildren()
              .add(
                  new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                      .view(q.path("question").path("questionAssetId").asText(), 180));
        if (q.hasNonNull("explanationAssetId"))
          getChildren()
              .add(
                  new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                      .view(q.path("explanationAssetId").asText(), 180));
      }
    }
    if (p == null) {
      getChildren().add(Ui.label("Đang tải…"));
      return;
    }
    if (p.path("items").isEmpty()) getChildren().add(Ui.label("Bạn chưa có trận đấu đã lưu."));
    for (var m : p.path("items")) {
      var row =
          Ui.stack(
              Ui.label(
                  m.path("quizTitle").asText()
                      + " · "
                      + m.path("opponentName").asText()
                      + " · "
                      + (m.path("ranked").asBoolean(true)
                          ? "Tính hạng"
                          : "Cộng đồng · Không tính hạng")),
              Ui.label(
                  m.path("ownScore").asInt()
                      + " — "
                      + m.path("opponentScore").asInt()
                      + " · "
                      + PresentationText.outcome(m.path("outcome").asText())
                      + " · "
                      + PresentationText.reason(m.path("reasonCode").asText())),
              Ui.button(
                  "Xem chi tiết",
                  () ->
                      sink.send(
                          MessageType.MATCH_DETAIL_REQUEST,
                          null,
                          null,
                          new Payloads.MatchDetailRequest(
                              UUID.fromString(m.path("matchId").asText())))));
      getChildren().add(row);
    }
    int page = p.path("page").asInt(1);
    getChildren()
        .add(
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
                            new Payloads.HistoryRequest(page + 1, 20)))));
  }
}
