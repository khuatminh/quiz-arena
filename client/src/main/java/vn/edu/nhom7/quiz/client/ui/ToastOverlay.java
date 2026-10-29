package vn.edu.nhom7.quiz.client.ui;

import java.util.function.BooleanSupplier;
import javafx.animation.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

/** Floating notification with a single owned dismissal timer. */
final class ToastOverlay extends StackPane implements AutoCloseable {
  private final Label message = Ui.label("");
  private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(5));

  ToastOverlay(BooleanSupplier reducedMotion) {
    setMouseTransparent(true);
    // Messages are toasts: they float above the bottom edge and clear themselves.
    message.getStyleClass().add("toast");
    message.setWrapText(false);
    message.setMaxWidth(Region.USE_PREF_SIZE);
    message.visibleProperty().bind(message.textProperty().isNotEmpty());
    message.managedProperty().bind(message.visibleProperty());
    var toastHost = new StackPane(message);
    toastHost.setPickOnBounds(false);
    toastHost.setMouseTransparent(true);
    StackPane.setAlignment(message, javafx.geometry.Pos.BOTTOM_CENTER);
    StackPane.setMargin(message, new javafx.geometry.Insets(0, 24, 64, 24));
    toastTimer.setOnFinished(e -> message.setText(""));
    message
        .textProperty()
        .addListener(
            (o, before, text) -> {
              if (text.isEmpty()) return;
              // Evidence renders keep the toast up until the snapshot.
              if (Effects.preview) return;
              toastTimer.playFromStart();
              if (reducedMotion.getAsBoolean()) return;
              var in = new FadeTransition(Duration.millis(160), message);
              in.setFromValue(0);
              in.setToValue(1);
              in.play();
            });
    getChildren().add(toastHost);
  }

  Label message() {
    return message;
  }

  @Override
  public void close() {
    toastTimer.stop();
  }
}
