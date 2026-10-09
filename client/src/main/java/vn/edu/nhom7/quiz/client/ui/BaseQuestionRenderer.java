package vn.edu.nhom7.quiz.client.ui;

import java.util.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

abstract class BaseQuestionRenderer implements QuestionRenderer {
  protected final VBox box = new VBox(12);
  protected final List<Control> controls = new ArrayList<>();
  protected final Map<String, Control> answerControls = new LinkedHashMap<>();
  private com.fasterxml.jackson.databind.JsonNode players;

  public void setPlayers(com.fasterxml.jackson.databind.JsonNode p) {
    players = p;
  }

  public Node view() {
    return box;
  }

  protected Button option(String text, Runnable action) {
    var b = new Button(text);
    b.setWrapText(true);
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMinHeight(56);
    b.getStyleClass().add("answer-card");
    b.setOnAction(e -> action.run());
    controls.add(b);
    box.getChildren().add(b);
    return b;
  }

  public void setControlsEnabled(boolean enabled) {
    controls.forEach(c -> c.setDisable(!enabled));
  }

  public void showResult(Payloads.QuestionResult r, long self) {
    setControlsEnabled(false);
    var correct = new java.util.HashSet<String>();
    if (r.correctAnswer() != null) {
      if (r.correctAnswer().isArray()) r.correctAnswer().forEach(n -> correct.add(n.asText()));
      else correct.add(r.correctAnswer().asText());
    }
    for (var entry : answerControls.entrySet())
      if (correct.contains(entry.getKey())) {
        entry.getValue().getStyleClass().add("correct");
        entry.getValue().setAccessibleText("Đáp án đúng: " + entry.getKey());
        int correctIndex = box.getChildren().indexOf(entry.getValue());
        box.getChildren().add(correctIndex + 1, Ui.label("✓ Đáp án đúng"));
      }
    for (var outcome : r.outcomes()) {
      if (outcome.answer() == null) continue;
      var selected = new java.util.HashSet<String>();
      if (outcome.answer().isArray()) outcome.answer().forEach(n -> selected.add(n.asText()));
      else selected.add(outcome.answer().asText());
      for (String id : selected) {
        Control control = answerControls.get(id);
        if (control == null) continue;
        if (!correct.contains(id)) control.getStyleClass().add("incorrect");
        String name = outcome.userId() == self ? "Bạn" : "Đối thủ";
        String avatar = "avatar-01";
        if (players != null)
          for (var player : players)
            if (player.path("userId").asLong() == outcome.userId()) {
              name =
                  player.path("displayName").asText() + (outcome.userId() == self ? " · Bạn" : "");
              avatar = player.path("avatarId").asText();
            }
        var chip =
            new javafx.scene.layout.HBox(
                8,
                new vn.edu.nhom7.quiz.client.assets.AssetLoader().view(avatar, 28),
                Ui.label(
                    name
                        + " · chọn "
                        + (id.equals("true") ? "Đúng" : id.equals("false") ? "Sai" : id)
                        + (Boolean.TRUE.equals(outcome.correct()) ? " ✓" : " ✗")));
        int index = box.getChildren().indexOf(control);
        box.getChildren().add(index + 1, chip);
      }
    }
  }

  protected void clear() {
    box.getChildren().clear();
    controls.clear();
    answerControls.clear();
  }
}
