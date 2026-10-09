package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class SingleChoiceRenderer extends BaseQuestionRenderer {
  public void activateOption(char key) {
    int i = Character.toUpperCase(key) - 'A';
    if (i >= 0
        && i < controls.size()
        && controls.get(i) instanceof javafx.scene.control.Button b
        && !b.isDisabled()) b.fire();
  }

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    for (var o : q.options())
      answerControls.put(
          o.id(),
          option(
              o.id() + ".  " + o.text(),
              () -> {
                setControlsEnabled(false);
                answer.accept(AnswerIntentFactory.single(o.id()));
              }));
  }
}
