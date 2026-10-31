package vn.edu.nhom7.quiz.client.ui;

import java.util.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/**
 * Loads question media once, then acknowledges readiness only if the same match and round remain.
 */
final class QuestionPreparation {
  private final ClientStore store;
  private final UiCommandSink commands;
  private final Consumer<String> failure;
  private final Set<UUID> readyRounds = new HashSet<>();

  QuestionPreparation(ClientStore store, UiCommandSink commands, Consumer<String> failure) {
    this.store = store;
    this.commands = commands;
    this.failure = failure;
  }

  void clear() {
    readyRounds.clear();
  }

  void prepare(ClientState state) {
    if (state.phase().equals("QUESTION_PREPARE")
        && state.roundId() != null
        && readyRounds.add(state.roundId())
        && state.question() != null) {
      var prepared =
          state.question().hasNonNull("questionAssetId")
              ? new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                  .load(state.question().path("questionAssetId").asText())
              : java.util.concurrent.CompletableFuture.completedFuture(null);
      prepared.whenComplete(
          (image, error) ->
              Platform.runLater(
                  () -> {
                    if (!Objects.equals(store.state().roundId(), state.roundId())
                        || !Objects.equals(store.state().activeMatchId(), state.activeMatchId()))
                      return;
                    if (error != null) {
                      failure.accept(
                          "Không thể tải/giải mã ảnh câu hỏi. Đang chờ server hủy trận an toàn.");
                    } else
                      commands.send(
                          MessageType.QUESTION_READY,
                          state.activeMatchId(),
                          state.roundId(),
                          new Payloads.QuestionReady(state.question().path("questionId").asLong()));
                  }));
    }
  }
}
