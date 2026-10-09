package vn.edu.nhom7.quiz.client.ui;

import javafx.geometry.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class WaitingView extends VBox {
  private final java.util.Map<Long, javafx.scene.control.Label> statuses =
      new java.util.HashMap<>();
  private final javafx.scene.control.Label notice =
      Ui.label("Trận đấu bắt đầu khi cả hai người sẵn sàng.");
  private final javafx.scene.control.Button ready = Ui.button("Tôi đã sẵn sàng", () -> {});

  public WaitingView(ClientState s, UiCommandSink sink) {
    setSpacing(28);
    setPadding(new Insets(40));
    setAlignment(Pos.CENTER);
    getStyleClass().add("waiting-panel");
    var match = s.payload(MessageType.MATCH_START);
    getChildren()
        .addAll(
            Ui.title("Sẵn sàng đấu trí?"),
            Ui.label(
                (match == null ? 10 : match.path("totalRounds").asInt(10))
                    + " câu hỏi · 15 giây mỗi câu"));
    var players = new HBox(32);
    players.setAlignment(Pos.CENTER);
    if (match != null)
      for (var player : match.path("players")) {
        var status = Ui.label("Chưa sẵn sàng");
        statuses.put(player.path("userId").asLong(), status);
        var tile =
            new VBox(
                12,
                new AssetLoader().view(player.path("avatarId").asText(), 100),
                Ui.title(player.path("displayName").asText()),
                Ui.label(player.path("userId").asLong() == s.selfUserId() ? "Bạn" : "Đối thủ"),
                status);
        tile.getStyleClass().add("player-tile");
        tile.setPrefWidth(240);
        players.getChildren().add(tile);
      }
    ready.setOnAction(
        e -> {
          ready.setDisable(true);
          ready.setText("✓ Đã sẵn sàng");
          notice.setText("Đang chờ người chơi còn lại…");
          sink.send(MessageType.MATCH_READY, s.activeMatchId(), null, new Payloads.MatchReady());
        });
    getChildren()
        .addAll(
            players,
            notice,
            ready,
            Ui.secondary(
                "Rời trận",
                () ->
                    sink.send(
                        MessageType.EXIT_MATCH,
                        s.activeMatchId(),
                        null,
                        new Payloads.ExitMatch())));
    render(s);
  }

  public void render(ClientState s) {
    var status = s.payload(MessageType.READY_STATUS);
    if (status == null) return;
    for (var id : status.path("readyUserIds")) {
      var label = statuses.get(id.asLong());
      if (label != null) label.setText("✓ Đã sẵn sàng");
      if (id.asLong() == s.selfUserId()) {
        ready.setDisable(true);
        ready.setText("✓ Đã sẵn sàng");
        notice.setText("Đang chờ đối thủ sẵn sàng…");
      }
    }
  }
}
