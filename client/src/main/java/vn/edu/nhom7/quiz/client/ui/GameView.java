package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.util.Duration;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class GameView extends BorderPane {
  private static final double RING = 36;
  private final Label timer = Ui.label("");
  private final Label notice = Ui.label("");
  private final Label countdown = Ui.label("");
  private final Label bonus = Ui.label("");
  private final Label selfScore = Ui.label("0"), opponentScore = Ui.label("0");
  private final Label selfGain = Ui.label(""), opponentGain = Ui.label("");
  private final Label opponentStatus = Ui.label("");
  private final Arc ringBar = new Arc(0, 0, RING, RING, 90, 0);
  private final StackPane ring = new StackPane();
  private final DoubleProperty selfShare = new SimpleDoubleProperty();
  private final DoubleProperty opponentShare = new SimpleDoubleProperty();
  private final QuestionRenderer renderer;
  private final long received, remaining;
  private final ClientState bound;
  private final boolean reducedMotion;
  private final List<Animation> animations = new ArrayList<>();
  private ScaleTransition pulse;
  private String lastCountdown = "";
  private long selfId, opponentId;
  private int maxScore;
  private LeaderboardPanel leaderboard;
  private final HBox selfStreak = new HBox(), opponentStreak = new HBox();
  private int shownSelfStreak = -1, shownOpponentStreak = -1;
  private long lastTickSecond = -1;
  private final Effects.Layer fx = new Effects.Layer();
  /** The round whose reveal already played its sounds and effects, so rebuilds stay quiet. */
  static UUID revealedRound;

  public GameView(ClientState s, UiCommandSink sink, boolean reducedMotion) {
    bound = s;
    this.reducedMotion = reducedMotion;
    received = s.receivedNanos();
    setPadding(new Insets(20, 28, 24, 28));
    getStyleClass().add("game-surface");
    var questionEvent = s.payload(MessageType.QUESTION);
    int roundIndex = questionEvent == null ? 0 : questionEvent.path("roundIndex").asInt();
    JsonNode start = s.payload(MessageType.MATCH_START);
    int totalRounds = start == null ? 10 : start.path("totalRounds").asInt(10);
    maxScore = Math.max(3, totalRounds * 3);
    setTop(versusBar(s, start, roundIndex, totalRounds));
    BorderPane.setMargin(getTop(), new Insets(0, 0, 18, 0));
    timer.getStyleClass().add("ring-label");
    countdown.getStyleClass().add("countdown-number");
    bonus.getStyleClass().add("bonus-text");
    notice.getStyleClass().add("light-label");
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
      var box =
          new VBox(
              24, Ui.title("Trận đấu sắp bắt đầu"), countdown, Ui.label("Chuẩn bị tinh thần!"));
      box.setAlignment(Pos.CENTER);
      box.getStyleClass().add("waiting-panel");
      setCenter(box);
    } else {
      JsonNode q = s.question();
      if (q == null || q.isNull()) {
        renderer = null;
        setCenter(Ui.label("Đang đồng bộ câu hỏi…"));
      } else {
        var card = new VBox(12);
        card.getStyleClass().add("question-panel");
        String content = q.path("content").asText();
        var title = Ui.title(content);
        title.getStyleClass().add("question-title");
        if (content.codePointCount(0, content.length()) > 240)
          title.getStyleClass().add("long-question");
        String hint =
            switch (q.path("questionType").asText()) {
              case "MULTIPLE_CHOICE" ->
                  "Nhiều đáp án · Chọn tất cả phương án đúng, rồi gửi lựa chọn";
              case "TRUE_FALSE" -> "Đúng / Sai · Phím 1 hoặc 2";
              case "SHORT_ANSWER" -> "Trả lời ngắn · Nhập đáp án và nhấn Enter để gửi";
              default -> "Một đáp án · Phím A–D hoặc 1–4";
            };
        if (s.phase().equals("REVEAL"))
          hint = "Đáp án: " + PresentationText.answer(q, revealAnswer(s));
        else if (s.phase().equals("QUESTION_PREPARE"))
          hint = "Đang tải câu hỏi · Thời gian trả lời chưa bắt đầu";
        else if (s.phase().equals("ROUND_COUNTDOWN"))
          hint = "Chuẩn bị trả lời · Chờ đồng hồ bắt đầu";
        var badgeRow = new HBox(12, Ui.badge(hint), Ui.spacer(), bonus);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(badgeRow, title);
        if (q.hasNonNull("questionAssetId"))
          card.getChildren().add(new AssetLoader().view(q.path("questionAssetId").asText(), 260));
        var body = new VBox(16, card);
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
          if (renderer instanceof BaseQuestionRenderer base && start != null)
            base.setPlayers(start.path("players"));
          renderer.setControlsEnabled(s.canAnswer());
          if (s.phase().equals("REVEAL"))
            renderer.showResult(
                new ObjectMapper()
                    // The client adds its own fields (streaks) to the stored result.
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .treeToValue(
                        s.payload(MessageType.QUESTION_RESULT), Payloads.QuestionResult.class),
                s.selfUserId());
          body.getChildren().addAll(renderer.view(), notice);
          if (s.phase().equals("REVEAL"))
            body.getChildren()
                .add(
                    new RevealPanel(
                        s.payload(MessageType.QUESTION_RESULT),
                        q,
                        s.selfUserId(),
                        start == null ? null : start.path("players")));
        } catch (Exception e) {
          body.getChildren()
              .add(Ui.label("Không thể hiển thị câu hỏi; chờ server kết thúc an toàn."));
        }
        setCenter(Ui.scroll(body));
        if (!reducedMotion && s.phase().equals("QUESTION_PREPARE")) enter(card, 0);
        if (!reducedMotion && s.phase().equals("REVEAL"))
          javafx.application.Platform.runLater(() -> celebrate(body));
      }
    }
    setOnKeyPressed(
        e -> {
          if (getScene() != null
              && getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl)
            return;
          if (!bound.canAnswer()) return;
          String key = e.getCode().getName();
          if (key.length() != 1) key = e.getText();
          if (key == null || key.length() != 1) return;
          char c = key.charAt(0);
          if (renderer instanceof SingleChoiceRenderer single
              && (Character.isLetter(c) || Character.isDigit(c))) {
            single.activateOption(c);
            e.consume();
          } else if (renderer instanceof TrueFalseRenderer tf && (c == '1' || c == '2')) {
            tf.activate(c - '1');
            e.consume();
          }
        });
    getChildren().add(fx);
    render(s);
    tick();
    if (s.phase().equals("REVEAL") && s.roundId() != null && !s.roundId().equals(revealedRound)) {
      revealedRound = s.roundId();
      javafx.application.Platform.runLater(() -> revealFeedback(s));
    }
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();
    fx.resizeRelocate(0, 0, getWidth(), getHeight());
    if (getChildren().getLast() != fx) fx.toFront();
  }

  static int streak(ClientState s, long userId) {
    JsonNode result = s.payload(MessageType.QUESTION_RESULT);
    return result == null ? 0 : result.path("streaks").path(Long.toString(userId)).asInt(0);
  }

  /** Right/wrong sound, a red flash on a miss and the streak banner at 3, 5 and 10. */
  private void revealFeedback(ClientState s) {
    boolean right = false;
    JsonNode result = s.payload(MessageType.QUESTION_RESULT);
    if (result != null)
      for (var o : result.path("outcomes"))
        if (o.path("userId").asLong() == selfId) right = o.path("correct").asBoolean(false);
    Sound.play(right ? Sound.Effect.CORRECT : Sound.Effect.WRONG);
    if (!right && !reducedMotion) Effects.wrongFlash(fx, animations);
    int streak = streak(s, selfId);
    if (right && (streak == 3 || streak == 5 || streak == 10)) {
      var later = new PauseTransition(Duration.millis(Effects.preview ? 0 : 260));
      later.setOnFinished(
          e -> {
            Sound.play(Sound.Effect.STREAK);
            if (!reducedMotion) Effects.streakBanner(fx, streak, animations);
          });
      if (Effects.preview && !reducedMotion) Effects.streakBanner(fx, streak, animations);
      else play(later);
    }
  }

  private static JsonNode revealAnswer(ClientState s) {
    var result = s.payload(MessageType.QUESTION_RESULT);
    return result == null ? null : result.path("correctAnswer");
  }

  /** Avatars, scores and timer ring for both players; the local player is always on the left. */
  private Node versusBar(ClientState s, JsonNode start, int roundIndex, int totalRounds) {
    JsonNode self = null, opponent = null;
    if (start != null)
      for (var p : start.path("players"))
        if (p.path("userId").asLong() == s.selfUserId()) self = p;
        else opponent = p;
    selfId = self == null ? s.selfUserId() : self.path("userId").asLong();
    opponentId = opponent == null ? -1 : opponent.path("userId").asLong();
    var left =
        player(
            self,
            (self == null ? "Bạn" : self.path("displayName").asText()) + " · Bạn",
            selfScore,
            selfGain,
            null,
            false);
    var right =
        player(
            opponent,
            opponent == null ? "Đối thủ" : opponent.path("displayName").asText(),
            opponentScore,
            opponentGain,
            opponentStatus,
            true);
    // Fixed-size pane so the partial arc stays centred on the track.
    double c = RING + 6;
    var track = new Circle(c, c, RING, javafx.scene.paint.Color.TRANSPARENT);
    track.getStyleClass().add("timer-ring-track");
    ringBar.setCenterX(c);
    ringBar.setCenterY(c);
    ringBar.setType(ArcType.OPEN);
    ringBar.getStyleClass().add("timer-ring-bar");
    var dial = new Pane(track, ringBar);
    dial.setMinSize(2 * c, 2 * c);
    dial.setMaxSize(2 * c, 2 * c);
    ring.getChildren().addAll(dial, timer);
    ring.setMinSize(2 * RING + 12, 2 * RING + 12);
    ring.setMaxSize(2 * RING + 12, 2 * RING + 12);
    var round = Ui.label("Câu " + roundIndex + " / " + totalRounds);
    round.getStyleClass().add("round-label");
    var center = new VBox(6, ring, round);
    center.setAlignment(Pos.CENTER);
    var grid = new GridPane();
    grid.setHgap(24);
    var side = new ColumnConstraints();
    side.setHgrow(Priority.ALWAYS);
    side.setPercentWidth(40);
    var middle = new ColumnConstraints();
    middle.setPercentWidth(20);
    middle.setHalignment(HPos.CENTER);
    var side2 = new ColumnConstraints();
    side2.setPercentWidth(40);
    side2.setHalignment(HPos.RIGHT);
    grid.getColumnConstraints().addAll(side, middle, side2);
    grid.add(left, 0, 0);
    grid.add(center, 1, 0);
    grid.add(right, 2, 0);
    var race = new HBox();
    race.getStyleClass().add("race-track");
    race.setMinHeight(8);
    race.setMaxHeight(8);
    var selfBar = new Region();
    selfBar.getStyleClass().add("race-self");
    var opponentBar = new Region();
    opponentBar.getStyleClass().add("race-opponent");
    selfBar.prefWidthProperty().bind(race.widthProperty().multiply(selfShare).multiply(0.5));
    opponentBar.prefWidthProperty().bind(race.widthProperty().multiply(opponentShare).multiply(0.5));
    selfBar.minWidthProperty().bind(selfBar.prefWidthProperty());
    opponentBar.minWidthProperty().bind(opponentBar.prefWidthProperty());
    race.getChildren().addAll(selfBar, Ui.spacer(), opponentBar);
    var bar = new VBox(14, grid, race);
    bar.getStyleClass().add("versus-bar");
    return bar;
  }

  private HBox player(
      JsonNode player, String name, Label score, Label gain, Label status, boolean mirrored) {
    var avatar =
        new AssetLoader().view(player == null ? "avatar-01" : player.path("avatarId").asText(), 60);
    var avatarFrame = new StackPane(avatar);
    avatarFrame.getStyleClass().addAll("versus-avatar", mirrored ? "opponent" : "self");
    avatarFrame.setMinSize(74, 74);
    avatarFrame.setMaxSize(74, 74);
    var nameLabel = Ui.label(name);
    nameLabel.getStyleClass().add("versus-name");
    nameLabel.setWrapText(false);
    nameLabel.setMinWidth(0);
    var streakSlot = mirrored ? opponentStreak : selfStreak;
    streakSlot.setAlignment(Pos.CENTER);
    streakSlot.setMinWidth(Region.USE_PREF_SIZE);
    var nameRow = mirrored ? new HBox(8, streakSlot, nameLabel) : new HBox(8, nameLabel, streakSlot);
    nameRow.setAlignment(mirrored ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    score.getStyleClass().add("versus-score");
    gain.getStyleClass().add("gain-pill");
    gain.managedProperty().bind(gain.textProperty().isNotEmpty());
    gain.visibleProperty().bind(gain.managedProperty());
    var text = new VBox(2, nameRow, score);
    text.setAlignment(mirrored ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    if (status != null) {
      status.getStyleClass().add("opponent-status");
      status.managedProperty().bind(status.textProperty().isNotEmpty());
      status.visibleProperty().bind(status.managedProperty());
      text.getChildren().add(1, status);
    }
    var row =
        mirrored ? new HBox(16, gain, text, avatarFrame) : new HBox(16, avatarFrame, text, gain);
    row.setAlignment(mirrored ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    return row;
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
    var totals = new HashMap<Long, Integer>();
    JsonNode standings = s.payload(MessageType.ROUND_LEADERBOARD);
    if (standings != null)
      for (var st : standings.path("standings"))
        totals.put(st.path("userId").asLong(), st.path("totalScore").asInt());
    var gains = new HashMap<Long, Integer>();
    JsonNode result = s.payload(MessageType.QUESTION_RESULT);
    // During the reveal the round's points are known but not yet in the standings.
    if (s.phase().equals("REVEAL") && result != null)
      for (var o : result.path("outcomes"))
        gains.put(o.path("userId").asLong(), o.path("earnedPoints").asInt());
    int self = totals.getOrDefault(selfId, 0) + gains.getOrDefault(selfId, 0);
    int opponent = totals.getOrDefault(opponentId, 0) + gains.getOrDefault(opponentId, 0);
    selfScore.setText(Integer.toString(self));
    opponentScore.setText(Integer.toString(opponent));
    selfGain.setText(gains.containsKey(selfId) ? "+" + gains.get(selfId) : "");
    opponentGain.setText(gains.containsKey(opponentId) ? "+" + gains.get(opponentId) : "");
    opponentGain.getStyleClass().remove("zero");
    if (gains.getOrDefault(opponentId, 1) == 0) opponentGain.getStyleClass().add("zero");
    selfGain.getStyleClass().remove("zero");
    if (gains.getOrDefault(selfId, 1) == 0) selfGain.getStyleClass().add("zero");
    selfShare.set(Math.min(1, (double) self / maxScore));
    opponentShare.set(Math.min(1, (double) opponent / maxScore));
    boolean opponentAnswered = false;
    JsonNode answered = s.payload(MessageType.ANSWER_STATUS);
    if (answered != null)
      for (var id : answered.path("answeredUserIds"))
        if (id.asLong() == opponentId) opponentAnswered = true;
    opponentStatus.setText(
        !s.phase().equals("OPEN") ? "" : opponentAnswered ? "✓ đã trả lời" : "đang suy nghĩ…");
    opponentStatus.getStyleClass().remove("answered");
    if (opponentAnswered) opponentStatus.getStyleClass().add("answered");
    int mine = streak(s, selfId), theirs = streak(s, opponentId);
    if (mine != shownSelfStreak) showStreak(selfStreak, shownSelfStreak = mine);
    if (theirs != shownOpponentStreak) showStreak(opponentStreak, shownOpponentStreak = theirs);
  }

  /** Flame badge from three correct answers in a row; its colour climbs at 5 and at 10. */
  private void showStreak(HBox slot, int streak) {
    slot.getChildren().clear();
    if (Effects.tier(streak) == 0) return;
    var badge = Effects.streakBadge(streak, !reducedMotion, animations);
    slot.getChildren().add(badge);
    if (!reducedMotion && bound.phase().equals("REVEAL")) {
      var pop = new ScaleTransition(Duration.millis(420), badge);
      pop.setFromX(0.3);
      pop.setFromY(0.3);
      pop.setToX(1);
      pop.setToY(1);
      pop.setInterpolator(Effects.BACK_OUT);
      play(pop);
    }
  }

  private void enter(Node node, int delayMs) {
    node.setOpacity(0);
    var fade = new FadeTransition(Duration.millis(260), node);
    fade.setFromValue(0);
    fade.setToValue(1);
    var slide = new TranslateTransition(Duration.millis(260), node);
    slide.setFromY(14);
    slide.setToY(0);
    slide.setInterpolator(Interpolator.EASE_OUT);
    var both = new ParallelTransition(fade, slide);
    both.setDelay(Duration.millis(delayMs));
    play(both);
  }

  /** Reveal: the correct tile pops, a wrong pick shakes, points float in, the race bar moves. */
  private void celebrate(Parent body) {
    for (var node : body.lookupAll(".answer-card")) {
      if (node.getStyleClass().contains("correct")) {
        var pop = new ScaleTransition(Duration.millis(160), node);
        pop.setToX(1.03);
        pop.setToY(1.03);
        pop.setAutoReverse(true);
        pop.setCycleCount(4);
        play(pop);
      } else if (node.getStyleClass().contains("incorrect")) {
        var shake =
            new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60), new KeyValue(node.translateXProperty(), -9)),
                new KeyFrame(Duration.millis(120), new KeyValue(node.translateXProperty(), 9)),
                new KeyFrame(Duration.millis(180), new KeyValue(node.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(240), new KeyValue(node.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(300), new KeyValue(node.translateXProperty(), 0)));
        play(shake);
      }
    }
    for (var gain : List.of(selfGain, opponentGain)) if (gain.isVisible()) enter(gain, 150);
    double selfTarget = selfShare.get(), opponentTarget = opponentShare.get();
    int selfBefore = Integer.parseInt(selfScore.getText()) - parseGain(selfGain);
    int opponentBefore = Integer.parseInt(opponentScore.getText()) - parseGain(opponentGain);
    var race =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(selfShare, Math.min(1, (double) selfBefore / maxScore)),
                new KeyValue(opponentShare, Math.min(1, (double) opponentBefore / maxScore))),
            new KeyFrame(
                Duration.millis(600),
                new KeyValue(selfShare, selfTarget, Interpolator.EASE_OUT),
                new KeyValue(opponentShare, opponentTarget, Interpolator.EASE_OUT)));
    play(race);
  }

  private static int parseGain(Label gain) {
    return gain.getText().isEmpty() ? 0 : Integer.parseInt(gain.getText().substring(1));
  }

  private void play(Animation a) {
    animations.add(a);
    a.play();
  }

  public void dispose() {
    animations.forEach(Animation::stop);
    if (pulse != null) pulse.stop();
    if (leaderboard != null) leaderboard.dispose();
  }

  public static long remainingAt(long initial, long receivedNanos, long nowNanos) {
    return Math.max(0, initial - Math.max(0, nowNanos - receivedNanos) / 1_000_000);
  }

  public void tick() {
    long left = remainingAt(remaining, received, System.nanoTime());
    boolean open = bound.phase().equals("OPEN");
    bonus.setText(
        open
            ? (left >= 10000
                ? "Thưởng hiện tại: 3 điểm"
                : left >= 5000
                    ? "Thưởng hiện tại: 2 điểm"
                    : left > 0 ? "Thưởng hiện tại: 1 điểm" : "")
            : "");
    timer.setText(
        bound.phase().equals("REVEAL")
            ? "✓"
            : left > 0 ? Long.toString((left + 999) / 1000) : "…");
    String count = left > 0 ? Long.toString((left + 999) / 1000) : "Sẵn sàng";
    if (!count.equals(lastCountdown)) {
      lastCountdown = count;
      countdown.setText(count);
      if (!reducedMotion && bound.phase().equals("MATCH_COUNTDOWN")) {
        var pop = new ScaleTransition(Duration.millis(220), countdown);
        pop.setFromX(1.4);
        pop.setFromY(1.4);
        pop.setToX(1);
        pop.setToY(1);
        play(pop);
      }
    }
    ringBar.setLength(remaining <= 0 ? 0 : -360 * Math.min(1, (double) left / remaining));
    boolean urgent = open && left > 0 && left <= 5000;
    long second = (left + 999) / 1000;
    if (urgent && second != lastTickSecond) {
      lastTickSecond = second;
      Sound.play(Sound.Effect.TICK);
    }
    if (urgent != ring.getStyleClass().contains("urgent")) {
      if (urgent) {
        ring.getStyleClass().add("urgent");
        if (!reducedMotion) {
          pulse = new ScaleTransition(Duration.millis(450), ring);
          pulse.setToX(1.08);
          pulse.setToY(1.08);
          pulse.setAutoReverse(true);
          pulse.setCycleCount(Animation.INDEFINITE);
          pulse.play();
        }
      } else {
        ring.getStyleClass().remove("urgent");
        if (pulse != null) {
          pulse.stop();
          ring.setScaleX(1);
          ring.setScaleY(1);
        }
      }
    }
    if (left == 0 && open && renderer != null) renderer.setControlsEnabled(false);
  }
}
