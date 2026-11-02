package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import vn.edu.nhom7.quiz.common.protocol.*;

/** A separate review page that accumulates the server's bounded review pages in order. */
public final class MatchHistoryDetailView extends BorderPane implements AutoCloseable {
  private final UUID matchId;
  private final long self;
  private final UiCommandSink sink;
  private final VBox questions = new VBox(14);
  private final List<ReviewCard> cards = new ArrayList<>();
  private final Label progress = Ui.muted("Đang tải câu hỏi…");
  private final Label note = Ui.muted("");
  private final Button retry = Ui.secondary("Thử tải lại", this::requestPage);
  private final Button toggle = Ui.secondary("Thu gọn tất cả", this::toggleAll);
  private UUID pending;
  private int page = 1;
  private long total = -1;
  private boolean expanded = true;
  private boolean closed;
  // Stay below the server's ten queries/second limit, including other navigation requests.
  private final PauseTransition nextPage = new PauseTransition(Duration.millis(200));
  private final PauseTransition responseTimeout = new PauseTransition(Duration.seconds(5));

  public MatchHistoryDetailView(JsonNode summary, long self, UiCommandSink sink, Runnable back) {
    this.matchId = UUID.fromString(summary.path("matchId").asText());
    this.self = self;
    this.sink = sink;
    nextPage.setOnFinished(e -> requestPage());
    responseTimeout.setOnFinished(e -> timeout());
    setPadding(new Insets(24, 28, 24, 28));
    getStyleClass().add("history-detail-page");
    String date =
        summary.path("endedAtMs").asLong() > 0
            ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(summary.path("endedAtMs").asLong()))
            : "";
    var score =
        Ui.title(
            "Bạn "
                + summary.path("ownScore").asInt()
                + " : "
                + summary.path("opponentScore").asInt()
                + " "
                + summary.path("opponentName").asText("Đối thủ"));
    score.getStyleClass().add("history-score");
    var heading =
        new VBox(
            10,
            Ui.actions(Ui.secondary("← Lịch sử trận đấu", back), Ui.spacer()),
            Ui.kicker("CHI TIẾT TRẬN ĐẤU"),
            Ui.title(summary.path("quizTitle").asText()),
            Ui.muted(
                date
                    + " · "
                    + summary.path("totalRounds").asInt()
                    + " câu · "
                    + (summary.path("ranked").asBoolean(true) ? "Tính hạng" : "Không tính hạng")),
            Ui.actions(
                score, Ui.badge(PresentationText.outcome(summary.path("outcome").asText()))));
    if (summary.hasNonNull("reasonCode"))
      heading
          .getChildren()
          .add(Ui.muted(PresentationText.reason(summary.path("reasonCode").asText())));
    heading.getChildren().add(Ui.actions(progress, Ui.spacer(), toggle, retry));
    heading.getChildren().add(note);
    heading.setPadding(new Insets(0, 0, 16, 0));
    setTop(heading);
    setCenter(Ui.scroll(questions));
    showRetry(false);
    toggle.setDisable(true);
    note.managedProperty().bind(note.textProperty().isNotEmpty());
    note.visibleProperty().bind(note.managedProperty());
  }

  public void load() {
    if (cards.isEmpty() && total < 0 && pending == null) requestPage();
  }

  private void requestPage() {
    if (closed || pending != null) return;
    showRetry(false);
    progress.setText(
        total < 0 ? "Đang tải câu hỏi…" : "Đang tải " + cards.size() + " / " + total + " câu hỏi…");
    pending =
        sink.send(
            MessageType.MATCH_DETAIL_REQUEST,
            null,
            null,
            new Payloads.MatchDetailRequest(matchId, page));
    if (pending == null) failed("Không gửi được yêu cầu. Hãy kiểm tra kết nối và thử lại.");
    else responseTimeout.playFromStart();
  }

  public void onEvent(Envelope event) {
    if (closed || pending == null || !pending.equals(event.requestId())) return;
    if (event.type() == MessageType.ERROR) {
      failed("Không tải được chi tiết trận. " + event.payload().path("message").asText());
      return;
    }
    if (event.type() != MessageType.MATCH_DETAIL) return;
    responseTimeout.stop();
    JsonNode detail = event.payload();
    if (!matchId.toString().equals(detail.path("summary").path("matchId").asText())
        || detail.path("page").asInt(1) != page) {
      failed("Dữ liệu chi tiết không khớp trận đã chọn. Vui lòng thử lại.");
      return;
    }
    long expected = detail.path("totalItems").asLong(-1);
    JsonNode review = detail.path("review");
    if (expected < 0
        || !review.isArray()
        || cards.size() + review.size() > expected
        || (review.isEmpty() && cards.size() < expected)
        || (total >= 0 && total != expected)) {
      failed("Chưa tải đủ dữ liệu câu hỏi. Vui lòng thử lại.");
      return;
    }
    pending = null;
    total = expected;
    var summary = detail.path("summary");
    var players = JsonNodeFactory.instance.arrayNode();
    players
        .addObject()
        .put("userId", summary.path("opponentUserId").asLong())
        .put("displayName", summary.path("opponentName").asText("Đối thủ"));
    for (var question : review) {
      var card = new ReviewCard(question, players, self);
      card.setExpanded(expanded);
      cards.add(card);
      questions.getChildren().add(card);
    }
    toggle.setDisable(cards.isEmpty());
    if (cards.size() < total) {
      page++;
      progress.setText("Đang tải " + cards.size() + " / " + total + " câu hỏi…");
      nextPage.playFromStart();
    } else {
      progress.setText("Đã tải toàn bộ " + total + " câu hỏi");
      if (total == 0) questions.getChildren().add(Ui.label("Trận này chưa có câu hỏi được lưu."));
      if (total < summary.path("totalRounds").asInt())
        note.setText("Trận kết thúc sớm. Chỉ những câu đã mở trong trận được lưu để xem lại.");
    }
  }

  public void timeout() {
    if (pending != null) failed("Tải chi tiết quá thời gian chờ. Bạn có thể thử lại.");
  }

  private void failed(String message) {
    responseTimeout.stop();
    pending = null;
    progress.setText(message);
    showRetry(true);
  }

  private void showRetry(boolean show) {
    retry.setVisible(show);
    retry.setManaged(show);
  }

  private void toggleAll() {
    expanded = cards.stream().anyMatch(card -> !card.expanded());
    cards.forEach(card -> card.setExpanded(expanded));
    toggle.setText(expanded ? "Thu gọn tất cả" : "Mở tất cả");
  }

  @Override
  public void close() {
    closed = true;
    pending = null;
    nextPage.stop();
    responseTimeout.stop();
  }
}
