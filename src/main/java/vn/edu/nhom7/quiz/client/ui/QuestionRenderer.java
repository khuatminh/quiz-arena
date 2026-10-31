package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import javafx.scene.Node;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public interface QuestionRenderer {
  /** Shared renderer selection for live play and author previews. */
  static QuestionRenderer forType(String type) {
    return switch (type) {
      case "MULTIPLE_CHOICE" -> new MultipleChoiceRenderer();
      case "TRUE_FALSE" -> new TrueFalseRenderer();
      case "SHORT_ANSWER" -> new ShortAnswerRenderer();
      case "REORDER" -> new ReorderRenderer();
      case "RANGE" -> new RangeRenderer();
      case "PINPOINT" -> new PinpointRenderer();
      default -> new SingleChoiceRenderer();
    };
  }

  Node view();

  void bind(Payloads.PublicQuestion question, Consumer<JsonNode> answer);

  void setControlsEnabled(boolean enabled);

  void showResult(Payloads.QuestionResult result, long selfUserId);
}
