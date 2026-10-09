package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ChallengeDialog extends VBox {
  private final Label remaining = Ui.label("");
  private final long expires;

  public ChallengeDialog(JsonNode p, boolean received, UiCommandSink sink) {
    setSpacing(20);
    setPrefWidth(300);
    setMaxWidth(300);
    getStyleClass().add("challenge-panel");
    remaining.getStyleClass().add("badge");
    setPadding(new Insets(20));
    expires = p.path("expiresAtMs").asLong();
    var id = UUID.fromString(p.path("challengeId").asText());
    getChildren()
        .addAll(
            Ui.title(received ? "Bạn nhận được lời thách đấu!" : "Đang chờ đối thủ…"),
            Ui.label(p.path("quiz").path("title").asText()),
            remaining);
    if (received && p.hasNonNull("challengerProfile")) {
      var player = p.path("challengerProfile");
      getChildren()
          .add(
              1,
              new VBox(
                  10,
                  new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                      .view(player.path("avatarId").asText(), 64),
                  Ui.label(player.path("displayName").asText() + " muốn đấu trí cùng bạn")));
    }
    if (received) {
      var yes =
          Ui.button(
              "Chấp nhận",
              () -> {
                sink.send(
                    MessageType.CHALLENGE_ACCEPT, null, null, new Payloads.ChallengeAccept(id));
                setDisable(true);
              });
      getChildren()
          .addAll(
              yes,
              Ui.secondary(
                  "Từ chối",
                  () -> {
                    sink.send(
                        MessageType.CHALLENGE_REJECT, null, null, new Payloads.ChallengeReject(id));
                    setDisable(true);
                  }));
    } else
      getChildren()
          .add(
              Ui.secondary(
                  "Hủy lời mời",
                  () -> {
                    sink.send(
                        MessageType.CHALLENGE_CANCEL, null, null, new Payloads.ChallengeCancel(id));
                    setDisable(true);
                  }));
  }

  public void tick(long now) {
    long seconds = Math.max(0, (expires - now + 999) / 1000);
    remaining.setText(seconds > 0 ? "Còn " + seconds + " giây để phản hồi" : "Lời mời đã hết hạn");
    if (seconds == 0)
      for (var node : getChildren()) if (node instanceof Button b) b.setDisable(true);
  }
}
