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
  private static final double TIMER = 104;
  private final Label timer = Ui.label("");
  private final Label notice = Ui.label("");
  private final Label countdown = Ui.label("");
  private final List<Label> bonusChips = new ArrayList<>();
  private final Label selfScore = Ui.label("0"), opponentScore = Ui.label("0");
  private final Label selfGain = Ui.label(""), opponentGain = Ui.label("");
  private final Label opponentStatus = Ui.label("");
  private final Region drain = new Region();
  private final StackPane ring = new StackPane();
  private final HBox prompts = new HBox(20);
  private final DoubleProperty selfShare = new SimpleDoubleProperty();
  private final DoubleProperty opponentShare = new SimpleDoubleProperty();
  private final QuestionRenderer renderer;
  private final long received, remaining;
  private final ClientState bound;
  private final boolean reducedMotionFlag;
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
  private final LifelineDock lifelines;
  private ClientState current;

  /** The round whose reveal already played its sounds and effects, so rebuilds stay quiet. */
  static UUID revealedRound;

  public GameView(ClientState state, UiCommandSink sink, boolean reducedMotion) {
    bound = state;
    current = state;
    lifelines = new LifelineDock(sink, state, this::reducedMotion, animations, this::requestLayout);
    this.reducedMotionFlag = reducedMotion;
    received = state.receivedNanos();
    getStyleClass().add("game-surface");
    var questionEvent = state.payload(MessageType.QUESTION);
    int roundIndex = questionEvent == null ? 0 : questionEvent.path("roundIndex").asInt();
    JsonNode start = state.payload(MessageType.MATCH_START);
    int totalRounds = start == null ? 10 : start.path("totalRounds").asInt(10);
    maxScore = Math.max(3, totalRounds * 3);
    setTop(hud(state, start, roundIndex, totalRounds));
    countdown.getStyleClass().add("countdown-number");
    notice.getStyleClass().add("self-status");
    JsonNode timing =
        state.payload(
            state.phase().equals("OPEN")
                ? MessageType.QUESTION_OPEN
                : state.phase().equals("ROUND_COUNTDOWN")
                    ? MessageType.ROUND_COUNTDOWN
                    : state.phase().equals("MATCH_COUNTDOWN")
                        ? MessageType.MATCH_COUNTDOWN
                        : state.phase().equals("REVEAL")
                            ? MessageType.QUESTION_RESULT
                            : MessageType.ROUND_LEADERBOARD);
    remaining =
        state.payload(MessageType.MATCH_SNAPSHOT) != null
            ? state.payload(MessageType.MATCH_SNAPSHOT).path("phaseRemainingMs").asLong()
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
    if (state.phase().equals("LEADERBOARD")) {
      renderer = null;
      leaderboard =
          new LeaderboardPanel(
              state.payload(MessageType.ROUND_LEADERBOARD),
              state.selfUserId(),
              reducedMotion() || state.payload(MessageType.MATCH_SNAPSHOT) != null);
      var holder = new StackPane(leaderboard);
      holder.setAlignment(Pos.TOP_CENTER);
      var scroll = Ui.scroll(holder);
      scroll.getStyleClass().add("stage-scroll");
      setCenter(scroll);
    } else if (state.phase().equals("MATCH_COUNTDOWN")) {
      renderer = null;
      var box =
          new VBox(
              24, Ui.title("Trận đấu sắp bắt đầu"), countdown, Ui.label("Chuẩn bị tinh thần!"));
      box.setAlignment(Pos.CENTER);
      box.getStyleClass().add("waiting-panel");
      setCenter(box);
    } else {
      JsonNode question = state.question();
      if (question == null || question.isNull()) {
        renderer = null;
        setCenter(Ui.label("Đang đồng bộ câu hỏi…"));
      } else {
        String type = question.path("questionType").asText();
        var content = new QuestionContent(state, sink, this::lockedIn);
        renderer = content.renderer;
        var stage = content.stage;
        var deck = content.deck;
        lifelines.bind(question, renderer, selfId, opponentId);
        prompts.getStyleClass().add("prompt-bar");
        prompts.setAlignment(Pos.CENTER_LEFT);
        showPrompts(type, state.phase());
        var bottom = new HBox(16, prompts, Ui.spacer(), lifelines);
        bottom.setAlignment(Pos.CENTER_LEFT);
        // Hints are the first thing to give way on a narrow window; the clip hides the overflow.
        prompts.setMinWidth(0);
        var clip = new Rectangle();
        clip.widthProperty().bind(prompts.widthProperty());
        clip.heightProperty().bind(prompts.heightProperty());
        prompts.setClip(clip);
        lifelines.setMinWidth(Region.USE_PREF_SIZE);
        bottom.getStyleClass().add("deck-footer");
        deck.getChildren().add(bottom);
        var scroll = Ui.scroll(stage);
        scroll.getStyleClass().add("stage-scroll");
        // Centre a short question in the free space, scroll a long one.
        stage
            .minHeightProperty()
            .bind(
                javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        setCenter(scroll);
        setBottom(deck);
        if (!reducedMotion() && state.phase().equals("QUESTION_PREPARE")) {
          enter(stage, 0);
          // Medium tier: the question's heading slams in before the clock starts.
          Effects.slam(content.kicker, animations);
        }
        if (!reducedMotion() && state.phase().equals("REVEAL"))
          javafx.application.Platform.runLater(() -> celebrate(deck));
      }
    }
    setOnKeyPressed(
        e -> {
          if (getScene() != null
              && getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl)
            return;
          if (lifelines.handleKey(e)) {
            e.consume();
            return;
          }
          if (e.getCode() == javafx.scene.input.KeyCode.ENTER && current.canAnswer()) {
            if (getScene() != null
                && getScene().getFocusOwner() instanceof Button b
                && b.getStyleClass().contains("answer-tile")) b.fire();
            else if (renderer instanceof MultipleChoiceRenderer multiple) multiple.submit();
            e.consume();
            return;
          }
          String key = e.getCode().getName();
          if (key.length() != 1) key = e.getText();
          if (key == null || key.length() != 1) return;
          char c = key.charAt(0);
          if (!current.canAnswer()) return;
          if (renderer instanceof SingleChoiceRenderer single
              && (Character.isLetter(c) || Character.isDigit(c))) {
            single.activateOption(c);
            e.consume();
          } else if (renderer instanceof TrueFalseRenderer tf && (c == '1' || c == '2')) {
            tf.activate(c - '1');
            e.consume();
          } else if (renderer instanceof MultipleChoiceRenderer multiple
              && (Character.isLetter(c) || Character.isDigit(c))) {
            multiple.toggleOption(c);
            e.consume();
          }
        });
    // Above the question area, which was added after the card.
    getChildren().addAll(lifelines.overlays());
    getChildren().add(fx);
    render(state);
    tick();
    if (state.phase().equals("REVEAL")
        && state.roundId() != null
        && !state.roundId().equals(revealedRound)) {
      revealedRound = state.roundId();
      javafx.application.Platform.runLater(() -> revealFeedback(state));
    }
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();
    lifelines.layoutOverlay(this);
    fx.resizeRelocate(0, 0, getWidth(), getHeight());
    if (getChildren().getLast() != fx) fx.toFront();
  }

  static int streak(ClientState state, long userId) {
    JsonNode result = state.payload(MessageType.QUESTION_RESULT);
    return result == null ? 0 : result.path("streaks").path(Long.toString(userId)).asInt(0);
  }

  /** Right/wrong sound, a red flash on a miss and the streak banner at 3, 5 and 10. */
  private void revealFeedback(ClientState state) {
    boolean right = false;
    JsonNode result = state.payload(MessageType.QUESTION_RESULT);
    if (result != null)
      for (var o : result.path("outcomes"))
        if (o.path("userId").asLong() == selfId) right = o.path("correct").asBoolean(false);
    Sound.play(right ? Sound.Effect.CORRECT : Sound.Effect.WRONG);
    if (!right && !reducedMotion()) Effects.wrongFlash(fx, animations);
    int streak = streak(state, selfId);
    if (right && (streak == 3 || streak == 5 || streak == 10)) {
      var later = new PauseTransition(Duration.millis(Effects.preview ? 0 : 260));
      later.setOnFinished(
          e -> {
            Sound.play(Sound.Effect.STREAK);
            if (!reducedMotion()) {
              Effects.streakBanner(fx, streak, animations);
              Effects.whenLaidOut(fx, this::streakHit);
            }
          });
      if (Effects.preview && !reducedMotion()) {
        Effects.streakBanner(fx, streak, animations);
        Effects.whenLaidOut(fx, this::streakHit);
      } else play(later);
    }
  }

  /**
   * The corner HUD: each player's avatar, score and fighting-game score bar on their side, the
   * timer in the middle. The local player is always on the left.
   */
  private Node hud(ClientState state, JsonNode start, int roundIndex, int totalRounds) {
    JsonNode self = null, opponent = null;
    if (start != null)
      for (var p : start.path("players"))
        if (p.path("userId").asLong() == state.selfUserId()) self = p;
        else opponent = p;
    selfId = self == null ? state.selfUserId() : self.path("userId").asLong();
    opponentId = opponent == null ? -1 : opponent.path("userId").asLong();
    var left =
        player(
            self,
            self == null ? "Bạn" : self.path("displayName").asText(),
            selfScore,
            selfGain,
            notice,
            selfShare,
            false);
    var right =
        player(
            opponent,
            opponent == null ? "Đối thủ" : opponent.path("displayName").asText(),
            opponentScore,
            opponentGain,
            opponentStatus,
            opponentShare,
            true);
    // The time left drains out of the box from the top.
    drain.getStyleClass().add("timer-drain");
    drain.setManaged(false);
    timer.getStyleClass().add("timer-number");
    ring.getStyleClass().add("timer-box");
    ring.getChildren().addAll(drain, timer);
    ring.setMinSize(TIMER, TIMER);
    ring.setMaxSize(TIMER, TIMER);
    ring.setAccessibleRole(AccessibleRole.TEXT);
    var round = Ui.label("CÂU " + roundIndex + " / " + totalRounds);
    round.getStyleClass().add("round-label");
    var chips = new HBox(6);
    chips.setAlignment(Pos.CENTER);
    for (int points = 3; points >= 1; points--) {
      var chip = Ui.label("×" + points);
      chip.getStyleClass().add("bonus-chip");
      bonusChips.add(chip);
      chips.getChildren().add(chip);
    }
    var center = new VBox(6, ring, round, chips);
    center.setAlignment(Pos.TOP_CENTER);
    center.setMinWidth(TIMER + 40);
    var grid = new GridPane();
    grid.setHgap(24);
    // Equal growing sides that may shrink to nothing, so the HUD never forces the window wider.
    var side = new ColumnConstraints(0, 240, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true);
    var middle = new ColumnConstraints(TIMER + 40);
    middle.setHalignment(HPos.CENTER);
    var side2 = new ColumnConstraints(0, 240, Double.MAX_VALUE, Priority.ALWAYS, HPos.RIGHT, true);
    grid.getColumnConstraints().addAll(side, middle, side2);
    grid.add(left, 0, 0);
    grid.add(center, 1, 0);
    grid.add(right, 2, 0);
    GridPane.setValignment(left, VPos.TOP);
    GridPane.setValignment(right, VPos.TOP);
    grid.getStyleClass().add("hud");
    return grid;
  }

  private HBox player(
      JsonNode player,
      String name,
      Label score,
      Label gain,
      Label status,
      DoubleProperty share,
      boolean mirrored) {
    var avatar =
        new AssetLoader().view(player == null ? "avatar-01" : player.path("avatarId").asText(), 60);
    var avatarFrame = new StackPane(avatar);
    avatarFrame.getStyleClass().addAll("versus-avatar", mirrored ? "opponent" : "self");
    avatarFrame.setMinSize(72, 72);
    avatarFrame.setMaxSize(72, 72);
    var nameLabel = Ui.label(name);
    nameLabel.getStyleClass().add("versus-name");
    nameLabel.setWrapText(false);
    nameLabel.setMinWidth(0);
    var streakSlot = mirrored ? opponentStreak : selfStreak;
    streakSlot.setAlignment(Pos.CENTER);
    streakSlot.setMinWidth(Region.USE_PREF_SIZE);
    var extra = mirrored ? lifelines.opponentIndicator() : lifelines.selfIndicator();
    extra.getStyleClass().add(mirrored ? "opponent-lifeline" : "double-chip");
    extra.setMinWidth(Region.USE_PREF_SIZE);
    extra.setVisible(false);
    extra.managedProperty().bind(extra.visibleProperty());
    score.getStyleClass().add("versus-score");
    score.setMinWidth(Region.USE_PREF_SIZE);
    var parts = new ArrayList<Node>(List.of(nameLabel));
    if (!mirrored) {
      var you = Ui.label("BẠN");
      you.getStyleClass().add("you-tag");
      you.setMinWidth(Region.USE_PREF_SIZE);
      parts.add(you);
    }
    parts.addAll(List.of(streakSlot, extra, Ui.spacer(), score));
    if (mirrored) java.util.Collections.reverse(parts);
    var nameRow = new HBox(10, parts.toArray(Node[]::new));
    nameRow.setAlignment(Pos.CENTER_LEFT);
    // Fighting-game bar: each player's bar fills from the outside in, towards the timer.
    var fill = new Region();
    fill.getStyleClass().addAll("score-fill", mirrored ? "opponent" : "self");
    var track = new StackPane(fill);
    track.getStyleClass().add("score-track");
    track.setMinHeight(16);
    track.setMaxHeight(16);
    track.setAlignment(mirrored ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
    fill.maxWidthProperty().bind(track.widthProperty().multiply(share));
    fill.prefWidthProperty().bind(fill.maxWidthProperty());
    fill.setMaxHeight(Double.MAX_VALUE);
    gain.getStyleClass().add("gain-pill");
    gain.managedProperty().bind(gain.textProperty().isNotEmpty());
    gain.visibleProperty().bind(gain.managedProperty());
    status.getStyleClass().add("opponent-status");
    status.setMinWidth(0);
    var caption =
        mirrored ? new HBox(8, gain, Ui.spacer(), status) : new HBox(8, status, Ui.spacer(), gain);
    caption.setAlignment(Pos.CENTER_LEFT);
    caption.setMinHeight(26);
    var text = new VBox(8, nameRow, track, caption);
    HBox.setHgrow(text, Priority.ALWAYS);
    var row = mirrored ? new HBox(16, text, avatarFrame) : new HBox(16, avatarFrame, text);
    row.setAlignment(Pos.TOP_LEFT);
    row.setMinWidth(0);
    text.setMinWidth(0);
    nameRow.setMinWidth(0);
    return row;
  }

  /** The keyboard hints along the bottom, matching what the keys do in this phase. */
  private void showPrompts(String type, String phase) {
    prompts.getChildren().clear();
    if (phase.equals("REVEAL")) {
      prompts.getChildren().add(Ui.prompt(null, "Câu tiếp theo sẽ tự bắt đầu"));
      return;
    }
    int options = renderer instanceof BaseQuestionRenderer base ? base.answerControls.size() : 0;
    switch (type) {
      case "SHORT_ANSWER" -> prompts.getChildren().add(Ui.prompt("Enter", "Gửi câu trả lời"));
      case "TRUE_FALSE" -> prompts.getChildren().add(Ui.prompt("1 / 2", "Đúng / Sai"));
      case "MULTIPLE_CHOICE" ->
          prompts
              .getChildren()
              .addAll(Ui.prompt("1–" + options, "Đánh dấu"), Ui.prompt("Enter", "Khóa đáp án"));
      default ->
          prompts
              .getChildren()
              .addAll(Ui.prompt("1–" + options, "Chọn"), Ui.prompt("Tab", "Di chuyển"));
    }
    if (lifelines.hasQuestion()) prompts.getChildren().add(Ui.prompt("X · L · N", "Trợ giúp"));
    prompts.getChildren().add(Ui.prompt("Esc", "Tạm dừng"));
  }

  public void render(ClientState state) {
    current = state;
    if (renderer != null) renderer.setControlsEnabled(state.canAnswer());
    lifelines.render(state);
    notice.setText(
        !state.phase().equals("OPEN")
            ? ""
            : state.accepted()
                ? "✓ Đã khóa đáp án · chờ kết quả"
                : state.ownPending() ? "Đang gửi đáp án…" : "");
    notice.getStyleClass().remove("answered");
    if (state.accepted() && state.phase().equals("OPEN")) notice.getStyleClass().add("answered");
    var totals = new HashMap<Long, Integer>();
    JsonNode standings = state.payload(MessageType.ROUND_LEADERBOARD);
    if (standings != null)
      for (var st : standings.path("standings"))
        totals.put(st.path("userId").asLong(), st.path("totalScore").asInt());
    var gains = new HashMap<Long, Integer>();
    JsonNode result = state.payload(MessageType.QUESTION_RESULT);
    // During the reveal the round's points are known but not yet in the standings.
    if (state.phase().equals("REVEAL") && result != null)
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
    selfGain.getStyleClass().removeAll("zero", "doubled");
    if (gains.getOrDefault(selfId, 1) == 0) selfGain.getStyleClass().add("zero");
    else if (lifelines.doubled()) selfGain.getStyleClass().add("doubled");
    selfShare.set(Math.min(1, (double) self / maxScore));
    opponentShare.set(Math.min(1, (double) opponent / maxScore));
    boolean opponentAnswered = false;
    JsonNode answered = state.payload(MessageType.ANSWER_STATUS);
    if (answered != null)
      for (var id : answered.path("answeredUserIds"))
        if (id.asLong() == opponentId) opponentAnswered = true;
    opponentStatus.setText(
        !state.phase().equals("OPEN")
            ? ""
            : opponentAnswered ? "✓ Đã khóa đáp án" : "Đang suy nghĩ…");
    opponentStatus.getStyleClass().remove("answered");
    if (opponentAnswered) opponentStatus.getStyleClass().add("answered");
    int mine = streak(state, selfId), theirs = streak(state, opponentId);
    if (mine != shownSelfStreak) showStreak(selfStreak, shownSelfStreak = mine);
    if (theirs != shownOpponentStreak) showStreak(opponentStreak, shownOpponentStreak = theirs);
  }

  void openLifeline(String kind) {
    lifelines.openLifeline(kind);
  }

  /** Flame badge from three correct answers in a row; its colour climbs at 5 and at 10. */
  private void showStreak(HBox slot, int streak) {
    slot.getChildren().clear();
    if (Effects.tier(streak) == 0) return;
    var badge = Effects.streakBadge(streak, !reducedMotion(), animations);
    slot.getChildren().add(badge);
    if (!reducedMotion() && bound.phase().equals("REVEAL")) {
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

  /** Small tier: the locked tile squashes and springs back as its "ĐÃ KHÓA" stamp lands. */
  private void lockedIn() {
    if (reducedMotion() || renderer == null) return;
    for (var tile : renderer.view().lookupAll(".answer-tile.chosen"))
      Effects.squash(tile, animations);
    for (var stamp : renderer.view().lookupAll(".lock-stamp")) Effects.stamp(stamp, animations);
  }

  /**
   * Large tier for a streak of 3, 5 or 10: the whole screen shakes and sparks fly off the avatar.
   */
  private void streakHit() {
    Effects.shake(this, Effects.Tier.LARGE, animations);
    var avatar = lookup(".versus-avatar.self");
    if (avatar != null)
      Effects.burst(fx, avatar, Effects.Tier.LARGE, Effects.SPARK_HOT, animations);
  }

  private void celebrate(Parent body) {
    // Medium tier: a right answer freezes for a beat under a white flash, then throws sparks; a
    // wrong one shakes the answer row.
    Node own = body.lookup(".own-pick.correct");
    boolean right = false;
    JsonNode result = bound.payload(MessageType.QUESTION_RESULT);
    if (result != null)
      for (var o : result.path("outcomes"))
        if (o.path("userId").asLong() == selfId) right = o.path("correct").asBoolean(false);
    if (right) {
      Node from = own != null ? own : selfScore;
      Effects.whenLaidOut(
          fx,
          () ->
              Effects.hitFlash(
                  fx,
                  from,
                  Effects.Tier.MEDIUM,
                  animations,
                  () ->
                      Effects.burst(
                          fx, from, Effects.Tier.MEDIUM, Effects.SPARK_GOOD, animations)));
    } else Effects.shake(body, Effects.Tier.MEDIUM, animations);
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

  /** Motion is reduced by the start-up flag or, from the next animation on, by the settings. */
  private boolean reducedMotion() {
    return reducedMotionFlag || Theme.reduceMotion();
  }

  /** Whole seconds left to answer, or -1 when no question is open. */
  public int secondsLeft() {
    if (!bound.phase().equals("OPEN")) return -1;
    long left = remainingAt(remaining, received, System.nanoTime());
    return left > 0 ? (int) ((left + 999) / 1000) : 0;
  }

  public void tick() {
    long left = remainingAt(remaining, received, System.nanoTime());
    boolean open = bound.phase().equals("OPEN");
    int points = left >= 10000 ? 3 : left >= 5000 ? 2 : 1;
    for (int i = 0; i < bonusChips.size(); i++) {
      var chip = bonusChips.get(i);
      int value = (3 - i) * (lifelines.doubled() ? 2 : 1);
      chip.setText("×" + value);
      boolean on = open && left > 0 && 3 - i == points;
      if (on != chip.getStyleClass().contains("on"))
        if (on) chip.getStyleClass().add("on");
        else chip.getStyleClass().remove("on");
      chip.setVisible(open);
    }
    String seconds = left > 0 ? String.format("%02d", (left + 999) / 1000) : "00";
    timer.setText(bound.phase().equals("REVEAL") ? "✓" : left > 0 ? seconds : "··");
    // Outside the answering time the box counts down to the next step in quieter colours.
    if (open == ring.getStyleClass().contains("idle"))
      if (open) ring.getStyleClass().remove("idle");
      else ring.getStyleClass().add("idle");
    ring.setAccessibleText(
        bound.phase().equals("OPEN") ? "Còn " + ((left + 999) / 1000) + " giây" : "Chưa tính giờ");
    String count = left > 0 ? Long.toString((left + 999) / 1000) : "Sẵn sàng";
    if (!count.equals(lastCountdown)) {
      lastCountdown = count;
      countdown.setText(count);
      if (!reducedMotion() && bound.phase().equals("MATCH_COUNTDOWN")) {
        var pop = new ScaleTransition(Duration.millis(220), countdown);
        pop.setFromX(1.4);
        pop.setFromY(1.4);
        pop.setToX(1);
        pop.setToY(1);
        play(pop);
      }
    }
    double share = !open || remaining <= 0 ? 0 : Math.min(1, (double) left / remaining);
    double inner = TIMER - 8;
    drain.resizeRelocate(4, 4 + inner * (1 - share), inner, inner * share);
    boolean urgent = open && left > 0 && left <= 5000;
    long second = (left + 999) / 1000;
    if (urgent && second != lastTickSecond) {
      lastTickSecond = second;
      Sound.play(Sound.Effect.TICK);
    }
    if (urgent != ring.getStyleClass().contains("urgent")) {
      if (urgent) {
        ring.getStyleClass().add("urgent");
        if (!reducedMotion()) {
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
