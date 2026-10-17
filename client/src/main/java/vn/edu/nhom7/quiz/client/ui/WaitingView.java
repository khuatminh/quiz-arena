package vn.edu.nhom7.quiz.client.ui;

import javafx.geometry.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/** The ready room: both players face off with a VS between them until each presses ready. */
public final class WaitingView extends VBox {
  private final java.util.Map<Long, javafx.scene.control.Label> statuses =
      new java.util.HashMap<>();
  private final javafx.scene.control.Label notice =
      Ui.label("Trận đấu bắt đầu khi cả hai người sẵn sàng.");
  private final javafx.scene.control.Button ready = Ui.button("Tôi đã sẵn sàng", () -> {});

  public WaitingView(ClientState s, UiCommandSink sink) {
    setSpacing(24);
    setPadding(new Insets(40));
    setAlignment(Pos.CENTER);
    getStyleClass().add("waiting-panel");
    var match = s.payload(MessageType.MATCH_START);
    String quiz = match == null ? "" : match.path("quiz").path("title").asText("");
    var kicker = Ui.kicker(quiz.isBlank() ? "PHÒNG CHỜ" : "PHÒNG CHỜ · " + quiz.toUpperCase());
    kicker.getStyleClass().add("waiting-kicker");
    getChildren()
        .addAll(
            kicker,
            Ui.title("Sẵn sàng đấu trí?"),
            Ui.muted(
                (match == null ? 10 : match.path("totalRounds").asInt(10))
                    + " câu hỏi · 15 giây mỗi câu"));
    var players = new HBox(28);
    players.setAlignment(Pos.CENTER);
    if (match != null)
      for (var player : match.path("players")) {
        boolean self = player.path("userId").asLong() == s.selfUserId();
        var status = Ui.label("Chưa sẵn sàng");
        status.getStyleClass().add("ready-chip");
        statuses.put(player.path("userId").asLong(), status);
        var avatar = new StackPane(new AssetLoader().view(player.path("avatarId").asText(), 96));
        avatar.getStyleClass().add("waiting-avatar");
        avatar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        var role = Ui.label(self ? "BẠN" : "ĐỐI THỦ");
        role.getStyleClass().add("role-tag");
        var name = Ui.title(player.path("displayName").asText());
        name.getStyleClass().add("waiting-name");
        var tile = new VBox(12, avatar, name, role, status);
        tile.getStyleClass().addAll("player-tile", self ? "self" : "opponent");
        tile.setPrefWidth(240);
        tile.setMinWidth(180);
        if (!players.getChildren().isEmpty()) {
          var versus = Ui.label("VS");
          versus.getStyleClass().add("versus-mark");
          versus.setMinWidth(Region.USE_PREF_SIZE);
          players.getChildren().add(versus);
        }
        players.getChildren().add(tile);
      }
    ready.setOnAction(
        e -> {
          ready.setDisable(true);
          ready.setText("✓ Đã sẵn sàng");
          notice.setText("Đang chờ người chơi còn lại…");
          sink.send(MessageType.MATCH_READY, s.activeMatchId(), null, new Payloads.MatchReady());
        });
    ready.setDefaultButton(true);
    ready.getStyleClass().add("ready-button");
    notice.getStyleClass().add("waiting-notice");
    var leave =
        Ui.secondary(
            "Rời trận",
            () -> {
              if (Ui.confirmLeaveMatch(this))
                sink.send(
                    MessageType.EXIT_MATCH, s.activeMatchId(), null, new Payloads.ExitMatch());
            });
    var actions = new HBox(12, leave, ready);
    actions.setAlignment(Pos.CENTER);
    var prompts = new HBox(20, Ui.prompt("Enter", "Sẵn sàng"), Ui.prompt("Tab", "Di chuyển"));
    prompts.getStyleClass().add("prompt-bar");
    prompts.setAlignment(Pos.CENTER);
    getChildren().addAll(players, notice, actions, prompts);
    render(s);
  }

  public void render(ClientState s) {
    var status = s.payload(MessageType.READY_STATUS);
    if (status == null) return;
    for (var id : status.path("readyUserIds")) {
      var label = statuses.get(id.asLong());
      if (label != null) {
        label.setText("✓ Đã sẵn sàng");
        if (!label.getStyleClass().contains("ready")) label.getStyleClass().add("ready");
      }
      if (id.asLong() == s.selfUserId()) {
        ready.setDisable(true);
        ready.setText("✓ Đã sẵn sàng");
        notice.setText("Đang chờ đối thủ sẵn sàng…");
      }
    }
  }
}
