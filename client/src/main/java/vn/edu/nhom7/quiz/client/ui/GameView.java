package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class GameView extends BorderPane {
  private final Label timer = Ui.label("");
  private final Label scores = Ui.label("");
  private final Label notice = Ui.label("");
  private final Label countdown = Ui.label("");
  private final ProgressBar progress = new ProgressBar();
  private final Label bonus = Ui.label("");
  private final QuestionRenderer renderer;
  private final long received, remaining;
  private final ClientState bound;
  private LeaderboardPanel leaderboard;

  public GameView(ClientState s, UiCommandSink sink, boolean reducedMotion) {
    bound = s;
    received = s.receivedNanos();
    setPadding(new Insets(24));
    getStyleClass().add("game-surface");
    var questionEvent = s.payload(MessageType.QUESTION);
    int roundIndex = questionEvent == null ? 0 : questionEvent.path("roundIndex").asInt();
    var header =
        new HBox(
            20,
            scores,
            Ui.label(
                "Câu "
                    + roundIndex
                    + " / "
                    + (s.payload(MessageType.MATCH_START) == null
                        ? 10
                        : s.payload(MessageType.MATCH_START).path("totalRounds").asInt(10))),
            timer,
            bonus);
    header.getStyleClass().add("game-header");
    scores.getStyleClass().add("score-line");
    scores.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(scores, Priority.ALWAYS);
    timer.getStyleClass().add("timer");
    countdown.getStyleClass().add("countdown-number");
    progress.setMaxWidth(Double.MAX_VALUE);
    setTop(new VBox(8, header, progress));
    BorderPane.setMargin(getTop(), new Insets(0, 0, 20, 0));
    JsonNode timing =
        s.payload(
            s.phase().equals("OPEN")
                ? MessageType.QUESTION_OPEN
                : s.phase().equals("ROUND_COUNTDOWN")
                    ? MessageType.ROUND_COUNTDOWN
                    : s.phase().equals("MATCH_COUNTDOWN")
                        ? MessageType.MATCH_COUNTDOWN
                        : s.phase().equals("REVEAL")
                            ? MessageType.QUESTION_RESULT
                            : MessageType.ROUND_LEADERBOARD);
    remaining =
        s.payload(MessageType.MATCH_SNAPSHOT) != null
            ? s.payload(MessageType.MATCH_SNAPSHOT).path("phaseRemainingMs").asLong()
            : timing == null
                ? 0
                : timing
                    .path("remainingMs")
                    .asLong(
                        timing.has("phaseEndsAtMs")
                            ? Math.max(
                                0,
                                timing.path("phaseEndsAtMs").asLong()
                                    - timing.path("serverTimeMs").asLong())
                            : timing.path("durationMs").asLong());
    if (s.phase().equals("LEADERBOARD")) {
      renderer = null;
      leaderboard =
          new LeaderboardPanel(
              s.payload(MessageType.ROUND_LEADERBOARD),
              s.selfUserId(),
              reducedMotion || s.payload(MessageType.MATCH_SNAPSHOT) != null);
      setCenter(Ui.scroll(leaderboard));
    } else if (s.phase().equals("MATCH_COUNTDOWN")) {
      renderer = null;
      var start =
          new VBox(
              24, Ui.title("Trận đấu sắp bắt đầu"), countdown, Ui.label("Chuẩn bị tinh thần!"));
      start.setAlignment(Pos.CENTER);
      start.getStyleClass().add("waiting-panel");
      setCenter(start);
    } else {
      JsonNode q = s.question();
      if (q == null || q.isNull()) {
        renderer = null;
        setCenter(Ui.label("Đang đồng bộ câu hỏi…"));
      } else {
        var box = new VBox(20);
        box.getStyleClass().add("question-panel");
        box.setPadding(new Insets(24));
        String content = q.path("content").asText();
        var title = Ui.title(content);
        title.getStyleClass().add("question-title");
        if (content.codePointCount(0, content.length()) > 240)
          title.getStyleClass().add("long-question");
        String hint =
            switch (q.path("questionType").asText()) {
              case "MULTIPLE_CHOICE" ->
                  "Nhiều đáp án · Chọn tất cả phương án đúng, rồi gửi lựa chọn";
              case "TRUE_FALSE" -> "Đúng / Sai · Chọn để gửi câu trả lời";
              case "SHORT_ANSWER" -> "Trả lời ngắn · Nhập đáp án và nhấn Enter để gửi";
              default -> "Một đáp án · Chọn phương án đúng (phím A–F)";
            };
        if (s.phase().equals("REVEAL")) hint = "Công bố đáp án · Cùng xem lại câu trả lời";
        else if (s.phase().equals("QUESTION_PREPARE"))
          hint = "Đang tải câu hỏi · Thời gian trả lời chưa bắt đầu";
        else if (s.phase().equals("ROUND_COUNTDOWN"))
          hint = "Chuẩn bị trả lời · Chờ đồng hồ bắt đầu";
        box.getChildren().addAll(Ui.badge(hint), title);
        if (q.hasNonNull("questionAssetId"))
          box.getChildren().add(new AssetLoader().view(q.path("questionAssetId").asText(), 260));
        renderer =
            switch (q.path("questionType").asText()) {
              case "MULTIPLE_CHOICE" -> new MultipleChoiceRenderer();
              case "TRUE_FALSE" -> new TrueFalseRenderer();
              case "SHORT_ANSWER" -> new ShortAnswerRenderer();
              default -> new SingleChoiceRenderer();
            };
        try {
          var question = new ObjectMapper().treeToValue(q, Payloads.PublicQuestion.class);
          renderer.bind(
              question,
              a -> {
                if (s.canAnswer())
                  sink.send(
                      MessageType.ANSWER,
                      s.activeMatchId(),
                      s.roundId(),
                      new Payloads.Answer(question.questionId(), a));
              });
          if (renderer instanceof BaseQuestionRenderer base
              && s.payload(MessageType.MATCH_START) != null)
            base.setPlayers(s.payload(MessageType.MATCH_START).path("players"));
          renderer.setControlsEnabled(s.canAnswer());
          if (s.phase().equals("REVEAL"))
            renderer.showResult(
                new ObjectMapper()
                    .treeToValue(
                        s.payload(MessageType.QUESTION_RESULT), Payloads.QuestionResult.class),
                s.selfUserId());
          box.getChildren().addAll(renderer.view(), notice);
          if (s.phase().equals("REVEAL"))
            box.getChildren()
                .add(new RevealPanel(s.payload(MessageType.QUESTION_RESULT), q, s.selfUserId()));
        } catch (Exception e) {
          box.getChildren()
              .add(Ui.label("Không thể hiển thị câu hỏi; chờ server kết thúc an toàn."));
        }
        setCenter(Ui.scroll(box));
      }
    }
    setOnKeyPressed(
        e -> {
          if (getScene() != null
              && getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl)
            return;
          if (renderer instanceof SingleChoiceRenderer single
              && bound.canAnswer()
              && e.getCode().isLetterKey()) {
            String key = e.getCode().getName();
            if (key.length() == 1) {
              single.activateOption(key.charAt(0));
              e.consume();
            }
          }
        });
    render(s);
    tick();
  }

  public void render(ClientState s) {
    if (renderer != null) renderer.setControlsEnabled(s.canAnswer());
    notice.setText(
        !s.phase().equals("OPEN")
            ? ""
            : s.accepted()
                ? "✓ Đã nhận câu trả lời · chờ kết quả"
                : s.ownPending() ? "Đang chờ server xác nhận…" : "");
    notice.setManaged(!notice.getText().isEmpty());
    JsonNode p = s.payload(MessageType.MATCH_START);
    if (p != null) {
      var names = new ArrayList<String>();
      JsonNode standings = s.payload(MessageType.ROUND_LEADERBOARD);
      for (var player : (standings == null ? p.path("players") : standings.path("standings")))
        names.add(player.path("displayName").asText() + "  " + player.path("totalScore").asInt());
      scores.setText(String.join("     ·     ", names));
    }
  }

  public void dispose() {
    if (leaderboard != null) leaderboard.dispose();
  }

  public static long remainingAt(long initial, long receivedNanos, long nowNanos) {
    return Math.max(0, initial - Math.max(0, nowNanos - receivedNanos) / 1_000_000);
  }

  public void tick() {
    long left = remainingAt(remaining, received, System.nanoTime());
    bonus.setText(
        bound.phase().equals("OPEN")
            ? (left >= 10000
                ? "Thưởng dự kiến: 3 điểm"
                : left >= 5000
                    ? "Thưởng dự kiến: 2 điểm"
                    : left > 0 ? "Thưởng dự kiến: 1 điểm" : "")
            : "");
    timer.setText(left > 0 ? ((left + 999) / 1000) + " giây" : "Đang chuyển…");
    countdown.setText(left > 0 ? Long.toString((left + 999) / 1000) : "Sẵn sàng");
    progress.setProgress(remaining <= 0 ? 0 : Math.min(1, (double) left / remaining));
    if (left == 0 && bound.phase().equals("OPEN") && renderer != null)
      renderer.setControlsEnabled(false);
  }
}
