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
    setSpacing(12);
    setPadding(new Insets(20));
    expires = p.path("expiresAtMs").asLong();
    var id = UUID.fromString(p.path("challengeId").asText());
    getChildren()
        .addAll(
            Ui.title(received ? "Bạn nhận được lời thách đấu!" : "Đang chờ đối thủ…"),
            Ui.label(p.path("quiz").path("title").asText()),
            remaining);
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
              Ui.button(
                  "Từ chối",
                  () -> {
                    sink.send(
                        MessageType.CHALLENGE_REJECT, null, null, new Payloads.ChallengeReject(id));
                    setDisable(true);
                  }));
    } else
      getChildren()
          .add(
              Ui.button(
                  "Hủy lời mời",
                  () -> {
                    sink.send(
                        MessageType.CHALLENGE_CANCEL, null, null, new Payloads.ChallengeCancel(id));
                    setDisable(true);
                  }));
  }

  public void tick(long now) {
    remaining.setText(
        "Còn " + Math.max(0, (expires - now + 999) / 1000) + " giây · chờ server xác nhận");
  }
}
