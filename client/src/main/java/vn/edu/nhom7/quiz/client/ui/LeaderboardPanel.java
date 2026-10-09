package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.animation.*;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;

public final class LeaderboardPanel extends VBox {
  public record Row(
      long userId, int rank, int scoreBefore, int earnedPoints, int totalScore, int correctCount) {
    public static Row from(JsonNode n) {
      return new Row(
          n.path("userId").asLong(),
          n.path("rank").asInt(),
          n.path("scoreBefore").asInt(),
          n.path("earnedPoints").asInt(),
          n.path("totalScore").asInt(),
          n.path("correctCount").asInt());
    }
  }

  private final List<Animation> animations = new ArrayList<>();
  private final Map<Label, Integer> finalScores = new HashMap<>();

  public LeaderboardPanel(JsonNode p, long self, boolean reducedMotion) {
    setSpacing(18);
    setPadding(new Insets(28));
    var heading = Ui.title("Bảng điểm sau câu " + p.path("roundIndex").asInt());
    heading.getStyleClass().add("light-label");
    getChildren().add(heading);
    for (var standing : p.path("standings")) {
      var row = Row.from(standing);
      var score = Ui.title(Integer.toString(row.totalScore()));
      finalScores.put(score, row.totalScore());
      var line =
          new HBox(
              20,
              new AssetLoader().view(standing.path("avatarId").asText(), 64),
              Ui.label(
                  "#"
                      + row.rank()
                      + "  "
                      + standing.path("displayName").asText()
                      + (row.userId() == self ? " · Bạn" : "")),
              score,
              Ui.label("+" + row.earnedPoints() + " · " + row.correctCount() + " đúng"));
      HBox.setHgrow(line.getChildren().get(1), Priority.ALWAYS);
      line.getStyleClass().add("leaderboard-row");
      getChildren().add(line);
      if (!reducedMotion) {
        var value = new SimpleDoubleProperty(row.scoreBefore());
        value.addListener(
            (observable, old, current) -> score.setText(Integer.toString(current.intValue())));
        var count =
            new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(value, row.scoreBefore())),
                new KeyFrame(
                    Duration.millis(500),
                    new KeyValue(value, row.totalScore(), Interpolator.EASE_OUT)));
        var move = new TranslateTransition(Duration.millis(400), line);
        move.setFromY((standing.path("previousRank").asInt(row.rank()) - row.rank()) * 30);
        move.setToY(0);
        var fade = new FadeTransition(Duration.millis(400), line);
        fade.setFromValue(.3);
        fade.setToValue(1);
        animations.add(count);
        animations.add(move);
        animations.add(fade);
        count.play();
        move.play();
        fade.play();
      }
    }
    if (p.path("standings").size() == 2
        && p.path("standings").get(0).path("rank").asInt()
            == p.path("standings").get(1).path("rank").asInt())
      getChildren().add(Ui.label("Đồng hạng"));
  }

  public void dispose() {
    animations.forEach(Animation::stop);
    finalScores.forEach((label, score) -> label.setText(score.toString()));
  }
}
