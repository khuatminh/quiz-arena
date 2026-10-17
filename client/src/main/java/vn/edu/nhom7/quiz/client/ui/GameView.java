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
  private final Map<String, Button> lifelineButtons = new LinkedHashMap<>();
  private final HBox dock = new HBox(12);
  private final VBox popover = new VBox(10);
  private final Polygon tail = new Polygon(0, 0, 18, 0, 9, 10);
  private final Label popIcon = Ui.label(""), popName = Ui.label(""), popKey = Ui.label("");
  private final Label popText = Ui.label(""), popStatus = Ui.label("");
  private final Button popUse = new Button("Sử dụng");
  private String openKind;
  private final Label selfDouble = Ui.label("x2");
  private final Label opponentLifeline = Ui.label("");
  private final UiCommandSink sink;
  private ClientState current;
  private long questionId = -1;
  private boolean lifelineFits, lifelinePending, doubled;
  private String errorAtSend = "";

  /** The round whose reveal already played its sounds and effects, so rebuilds stay quiet. */
  static UUID revealedRound;

  /** Lifeline uses already announced with sound and motion, as round id plus user id. */
  static final Set<String> announcedLifelines = new HashSet<>();

  static final Map<String, String[]> LIFELINE_TEXT =
      Map.of(
          "DOUBLE_POINTS",
          new String[] {
            "×2",
            "x2 điểm",
            "Nhân đôi điểm của câu này: trả lời đúng được 6, 4 hoặc 2 điểm. Trả lời sai vẫn 0"
                + " điểm.",
            "X"
          },
          "REMOVE_ONE",
          new String[] {
            "−1", "Loại 1 đáp án", "Ẩn ngẫu nhiên một phương án sai, giúp bạn chọn dễ hơn.", "L"
          },
          "FIFTY_FIFTY",
          new String[] {"½", "50-50", "Chỉ giữ lại đáp án đúng và một phương án sai.", "N"});

  public GameView(ClientState s, UiCommandSink sink, boolean reducedMotion) {
    bound = s;
    current = s;
    this.sink = sink;
    this.reducedMotion = reducedMotion;
    received = s.receivedNanos();
    getStyleClass().add("game-surface");
    var questionEvent = s.payload(MessageType.QUESTION);
    int roundIndex = questionEvent == null ? 0 : questionEvent.path("roundIndex").asInt();
    JsonNode start = s.payload(MessageType.MATCH_START);
    int totalRounds = start == null ? 10 : start.path("totalRounds").asInt(10);
    maxScore = Math.max(3, totalRounds * 3);
    setTop(hud(s, start, roundIndex, totalRounds));
    countdown.getStyleClass().add("countdown-number");
    notice.getStyleClass().add("self-status");
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
      var holder = new StackPane(leaderboard);
      holder.setAlignment(Pos.TOP_CENTER);
      var scroll = Ui.scroll(holder);
      scroll.getStyleClass().add("stage-scroll");
      setCenter(scroll);
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
        String type = q.path("questionType").asText();
        String content = q.path("content").asText();
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
              default -> "MỘT ĐÁP ÁN";
            };
        String quiz = start == null ? "" : start.path("quiz").path("title").asText("");
        var kicker = Ui.label(quiz.isBlank() ? kind : kind + " · " + quiz.toUpperCase(Locale.ROOT));
        kicker.getStyleClass().add("question-kicker");
        if (s.phase().equals("REVEAL")) {
          kicker.setText("ĐÁP ÁN · " + PresentationText.answer(q, revealAnswer(s)));
          kicker.getStyleClass().add("answer-kicker");
        } else if (s.phase().equals("QUESTION_PREPARE")) kicker.setText(kind + " · ĐANG TẢI");
        var stage = new VBox(14, kicker, title);
        stage.getStyleClass().add("question-stage");
        stage.setAlignment(Pos.CENTER);
        if (q.hasNonNull("questionAssetId"))
          stage.getChildren().add(new AssetLoader().view(q.path("questionAssetId").asText(), 220));
        questionId = q.path("questionId").asLong(-1);
        lifelineFits = type.equals("SINGLE_CHOICE") && q.path("options").size() >= 3;
        buildLifelines();
        renderer =
            switch (type) {
              case "MULTIPLE_CHOICE" -> new MultipleChoiceRenderer();
              case "TRUE_FALSE" -> new TrueFalseRenderer();
              case "SHORT_ANSWER" -> new ShortAnswerRenderer();
              default -> new SingleChoiceRenderer();
            };
        var deck = new VBox(14);
        deck.getStyleClass().add("answer-deck");
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
          if (s.phase().equals("REVEAL")) {
            var result = s.payload(MessageType.QUESTION_RESULT);
            renderer.showResult(
                new ObjectMapper()
                    // The client adds its own fields (streaks) to the stored result.
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .treeToValue(result, Payloads.QuestionResult.class),
                s.selfUserId());
            if (result.hasNonNull("explanation")
                && !result.path("explanation").asText().isBlank()) {
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
            if (type.equals("SHORT_ANSWER"))
              stage
                  .getChildren()
                  .add(
                      new RevealPanel(
                          result, q, s.selfUserId(), start == null ? null : start.path("players")));
          }
          deck.getChildren().add(renderer.view());
        } catch (Exception e) {
          deck.getChildren()
              .add(Ui.label("Không thể hiển thị câu hỏi; chờ server kết thúc an toàn."));
        }
        prompts.getStyleClass().add("prompt-bar");
        prompts.setAlignment(Pos.CENTER_LEFT);
        showPrompts(type, s.phase());
        var bottom = new HBox(16, prompts, Ui.spacer(), dock);
        bottom.setAlignment(Pos.CENTER_LEFT);
        // Hints are the first thing to give way on a narrow window; the clip hides the overflow.
        prompts.setMinWidth(0);
        var clip = new Rectangle();
        clip.widthProperty().bind(prompts.widthProperty());
        clip.heightProperty().bind(prompts.heightProperty());
        prompts.setClip(clip);
        dock.setMinWidth(Region.USE_PREF_SIZE);
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
        if (!reducedMotion && s.phase().equals("QUESTION_PREPARE")) enter(stage, 0);
        if (!reducedMotion && s.phase().equals("REVEAL"))
          javafx.application.Platform.runLater(() -> celebrate(deck));
      }
    }
    setOnKeyPressed(
        e -> {
          if (getScene() != null
              && getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl)
            return;
          if (openKind != null && e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            closeLifeline();
            e.consume();
            return;
          }
          if (openKind != null && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
            if (!popUse.isDisabled()) popUse.fire();
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
          for (var entry : LIFELINE_TEXT.entrySet())
            if (Character.toUpperCase(c) == entry.getValue()[3].charAt(0)
                && lifelineButtons.containsKey(entry.getKey())) {
              lifelineButtons.get(entry.getKey()).fire();
              e.consume();
              return;
            }
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
    if (!lifelineButtons.isEmpty()) List.of(tail, popover).forEach(Node::toFront);
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
    if (openKind != null && dock.getScene() != null) {
      // The card floats above the open icon, right-aligned with the dock.
      var icon = lifelineButtons.get(openKind);
      var iconBox = sceneToLocal(icon.localToScene(icon.getBoundsInLocal()));
      var dockBox = sceneToLocal(dock.localToScene(dock.getBoundsInLocal()));
      double pw = popover.prefWidth(-1), ph = popover.prefHeight(pw);
      double right = Math.min(getWidth() - 16, dockBox.getMaxX());
      double top = iconBox.getMinY() - 14 - ph;
      popover.resizeRelocate(right - pw, top, pw, ph);
      tail.relocate(iconBox.getCenterX() - 9, top + ph - 1);
    }
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

  /**
   * The corner HUD: each player's avatar, score and fighting-game score bar on their side, the
   * timer in the middle. The local player is always on the left.
   */
  private Node hud(ClientState s, JsonNode start, int roundIndex, int totalRounds) {
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
    var extra = mirrored ? opponentLifeline : selfDouble;
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
      prompts.getChildren().add(prompt(null, "Câu tiếp theo sẽ tự bắt đầu"));
      return;
    }
    int options = renderer instanceof BaseQuestionRenderer base ? base.answerControls.size() : 0;
    switch (type) {
      case "SHORT_ANSWER" -> prompts.getChildren().add(prompt("Enter", "Gửi câu trả lời"));
      case "TRUE_FALSE" -> prompts.getChildren().add(prompt("1 / 2", "Đúng / Sai"));
      case "MULTIPLE_CHOICE" ->
          prompts
              .getChildren()
              .addAll(prompt("1–" + options, "Đánh dấu"), prompt("Enter", "Khóa đáp án"));
      default ->
          prompts.getChildren().addAll(prompt("1–" + options, "Chọn"), prompt("Tab", "Di chuyển"));
    }
    if (!lifelineButtons.isEmpty()) prompts.getChildren().add(prompt("X · L · N", "Trợ giúp"));
  }

  private static HBox prompt(String keys, String action) {
    var text = Ui.label(action);
    text.getStyleClass().add("prompt-text");
    text.setWrapText(false);
    var item = new HBox(8, text);
    if (keys != null) {
      var cap = Ui.label(keys);
      cap.getStyleClass().add("prompt-key");
      cap.setWrapText(false);
      cap.setMinWidth(Region.USE_PREF_SIZE);
      item.getChildren().addFirst(cap);
    }
    item.setAlignment(Pos.CENTER_LEFT);
    item.setMinWidth(Region.USE_PREF_SIZE);
    return item;
  }

  public void render(ClientState s) {
    current = s;
    if (renderer != null) renderer.setControlsEnabled(s.canAnswer());
    renderLifelines(s);
    notice.setText(
        !s.phase().equals("OPEN")
            ? ""
            : s.accepted()
                ? "✓ Đã khóa đáp án · chờ kết quả"
                : s.ownPending() ? "Đang gửi đáp án…" : "");
    notice.getStyleClass().remove("answered");
    if (s.accepted() && s.phase().equals("OPEN")) notice.getStyleClass().add("answered");
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
    selfGain.getStyleClass().removeAll("zero", "doubled");
    if (gains.getOrDefault(selfId, 1) == 0) selfGain.getStyleClass().add("zero");
    else if (doubled) selfGain.getStyleClass().add("doubled");
    selfShare.set(Math.min(1, (double) self / maxScore));
    opponentShare.set(Math.min(1, (double) opponent / maxScore));
    boolean opponentAnswered = false;
    JsonNode answered = s.payload(MessageType.ANSWER_STATUS);
    if (answered != null)
      for (var id : answered.path("answeredUserIds"))
        if (id.asLong() == opponentId) opponentAnswered = true;
    opponentStatus.setText(
        !s.phase().equals("OPEN") ? "" : opponentAnswered ? "✓ Đã khóa đáp án" : "Đang suy nghĩ…");
    opponentStatus.getStyleClass().remove("answered");
    if (opponentAnswered) opponentStatus.getStyleClass().add("answered");
    int mine = streak(s, selfId), theirs = streak(s, opponentId);
    if (mine != shownSelfStreak) showStreak(selfStreak, shownSelfStreak = mine);
    if (theirs != shownOpponentStreak) showStreak(opponentStreak, shownOpponentStreak = theirs);
  }

  /**
   * Lifeline icons in the bottom-right corner. Clicking one (or its key) opens a small card above
   * it with what the lifeline does and a "Sử dụng" button.
   */
  private void buildLifelines() {
    dock.getStyleClass().add("lifeline-dock");
    dock.setAlignment(Pos.CENTER_RIGHT);
    for (String kind : Payloads.LIFELINES) {
      String[] text = LIFELINE_TEXT.get(kind);
      var glyph = Ui.label(text[0]);
      glyph.getStyleClass().add("lifeline-glyph");
      var cap = Ui.label(text[3]);
      cap.getStyleClass().add("lifeline-key");
      StackPane.setAlignment(cap, Pos.BOTTOM_RIGHT);
      cap.setTranslateX(6);
      cap.setTranslateY(6);
      var face = new StackPane(glyph, cap);
      face.setMinSize(56, 56);
      face.setMaxSize(56, 56);
      var b = new Button();
      b.setGraphic(face);
      b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      b.setAccessibleText(text[1] + ". Phím " + text[3]);
      b.getStyleClass().addAll("lifeline", "lifeline-" + kind.toLowerCase(Locale.ROOT));
      b.setTooltip(new Tooltip(text[1] + " · phím " + text[3]));
      b.setOnAction(e -> toggleLifeline(kind));
      lifelineButtons.put(kind, b);
      dock.getChildren().add(b);
    }
    popIcon.getStyleClass().add("lifeline-pop-icon");
    popIcon.setMinSize(40, 40);
    popIcon.setAlignment(Pos.CENTER);
    popName.getStyleClass().add("lifeline-pop-name");
    popKey.getStyleClass().add("lifeline-pop-key");
    var close = new Button("✕");
    close.getStyleClass().add("lifeline-pop-close");
    close.setAccessibleText("Đóng");
    close.setOnAction(e -> closeLifeline());
    var header = new HBox(10, popIcon, popName, Ui.spacer(), popKey, close);
    header.setAlignment(Pos.CENTER_LEFT);
    popText.getStyleClass().add("lifeline-pop-text");
    popStatus.getStyleClass().add("lifeline-pop-status");
    var rule = Ui.label("Mỗi quyền dùng 1 lần mỗi trận, tối đa 1 quyền mỗi câu.");
    rule.getStyleClass().add("lifeline-pop-rule");
    popUse.getStyleClass().add("lifeline-use");
    popUse.setMaxWidth(Double.MAX_VALUE);
    popUse.setOnAction(e -> useLifeline(openKind));
    popover.getChildren().addAll(header, popText, popStatus, popUse, rule);
    popover.getStyleClass().add("lifeline-pop");
    popover.setPrefWidth(330);
    popover.setFillWidth(true);
    tail.getStyleClass().add("lifeline-pop-tail");
    for (Node n : List.of(tail, popover)) {
      n.setManaged(false);
      getChildren().add(n);
    }
    popover.setVisible(false);
    tail.setVisible(false);
  }

  private void toggleLifeline(String kind) {
    if (kind.equals(openKind)) closeLifeline();
    else openLifeline(kind);
  }

  void openLifeline(String kind) {
    if (!lifelineButtons.containsKey(kind)) return;
    openKind = kind;
    String[] text = LIFELINE_TEXT.get(kind);
    popIcon.setText(text[0]);
    popIcon
        .getStyleClass()
        .removeIf(c -> c.startsWith("lifeline-") && !c.equals("lifeline-pop-icon"));
    popIcon.getStyleClass().add("lifeline-" + kind.toLowerCase(Locale.ROOT));
    popName.setText(text[1]);
    popKey.setText("Phím " + text[3]);
    popText.setText(text[2]);
    lifelineButtons.forEach((k, b) -> b.getStyleClass().remove("open"));
    lifelineButtons.get(kind).getStyleClass().add("open");
    popover.setVisible(true);
    tail.setVisible(true);
    renderLifelines(current);
    requestLayout();
    if (!reducedMotion) {
      popover.setScaleX(0.85);
      popover.setScaleY(0.85);
      popover.setOpacity(0);
      var grow = new ScaleTransition(Duration.millis(180), popover);
      grow.setToX(1);
      grow.setToY(1);
      grow.setInterpolator(Effects.BACK_OUT);
      var fade = new FadeTransition(Duration.millis(140), popover);
      fade.setToValue(1);
      Effects.run(new ParallelTransition(grow, fade), animations, Duration.millis(180));
    }
  }

  private void closeLifeline() {
    openKind = null;
    lifelineButtons.forEach((k, b) -> b.getStyleClass().remove("open"));
    popover.setVisible(false);
    tail.setVisible(false);
  }

  private void useLifeline(String kind) {
    if (kind == null || !current.canAnswer() || lifelinePending || questionId < 0) return;
    lifelinePending = true;
    errorAtSend = current.error();
    try {
      sink.send(
          MessageType.LIFELINE_USE,
          current.activeMatchId(),
          current.roundId(),
          new Payloads.LifelineUse(questionId, kind));
      closeLifeline();
    } catch (RuntimeException failure) {
      lifelinePending = false;
    }
    renderLifelines(current);
  }

  static List<String> texts(JsonNode array) {
    var values = new ArrayList<String>();
    array.forEach(n -> values.add(n.asText()));
    return values;
  }

  /** Button states, removed options, the x2 chip and the opponent's lifeline notice. */
  private void renderLifelines(ClientState s) {
    JsonNode result = s.payload(MessageType.LIFELINE_RESULT);
    boolean thisQuestion = result != null && result.path("questionId").asLong() == questionId;
    String active = thisQuestion ? result.path("kind").asText("") : "";
    if (thisQuestion || (!s.error().isBlank() && !s.error().equals(errorAtSend)))
      lifelinePending = false;
    List<String> remaining = result == null ? Payloads.LIFELINES : texts(result.path("remaining"));
    for (var entry : lifelineButtons.entrySet()) {
      String kind = entry.getKey();
      Button b = entry.getValue();
      boolean used = !remaining.contains(kind), isActive = kind.equals(active);
      boolean fits = kind.equals("DOUBLE_POINTS") || lifelineFits;
      b.getStyleClass().removeAll("used", "active", "unavailable");
      if (isActive) b.getStyleClass().add("active");
      else if (used) b.getStyleClass().add("used");
      else if (!fits) b.getStyleClass().add("unavailable");
      if (!kind.equals(openKind)) continue;
      String why =
          isActive
              ? kind.equals("DOUBLE_POINTS")
                  ? s.phase().equals("OPEN") ? "Đang nhân đôi câu này" : "Đã nhân đôi câu này"
                  : "Đã loại phương án ở câu này"
              : used
                  ? "Đã dùng trong trận này"
                  : !fits
                      ? "Chỉ dùng cho câu một đáp án có từ 3 phương án"
                      : !active.isEmpty()
                          ? "Câu này đã dùng một quyền trợ giúp"
                          : s.accepted() || s.ownPending()
                              ? "Bạn đã trả lời câu này"
                              : !s.phase().equals("OPEN")
                                  ? "Dùng được khi câu hỏi bắt đầu tính giờ"
                                  : lifelinePending ? "Đang gửi…" : "Còn 1 lần trong trận";
      boolean usable = s.canAnswer() && !used && fits && active.isEmpty() && !lifelinePending;
      popStatus.setText(why);
      popStatus.getStyleClass().removeAll("ok", "blocked");
      popStatus.getStyleClass().add(usable || isActive ? "ok" : "blocked");
      popUse.setDisable(!usable);
      popUse.setText(usable ? "Sử dụng · Enter" : isActive ? "Đang dùng" : "Sử dụng");
    }
    doubled = active.equals("DOUBLE_POINTS");
    selfDouble.setVisible(doubled);
    String key = s.roundId() + ":" + selfId;
    boolean fresh = !active.isEmpty() && announcedLifelines.add(key);
    if (fresh) Sound.play(Sound.Effect.LIFELINE);
    if (doubled && fresh && !reducedMotion) pop(selfDouble);
    if (thisQuestion && renderer instanceof BaseQuestionRenderer base) {
      var removed = base.eliminate(texts(result.path("removedOptionIds")));
      if (fresh && !reducedMotion) removed.forEach(this::crumble);
    }
    var status = s.payload(MessageType.LIFELINE_STATUS);
    var theirs = status == null ? null : status.path("byUser").get(Long.toString(opponentId));
    boolean shown = theirs != null && theirs.path("questionId").asLong() == questionId;
    opponentLifeline.setText(
        shown ? "dùng " + LIFELINE_TEXT.get(theirs.path("kind").asText("DOUBLE_POINTS"))[1] : "");
    boolean wasShown = opponentLifeline.isVisible();
    opponentLifeline.setVisible(shown);
    if (shown
        && !wasShown
        && announcedLifelines.add(s.roundId() + ":" + opponentId)
        && !reducedMotion) pop(opponentLifeline);
  }

  private void pop(Node node) {
    var pop = new ScaleTransition(Duration.millis(380), node);
    pop.setFromX(0.2);
    pop.setFromY(0.2);
    pop.setToX(1);
    pop.setToY(1);
    pop.setInterpolator(Effects.BACK_OUT);
    Effects.run(pop, animations, Duration.millis(380));
  }

  /** A removed option shudders, shrinks a little and fades back. */
  private void crumble(Node tile) {
    var shake = new TranslateTransition(Duration.millis(60), tile);
    shake.setFromX(0);
    shake.setByX(7);
    shake.setAutoReverse(true);
    shake.setCycleCount(4);
    var shrink = new ScaleTransition(Duration.millis(280), tile);
    shrink.setFromX(1);
    shrink.setFromY(1);
    shrink.setToX(0.96);
    shrink.setToY(0.96);
    var fade = new FadeTransition(Duration.millis(280), tile);
    fade.setFromValue(1);
    fade.setToValue(0.45);
    var all = new SequentialTransition(shake, new ParallelTransition(shrink, fade));
    Effects.run(all, animations, Duration.millis(520));
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
    int points = left >= 10000 ? 3 : left >= 5000 ? 2 : 1;
    for (int i = 0; i < bonusChips.size(); i++) {
      var chip = bonusChips.get(i);
      int value = (3 - i) * (doubled ? 2 : 1);
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
      if (!reducedMotion && bound.phase().equals("MATCH_COUNTDOWN")) {
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
