package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class RankingView extends VBox {
  public RankingView(JsonNode p, UiCommandSink sink) {
    setSpacing(16);
    setPadding(new Insets(28));
    getChildren().add(Ui.title("Bảng xếp hạng"));
    if (p == null) {
      getChildren().add(Ui.label("Đang tải…"));
      return;
    }
    if (p.path("entries").isEmpty()) getChildren().add(Ui.label("Chưa có điểm đã lưu."));
    getChildren().add(Ui.muted("Điểm tích lũy từ quiz hệ thống. Quiz cộng đồng không tính hạng."));
    var heading = row("Hạng", "Người chơi", "Tổng điểm", "Số trận", "Thắng");
    heading.getStyleClass().add("table-heading");
    getChildren().add(heading);
    for (var e : p.path("entries")) {
      var line =
          row(
              "#" + e.path("rank").asInt(),
              e.path("displayName").asText(),
              e.path("totalScore").asLong() + "",
              e.path("totalMatches").asInt() + "",
              e.path("wins").asInt() + "");
      line.getStyleClass().add("data-row");
      ((Label) line.getChildren().get(1))
          .setGraphic(
              new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                  .view(e.path("avatarId").asText(), 34));
      ((Label) line.getChildren().get(1)).setGraphicTextGap(12);
      if (e.path("userId").asLong() == p.path("myRank").path("userId").asLong(-1))
        line.getStyleClass().add("self");
      getChildren().add(line);
    }
    if (p.hasNonNull("myRank"))
      getChildren().add(Ui.badge("Vị trí của bạn: #" + p.path("myRank").path("rank").asInt()));
    int page = p.path("page").asInt(1);
    var prev =
        Ui.button(
            "← Trước",
            () ->
                sink.send(
                    MessageType.RANKING_REQUEST,
                    null,
                    null,
                    new Payloads.RankingRequest(Math.max(1, page - 1), 20)));
    prev.setDisable(page == 1);
    var next =
        Ui.button(
            "Tiếp →",
            () ->
                sink.send(
                    MessageType.RANKING_REQUEST,
                    null,
                    null,
                    new Payloads.RankingRequest(page + 1, 20)));
    next.setDisable((long) page * p.path("pageSize").asInt(20) >= p.path("totalItems").asLong());
    getChildren().add(new HBox(16, prev, Ui.label("Trang " + page), next));
  }

  private HBox row(String rank, String name, String score, String matches, String wins) {
    var r = new HBox(16);
    var place = Ui.label(rank);
    place.setPrefWidth(70);
    place.setMinWidth(70);
    var player = Ui.label(name);
    player.setMaxWidth(Double.MAX_VALUE);
    player.setMinWidth(100);
    HBox.setHgrow(player, Priority.ALWAYS);
    var points = Ui.label(score);
    points.setPrefWidth(120);
    points.setMinWidth(120);
    var games = Ui.label(matches);
    games.setPrefWidth(80);
    games.setMinWidth(80);
    var victories = Ui.label(wins);
    victories.setPrefWidth(70);
    victories.setMinWidth(70);
    r.getChildren().addAll(place, player, points, games, victories);
    r.setAlignment(Pos.CENTER_LEFT);
    return r;
  }
}
