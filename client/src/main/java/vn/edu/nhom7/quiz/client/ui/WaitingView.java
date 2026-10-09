package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class WaitingView extends VBox {
  public WaitingView(ClientState s, UiCommandSink sink) {
    setSpacing(24);
    setPadding(new Insets(36));
    getChildren()
        .addAll(
            Ui.title("Sẵn sàng đấu trí?"),
            Ui.label("10 câu hỏi · 15 giây mỗi câu · chờ đủ hai người chơi"));
    var players = s.payload(MessageType.MATCH_START);
    if (players != null)
      for (var player : players.path("players"))
        getChildren()
            .add(
                new HBox(
                    16,
                    new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                        .view(player.path("avatarId").asText(), 64),
                    Ui.label(player.path("displayName").asText())));
    var ready =
        Ui.button(
            "Tôi đã sẵn sàng",
            () ->
                sink.send(
                    MessageType.MATCH_READY, s.activeMatchId(), null, new Payloads.MatchReady()));
    ready.setOnAction(
        e -> {
          ready.setDisable(true);
          sink.send(MessageType.MATCH_READY, s.activeMatchId(), null, new Payloads.MatchReady());
        });
    getChildren()
        .addAll(
            ready,
            Ui.button(
                "Rời trận",
                () ->
                    sink.send(
                        MessageType.EXIT_MATCH,
                        s.activeMatchId(),
                        null,
                        new Payloads.ExitMatch())));
  }
}
