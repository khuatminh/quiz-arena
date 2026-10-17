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
                    : PresentationText.playerName(
                            r.path("players"), r.path("winnerUserId").asLong(), s.selfUserId())
                        + " chiến thắng · Cố lên lần sau!";
    var hero = new VBox(16, Ui.title(title));
    hero.getStyleClass().add("result-hero");
    hero.getChildren()
        .add(
            Ui.label(
                PresentationText.outcome(r.path("finishReason").asText())
                    + " · "
                    + PresentationText.reason(r.path("reasonCode").asText())));
    var players = new HBox(48);
    players.setAlignment(Pos.CENTER);
    for (var player : r.path("players")) {
      var score = Ui.title(player.path("totalScore").asInt() + "");
      score.getStyleClass().add("result-score");
      var tile =
          new VBox(
              8,
              new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                  .view(player.path("avatarId").asText(), 72),
              Ui.label(player.path("displayName").asText()),
              score,
              Ui.label(player.path("correctCount").asInt() + " câu đúng"));
      tile.setAlignment(Pos.CENTER);
      players.getChildren().add(tile);
    }
    hero.getChildren().addAll(players, save);
    getChildren().add(hero);
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
    // Only the invited player may answer a rematch; the requester just waits.
    JsonNode rematchStatus = s.payload(MessageType.REMATCH_STATUS);
    String rematchState = rematchStatus == null ? "" : rematchStatus.path("status").asText();
    boolean pending = rematchState.equals("PENDING");
    boolean ownRequest =
        pending && rematchStatus.path("requesterUserId").asLong() == s.selfUserId();
    boolean incoming = pending && !ownRequest && !detached;
    var rematchNote = Ui.label("");
    rematchNote.getStyleClass().add("rematch-status");
    rematchNote.setText(
        detached
            ? ""
            : ownRequest
                ? "Đã gửi lời mời đấu lại · đang chờ đối thủ trả lời"
                : incoming
                    ? PresentationText.playerName(
                            r.path("players"),
                            rematchStatus.path("requesterUserId").asLong(),
                            s.selfUserId())
                        + " muốn đấu lại!"
                    : switch (rematchState) {
                      case "REJECTED" -> "Lời mời đấu lại đã bị từ chối.";
                      case "EXPIRED" -> "Lời mời đấu lại đã hết hạn.";
                      case "INVALIDATED" -> "Không thể đấu lại trận này nữa.";
                      default -> "";
                    });
    rematchNote.managedProperty().bind(rematchNote.textProperty().isNotEmpty());
    rematchNote.visibleProperty().bind(rematchNote.managedProperty());
    rematch.setDisable(detached || pending);
    for (var b : List.of(accept, reject)) {
      b.setVisible(incoming);
      b.setManaged(incoming);
    }
    rematch.setVisible(!incoming);
    rematch.setManaged(!incoming);
    reject.getStyleClass().add("secondary");
    getChildren()
        .addAll(
            rematchNote,
            new FlowPane(
                12,
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
    getChildren()
        .add(
            Ui.label(
                r.path("totalRounds").asInt(10)
                    + " câu · tối đa "
                    + (r.path("totalRounds").asInt(10) * 3)
                    + " điểm · "
                    + (r.path("ranked").asBoolean(true)
                        ? "Tính hạng"
                        : "Cộng đồng · Không tính hạng")));
    JsonNode pageData = s.payload(MessageType.LIVE_REVIEW);
    int page = pageData == null ? r.path("reviewPage").asInt(1) : pageData.path("page").asInt(1);
    long total =
        pageData == null
            ? r.path("reviewTotalItems").asLong(r.path("review").size())
            : pageData.path("totalItems").asLong();
    final java.util.UUID reviewMatch = s.resultMatchId();
    Button previous =
        Ui.button(
            "← Trước",
            () ->
                sink.send(
                    MessageType.LIVE_REVIEW_REQUEST,
                    reviewMatch,
                    null,
                    new Payloads.LiveReviewRequest(page - 1)));
    Button next =
        Ui.button(
            "Tiếp →",
            () ->
                sink.send(
                    MessageType.LIVE_REVIEW_REQUEST,
                    reviewMatch,
                    null,
                    new Payloads.LiveReviewRequest(page + 1)));
    previous.setDisable(page <= 1 || detached);
    next.setDisable(page * Payloads.REVIEW_PAGE_SIZE >= total || detached);
    getChildren().add(new HBox(12, previous, Ui.label("Trang " + page), next));
    for (var review : (pageData == null ? r.path("review") : pageData.path("review"))) {
      getChildren().add(new ReviewCard(review, r.path("players"), s.selfUserId()));
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
