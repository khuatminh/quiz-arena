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
    for (var e : p.path("entries"))
      getChildren()
          .add(
              Ui.label(
                  "#"
                      + e.path("rank").asInt()
                      + "   "
                      + e.path("displayName").asText()
                      + "   "
                      + e.path("totalScore").asLong()
                      + " điểm · "
                      + e.path("wins").asInt()
                      + " thắng"));
    if (p.hasNonNull("myRank"))
      getChildren().add(Ui.label("Hạng của bạn: " + p.path("myRank").path("rank").asInt()));
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
    getChildren()
        .add(
            new HBox(
                16,
                prev,
                Ui.label("Trang " + page),
                Ui.button(
                    "Tiếp →",
                    () ->
                        sink.send(
                            MessageType.RANKING_REQUEST,
                            null,
                            null,
                            new Payloads.RankingRequest(page + 1, 20)))));
  }
}
