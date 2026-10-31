package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import java.math.*;
import java.util.function.Consumer;
import javafx.scene.control.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class RangeRenderer extends BaseQuestionRenderer {
  private boolean enabled, syncing, touched;
  private BigDecimal min, max, step;
  private TextField field;
  private Slider slider;
  private Button submit;

  public void bind(Payloads.PublicQuestion q, Consumer<JsonNode> answer) {
    clear();
    enabled = true;
    touched = false;
    min = InteractiveQuestions.number(q.config().path("min"));
    max = InteractiveQuestions.number(q.config().path("max"));
    step = InteractiveQuestions.number(q.config().path("step"));
    int ticks = max.subtract(min).divide(step).intValueExact();
    slider = new Slider(0, ticks, ticks / 2);
    slider.setId("range-slider");
    slider.setBlockIncrement(1);
    slider.setMajorTickUnit(1);
    slider.setMinorTickCount(0);
    slider.setSnapToTicks(true);
    slider.setAccessibleText("Chọn giá trị ước lượng");
    field = new TextField();
    field.setId("range-answer");
    field.setPromptText("Nhập số hoặc kéo thanh trượt");
    field.setAccessibleText("Giá trị ước lượng");
    var error = Ui.muted("");
    submit =
        Ui.button(
            "Khóa giá trị",
            () -> {
              if (!enabled) return;
              try {
                BigDecimal value = value();
                setControlsEnabled(false);
                answer.accept(DecimalNode.valueOf(value));
              } catch (IllegalArgumentException e) {
                error.setText("Nhập số trong thang đo và đúng bước nhảy.");
              }
            });
    submit.setId("range-submit");
    field.setOnAction(e -> submit());
    field
        .textProperty()
        .addListener(
            (o, old, text) -> {
              if (syncing) return;
              touched = !text.isBlank();
              try {
                var value = value();
                syncing = true;
                slider.setValue(value.subtract(min).divide(step).doubleValue());
              } catch (IllegalArgumentException ignored) {
                // Keep the user's partial or invalid input visible until corrected.
              } finally {
                syncing = false;
              }
              updateSubmit();
            });
    slider
        .valueProperty()
        .addListener(
            (o, old, value) -> {
              if (syncing) return;
              syncing = true;
              field.setText(
                  min.add(step.multiply(BigDecimal.valueOf(Math.round(value.doubleValue()))))
                      .stripTrailingZeros()
                      .toPlainString());
              touched = true;
              syncing = false;
              updateSubmit();
            });
    slider.setOnMousePressed(
        e -> {
          if (enabled) {
            field.setText(
                min.add(step.multiply(BigDecimal.valueOf(Math.round(slider.getValue()))))
                    .stripTrailingZeros()
                    .toPlainString());
          }
        });
    controls.add(field);
    controls.add(slider);
    controls.add(submit);
    box.getChildren()
        .addAll(
            Ui.label(
                min.toPlainString()
                    + " → "
                    + max.toPlainString()
                    + " "
                    + q.config().path("unit").asText()
                    + " · Bước "
                    + step.toPlainString()),
            slider,
            field,
            error,
            submit);
    updateSubmit();
  }

  private BigDecimal value() {
    BigDecimal v;
    try {
      v =
          InteractiveQuestions.number(
              DecimalNode.valueOf(new BigDecimal(field.getText().strip().replace(',', '.'))));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid number");
    }
    if (v.compareTo(min) < 0
        || v.compareTo(max) > 0
        || v.subtract(min).remainder(step).signum() != 0)
      throw new IllegalArgumentException("Invalid step");
    return v;
  }

  private void updateSubmit() {
    boolean valid = touched;
    try {
      value();
    } catch (IllegalArgumentException e) {
      valid = false;
    }
    submit.setDisable(!enabled || !valid);
  }

  public void submit() {
    if (submit != null && !submit.isDisabled()) submit.fire();
  }

  @Override
  public void setControlsEnabled(boolean value) {
    enabled = value;
    super.setControlsEnabled(value);
    if (submit != null) updateSubmit();
  }

  @Override
  public void showResult(Payloads.QuestionResult result, long self) {
    setControlsEnabled(false);
    box.getChildren()
        .add(
            Ui.label(
                "Khoảng đúng: "
                    + result.correctAnswer().path("acceptedMin").asText()
                    + " – "
                    + result.correctAnswer().path("acceptedMax").asText()
                    + " "
                    + result.correctAnswer().path("unit").asText()));
  }
}
