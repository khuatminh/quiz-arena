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
  private final List<javafx.animation.Animation> animations = new ArrayList<>();
  private final List<Label> scores = new ArrayList<>();
  private String outcome = "";
  private Node heroIcon;
  private Label heroTitle;

  public ResultView(ClientState s, UiCommandSink sink) {
    setSpacing(14);
    setPadding(new Insets(28));
    getStyleClass().add("result-view");
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
    outcome =
        r.path("finishReason").asText().equals("ABORTED")
            ? "aborted"
            : r.path("winnerUserId").isNull()
                ? "draw"
                : r.path("winnerUserId").asLong() == s.selfUserId() ? "win" : "lose";
    heroTitle = Ui.title(title);
    heroTitle.getStyleClass().add("result-title");
    heroIcon =
        switch (outcome) {
          case "win" -> Effects.trophy(64);
          case "lose" -> Effects.rainCloud(64, false, null);
          case "draw" -> Effects.draw(64);
          default -> null;
        };
    var hero = new VBox(8);
    if (heroIcon != null) {
      var halo = new StackPane(heroIcon);
      halo.getStyleClass().add("result-halo");
      halo.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      hero.getChildren().add(halo);
    }
    hero.getChildren().add(heroTitle);
    hero.getStyleClass().addAll("result-hero", outcome);
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
      scores.add(score);
      boolean winner =
          !r.path("winnerUserId").isNull()
              && player.path("userId").asLong() == r.path("winnerUserId").asLong();
      var avatar =
          new StackPane(
              new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                  .view(player.path("avatarId").asText(), 60));
      avatar.getStyleClass().add("result-avatar");
      if (winner) avatar.getStyleClass().add("winner");
      avatar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      var crown = winner ? Effects.crown(30) : new Region();
      if (!winner) ((Region) crown).setMinHeight(30);
      var tile =
          new VBox(
              4,
              crown,
              avatar,
              Ui.label(
                  player.path("displayName").asText()
                      + (player.path("userId").asLong() == s.selfUserId() ? " · Bạn" : "")),
              score,
              Ui.label(player.path("correctCount").asInt() + " câu đúng"));
      tile.setAlignment(Pos.CENTER);
      tile.getStyleClass().add("result-player");
      if (winner) tile.getStyleClass().add("winner");
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
    var home =
        Ui.secondary(
            "Về sảnh",
            () ->
                sink.send(
                    MessageType.EXIT_MATCH, s.activeMatchId(), null, new Payloads.ExitMatch()));
    var actions = new FlowPane(12, 12, rematch, accept, reject, home);
    actions.setAlignment(Pos.CENTER);
    actions.getStyleClass().add("result-actions");
    var reviewTitle = Ui.title("Xem lại câu hỏi");
    var reviewMeta =
        Ui.muted(
            r.path("totalRounds").asInt(10)
                + " câu · tối đa "
                + (r.path("totalRounds").asInt(10) * 3)
                + " điểm · "
                + (r.path("ranked").asBoolean(true) ? "Tính hạng" : "Cộng đồng · Không tính hạng")
                + " · câu sai được mở sẵn");
    var reviewHeading = new VBox(4, reviewTitle, reviewMeta);
    reviewHeading.setMinWidth(0);
    HBox.setHgrow(reviewHeading, Priority.ALWAYS);
    var cards = new ArrayList<ReviewCard>();
    var toggleAll = Ui.secondary("Mở tất cả", () -> {});
    toggleAll.setOnAction(
        e -> {
          boolean open = cards.stream().anyMatch(c -> !c.expanded());
          cards.forEach(c -> c.setExpanded(open));
          toggleAll.setText(open ? "Thu gọn tất cả" : "Mở tất cả");
        });
    var reviewHeader = new HBox(12, reviewHeading, toggleAll);
    reviewHeader.setAlignment(Pos.BOTTOM_LEFT);
    reviewHeader.getStyleClass().add("review-section-header");
    getChildren().addAll(rematchNote, actions, reviewHeader);
    JsonNode pageData = s.payload(MessageType.LIVE_REVIEW);
    int page = pageData == null ? r.path("reviewPage").asInt(1) : pageData.path("page").asInt(1);
    long total =
        pageData == null
            ? r.path("reviewTotalItems").asLong(r.path("review").size())
            : pageData.path("totalItems").asLong();
    final java.util.UUID reviewMatch = s.resultMatchId();
    Button previous =
        Ui.secondary(
            "← Trước",
            () ->
                sink.send(
                    MessageType.LIVE_REVIEW_REQUEST,
                    reviewMatch,
                    null,
                    new Payloads.LiveReviewRequest(page - 1)));
    Button next =
        Ui.secondary(
            "Tiếp →",
            () ->
                sink.send(
                    MessageType.LIVE_REVIEW_REQUEST,
                    reviewMatch,
                    null,
                    new Payloads.LiveReviewRequest(page + 1)));
    previous.setDisable(page <= 1 || detached);
    next.setDisable(page * Payloads.REVIEW_PAGE_SIZE >= total || detached);
    for (var review : (pageData == null ? r.path("review") : pageData.path("review"))) {
      var card = new ReviewCard(review, r.path("players"), s.selfUserId());
      cards.add(card);
      getChildren().add(card);
    }
    toggleAll.setVisible(!cards.isEmpty());
    toggleAll.setManaged(!cards.isEmpty());
    if (cards.stream().allMatch(ReviewCard::expanded) && !cards.isEmpty())
      toggleAll.setText("Thu gọn tất cả");
    var pageLabel = Ui.label("Trang " + page);
    pageLabel.getStyleClass().add("page-label");
    var pager = new HBox(12, previous, pageLabel, next);
    pager.setAlignment(Pos.CENTER_LEFT);
    pager.getStyleClass().add("pager");
    getChildren().add(pager);
    render(s);
  }

  /**
   * Plays the ending once: confetti and a trophy pop for a win, a rain cloud and a heavier title
   * drop for a loss, with the matching sound. Scores count up from zero either way.
   */
  public void celebrate(Pane layer, boolean reducedMotion) {
    switch (outcome) {
      case "win" -> Sound.play(Sound.Effect.WIN);
      case "lose" -> Sound.play(Sound.Effect.LOSE);
      case "draw" -> Sound.play(Sound.Effect.CORRECT);
      default -> {}
    }
    if (reducedMotion || outcome.equals("aborted")) return;
    if (outcome.equals("win")) Effects.confetti(layer, animations);
    if (heroIcon != null) {
      if (outcome.equals("lose") && heroIcon.getParent() instanceof StackPane halo) {
        heroIcon = Effects.rainCloud(64, true, animations);
        halo.getChildren().setAll(heroIcon);
      }
      var pop =
          new javafx.animation.Timeline(
              new javafx.animation.KeyFrame(
                  javafx.util.Duration.ZERO,
                  new javafx.animation.KeyValue(heroIcon.scaleXProperty(), 0.2),
                  new javafx.animation.KeyValue(heroIcon.scaleYProperty(), 0.2),
                  new javafx.animation.KeyValue(
                      heroIcon.rotateProperty(), outcome.equals("win") ? -25 : 0)),
              new javafx.animation.KeyFrame(
                  javafx.util.Duration.millis(560),
                  new javafx.animation.KeyValue(heroIcon.scaleXProperty(), 1, Effects.BACK_OUT),
                  new javafx.animation.KeyValue(heroIcon.scaleYProperty(), 1, Effects.BACK_OUT),
                  new javafx.animation.KeyValue(heroIcon.rotateProperty(), 0, Effects.BACK_OUT)));
      Effects.run(pop, animations, javafx.util.Duration.millis(560));
    }
    boolean lost = outcome.equals("lose");
    var title =
        new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.ZERO,
                new javafx.animation.KeyValue(heroTitle.translateYProperty(), lost ? -46 : 0),
                new javafx.animation.KeyValue(heroTitle.scaleXProperty(), lost ? 1 : 0.6),
                new javafx.animation.KeyValue(heroTitle.scaleYProperty(), lost ? 1 : 0.6),
                new javafx.animation.KeyValue(heroTitle.opacityProperty(), 0)),
            new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(lost ? 760 : 520),
                new javafx.animation.KeyValue(
                    heroTitle.translateYProperty(),
                    0,
                    lost ? javafx.animation.Interpolator.EASE_OUT : Effects.BACK_OUT),
                new javafx.animation.KeyValue(heroTitle.scaleXProperty(), 1, Effects.BACK_OUT),
                new javafx.animation.KeyValue(heroTitle.scaleYProperty(), 1, Effects.BACK_OUT),
                new javafx.animation.KeyValue(heroTitle.opacityProperty(), 1)));
    title.setDelay(javafx.util.Duration.millis(140));
    Effects.run(title, animations, javafx.util.Duration.millis(760));
    for (var score : scores) {
      int target = Integer.parseInt(score.getText());
      var value = new javafx.beans.property.SimpleIntegerProperty(0);
      value.addListener((o, b, a) -> score.setText(Integer.toString(a.intValue())));
      score.setText("0");
      var count =
          new javafx.animation.Timeline(
              new javafx.animation.KeyFrame(
                  javafx.util.Duration.millis(1100),
                  new javafx.animation.KeyValue(
                      value, target, javafx.animation.Interpolator.EASE_OUT)));
      count.setDelay(javafx.util.Duration.millis(250));
      count.setOnFinished(e -> score.setText(Integer.toString(target)));
      Effects.run(count, animations, javafx.util.Duration.millis(1350));
      if (Effects.preview) score.setText(Integer.toString(target));
    }
  }

  public void dispose() {
    animations.forEach(javafx.animation.Animation::stop);
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
