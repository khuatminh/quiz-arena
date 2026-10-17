package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.common.protocol.*;

/**
 * Floating challenge card: a dark header with the challenger and a countdown ring, the quiz below,
 * then Accept (Enter) and Reject (Esc). The outgoing variant is a compact "waiting" card.
 */
public final class ChallengeDialog extends VBox {
  private static final double RING = 25;
  private final Label remaining = Ui.label("");
  private final Label seconds = Ui.label("");
  private final Arc ringBar = new Arc(RING + 4, RING + 4, RING, RING, 90, -360);
  private final List<Button> buttons = new ArrayList<>();
  private final long expires;
  private final boolean received;
  private long total = -1;

  public ChallengeDialog(JsonNode p, boolean received, UiCommandSink sink) {
    this(p, received, sink, null);
  }

  /** {@code opponentName} names the invited player on the outgoing card, when known. */
  public ChallengeDialog(JsonNode p, boolean received, UiCommandSink sink, String opponentName) {
    this.received = received;
    getStyleClass().addAll("challenge-dialog", received ? "incoming" : "outgoing");
    setPrefWidth(received ? 520 : 380);
    setMaxWidth(received ? 520 : 380);
    setMaxHeight(Region.USE_PREF_SIZE);
    setAccessibleRole(AccessibleRole.DIALOG);
    expires = p.path("expiresAtMs").asLong();
    var id = UUID.fromString(p.path("challengeId").asText());
    JsonNode quiz = p.path("quiz");
    JsonNode player = p.path("challengerProfile");

    var kicker = Ui.label(received ? "LỜI THÁCH ĐẤU" : "ĐÃ GỬI LỜI MỜI");
    kicker.getStyleClass().add("challenge-kicker");
    String name = player.path("displayName").asText("");
    var headline =
        Ui.label(
            received
                ? (name.isEmpty()
                    ? "Bạn nhận được lời thách đấu!"
                    : name + " muốn đấu trí với bạn!")
                : opponentName == null || opponentName.isBlank()
                    ? "Đang chờ đối thủ…"
                    : "Đang chờ " + opponentName + " trả lời…");
    headline.getStyleClass().add("challenge-headline");
    headline.setMinHeight(Region.USE_PREF_SIZE);
    var who = new VBox(4, kicker, headline);
    who.setMinWidth(0);
    if (received && player.has("totalScore")) {
      var score =
          Ui.label(
              player.path("totalScore").asLong()
                  + " điểm · muốn chơi "
                  + quiz.path("title").asText());
      score.getStyleClass().add("challenge-sub");
      who.getChildren().add(score);
    }
    HBox.setHgrow(who, Priority.ALWAYS);
    var head = new HBox(20);
    head.getStyleClass().add("challenge-head");
    head.setAlignment(Pos.CENTER_LEFT);
    if (received) {
      var avatar =
          new StackPane(new AssetLoader().view(player.path("avatarId").asText("avatar-01"), 60));
      avatar.getStyleClass().add("challenge-avatar");
      avatar.setMinSize(76, 76);
      avatar.setMaxSize(76, 76);
      head.getChildren().add(avatar);
    }
    head.getChildren().addAll(who, countdownRing());

    var cover = new AssetLoader().view(quiz.path("coverAssetId").asText("quiz-cover-general"), 64);
    cover.setFitWidth(96);
    cover.setFitHeight(64);
    cover.setPreserveRatio(false);
    var clip = new Rectangle(96, 64);
    clip.setArcWidth(20);
    clip.setArcHeight(20);
    cover.setClip(clip);
    var quizTitle = Ui.label(quiz.path("title").asText());
    quizTitle.getStyleClass().add("challenge-quiz-title");
    var meta = new ArrayList<String>();
    if (quiz.has("totalRounds")) meta.add(quiz.path("totalRounds").asInt() + " câu");
    if (quiz.hasNonNull("categoryName")) meta.add(quiz.path("categoryName").asText());
    meta.add(
        quiz.path("quizSource").asText("SYSTEM").equals("COMMUNITY")
            ? "Không tính hạng"
            : "Tính hạng");
    var quizMeta = Ui.muted(String.join(" · ", meta));
    var quizCard = new HBox(16, cover, new VBox(4, quizTitle, quizMeta));
    quizCard.setAlignment(Pos.CENTER_LEFT);
    quizCard.getStyleClass().add("challenge-quiz");

    remaining.getStyleClass().add("challenge-remaining");
    remaining.managedProperty().bind(remaining.textProperty().isNotEmpty());
    remaining.visibleProperty().bind(remaining.managedProperty());
    var actions = new HBox(12);
    if (received) {
      var yes =
          Ui.button(
              "Chấp nhận · Enter",
              () -> {
                sink.send(
                    MessageType.CHALLENGE_ACCEPT, null, null, new Payloads.ChallengeAccept(id));
                setDisable(true);
              });
      yes.setDefaultButton(true);
      yes.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(yes, Priority.ALWAYS);
      var no =
          Ui.secondary(
              "Từ chối · Esc",
              () -> {
                sink.send(
                    MessageType.CHALLENGE_REJECT, null, null, new Payloads.ChallengeReject(id));
                setDisable(true);
              });
      no.setCancelButton(true);
      no.setPrefWidth(160);
      buttons.addAll(List.of(yes, no));
    } else {
      var cancel =
          Ui.secondary(
              "Hủy lời mời",
              () -> {
                sink.send(
                    MessageType.CHALLENGE_CANCEL, null, null, new Payloads.ChallengeCancel(id));
                setDisable(true);
              });
      cancel.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(cancel, Priority.ALWAYS);
      buttons.add(cancel);
    }
    for (var b : buttons) b.getStyleClass().add("challenge-action");
    actions.getChildren().addAll(buttons);
    var body = new VBox(18, quizCard, remaining, actions);
    body.getStyleClass().add("challenge-body");
    getChildren().addAll(head, body);
  }

  private StackPane countdownRing() {
    double c = RING + 4;
    var track = new Circle(c, c, RING);
    track.getStyleClass().add("challenge-ring-track");
    ringBar.setType(ArcType.OPEN);
    ringBar.getStyleClass().add("challenge-ring-bar");
    var dial = new Pane(track, ringBar);
    dial.setMinSize(2 * c, 2 * c);
    dial.setMaxSize(2 * c, 2 * c);
    seconds.getStyleClass().add("challenge-seconds");
    var ring = new StackPane(dial, seconds);
    ring.setMinSize(2 * c, 2 * c);
    ring.setMaxSize(2 * c, 2 * c);
    return ring;
  }

  /** Focuses the main action so Enter and Esc work as soon as the card appears. */
  public void focusAction() {
    if (!buttons.isEmpty()) buttons.getFirst().requestFocus();
  }

  public boolean incoming() {
    return received;
  }

  public void tick(long now) {
    long left = Math.max(0, expires - now);
    if (total < 0) total = Math.max(1000, left);
    long secondsLeft = (left + 999) / 1000;
    seconds.setText(Long.toString(secondsLeft));
    ringBar.setLength(-360 * Math.min(1, (double) left / total));
    boolean urgent = secondsLeft > 0 && secondsLeft <= 5;
    if (urgent != getStyleClass().contains("urgent"))
      if (urgent) getStyleClass().add("urgent");
      else getStyleClass().remove("urgent");
    remaining.setText(secondsLeft > 0 ? "" : "Lời mời đã hết hạn");
    if (secondsLeft == 0) for (var b : buttons) b.setDisable(true);
  }
}
