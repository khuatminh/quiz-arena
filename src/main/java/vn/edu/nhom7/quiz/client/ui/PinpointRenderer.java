package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class PinpointRenderer extends BaseQuestionRenderer {
  private ImageTarget target;
  private JsonNode selected;
  private Button submit;
  private boolean enabled;

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    selected = null;
    enabled = true;
    submit =
        Ui.button(
            "Khóa vị trí",
            () -> {
              if (!enabled || selected == null) return;
              var value = selected.deepCopy();
              setControlsEnabled(false);
              answer.accept(value);
            });
    submit.setId("pinpoint-submit");
    submit.setDisable(true);
    target =
        new ImageTarget(
            q.questionAssetId(),
            false,
            value -> {
              selected = value;
              submit.setDisable(!enabled);
            });
    controls.add(submit);
    box.getChildren()
        .addAll(
            Ui.muted("Bấm ảnh để đặt ghim · Phím mũi tên để điều chỉnh · Khóa vị trí để gửi"),
            target,
            submit);
  }

  public void submit() {
    if (submit != null && !submit.isDisabled()) submit.fire();
  }

  @Override
  public void setControlsEnabled(boolean value) {
    enabled = value;
    super.setControlsEnabled(value);
    if (target != null) target.setInputEnabled(value);
    if (submit != null) submit.setDisable(!value || selected == null);
  }

  @Override
  public void showResult(Payloads.QuestionResult result, long self) {
    setControlsEnabled(false);
    target.clearPins();
    target.setRegion(result.correctAnswer());
    for (var o : result.outcomes())
      if (o.answer() != null && !o.answer().isNull())
        target.addPin(o.answer(), o.userId() == self ? Color.web("#e49c23") : Color.web("#647eea"));
    box.getChildren().add(Ui.muted("Vùng xanh: đáp án đúng · Ghim vàng: bạn · Ghim tím: đối thủ"));
  }
}
