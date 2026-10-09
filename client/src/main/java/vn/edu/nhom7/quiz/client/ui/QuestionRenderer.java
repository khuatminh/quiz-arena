package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import javafx.scene.Node;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public interface QuestionRenderer {
  Node view();

  void bind(Payloads.PublicQuestion question, Consumer<JsonNode> answer);

  void setControlsEnabled(boolean enabled);

  void showResult(Payloads.QuestionResult result, long selfUserId);
}
