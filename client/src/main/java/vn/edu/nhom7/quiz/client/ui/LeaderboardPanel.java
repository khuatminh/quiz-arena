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
    setSpacing(14);
    setPadding(new Insets(28, 0, 28, 0));
    setAlignment(Pos.TOP_CENTER);
    setMaxWidth(760);
    getStyleClass().add("leaderboard");
    var kicker = Ui.label("BẢNG ĐIỂM · SAU CÂU " + p.path("roundIndex").asInt());
    kicker.getStyleClass().add("question-kicker");
    var heading = Ui.title(lead(p.path("standings"), self));
    heading.getStyleClass().add("leaderboard-headline");
    var top = new VBox(6, kicker, heading);
    top.setAlignment(Pos.CENTER);
    top.setPadding(new Insets(0, 0, 8, 0));
    getChildren().add(top);
    int best = Integer.MIN_VALUE;
    for (var standing : p.path("standings"))
      best = Math.max(best, standing.path("totalScore").asInt());
    for (var standing : p.path("standings")) {
      var row = Row.from(standing);
      var score = Ui.label(Integer.toString(row.totalScore()));
      score.getStyleClass().add("leaderboard-score");
      score.setMinWidth(Region.USE_PREF_SIZE);
      finalScores.put(score, row.totalScore());
      var rank = Ui.label("#" + row.rank());
      rank.getStyleClass().add("leaderboard-rank");
      rank.setMinWidth(Region.USE_PREF_SIZE);
      var name = Ui.label(standing.path("displayName").asText());
      name.getStyleClass().add("leaderboard-name");
      var nameRow = new HBox(10, name);
      nameRow.setAlignment(Pos.CENTER_LEFT);
      if (row.userId() == self) {
        var you = Ui.label("BẠN");
        you.getStyleClass().add("you-tag");
        nameRow.getChildren().add(you);
      }
      var detail =
          Ui.muted(
              "+" + row.earnedPoints() + " điểm câu này · " + row.correctCount() + " câu đúng");
      var text = new VBox(4, nameRow, detail);
      HBox.setHgrow(text, Priority.ALWAYS);
      var line =
          new HBox(
              18,
              rank,
              new AssetLoader().view(standing.path("avatarId").asText(), 56),
              text,
              score);
      line.setAlignment(Pos.CENTER_LEFT);
      line.getStyleClass().add("leaderboard-row");
      if (row.totalScore() == best) line.getStyleClass().add("leader");
      if (row.userId() == self) line.getStyleClass().add("self");
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
  }

  /** One line saying who leads and by how much, from the local player's point of view. */
  static String lead(JsonNode standings, long self) {
    if (standings.size() < 2) return "Bảng điểm";
    JsonNode mine = null, theirs = null;
    for (var s : standings)
      if (s.path("userId").asLong() == self) mine = s;
      else theirs = s;
    if (mine == null) {
      mine = standings.get(0);
      theirs = standings.get(1);
    }
    int gap = mine.path("totalScore").asInt() - theirs.path("totalScore").asInt();
    if (gap == 0) return "Hai bên đang hòa điểm";
    return gap > 0
        ? "Bạn đang dẫn " + gap + " điểm"
        : theirs.path("displayName").asText() + " đang dẫn " + -gap + " điểm";
  }

  public void dispose() {
    animations.forEach(Animation::stop);
    finalScores.forEach((label, score) -> label.setText(score.toString()));
  }
}
