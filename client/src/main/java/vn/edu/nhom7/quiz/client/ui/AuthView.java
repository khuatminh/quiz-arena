package vn.edu.nhom7.quiz.client.ui;

import java.util.function.BiConsumer;
import javafx.geometry.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

/** Login screen composition; the form owns input validation and authentication intents. */
public final class AuthView extends VBox {
  private final AuthForm form;

  public AuthView(UiCommandSink sink, BiConsumer<String, Integer> connect) {
    setAlignment(Pos.CENTER);
    getStyleClass().add("auth-view");
    form = new AuthForm(sink, connect);
    var art = illustration();
    var layout = new HBox(art, form);
    layout.setMaxWidth(970);
    layout.setMaxHeight(Region.USE_PREF_SIZE);
    layout.setAlignment(Pos.CENTER);
    HBox.setHgrow(form, Priority.ALWAYS);
    var centered = new StackPane(layout);
    centered.setPadding(new Insets(0));
    var scroll = Ui.scroll(centered);
    getChildren().add(scroll);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    centered.minHeightProperty().bind(scroll.heightProperty().subtract(2));
    connected(false);
  }

  private VBox illustration() {
    var brand = Ui.title("QUIZ ARENA");
    brand.getStyleClass().add("brand");
    var headline = Ui.title("Một chủ đề mới.\nMột cuộc đấu trí mới.");
    headline.getStyleClass().add("display");
    var illustration =
        new ImageView(
            new Image(
                getClass()
                    .getResource("/assets/images/quiz-cover-general-v2.png")
                    .toExternalForm()));
    illustration.setFitWidth(380);
    illustration.setPreserveRatio(true);
    illustration.setAccessibleText("Sách mở, quả địa cầu và cúp giữa khu vườn xanh");
    var art =
        new VBox(
            20,
            brand,
            headline,
            Ui.muted("Chọn quiz yêu thích, thách đấu bạn bè\nvà chia sẻ kiến thức của riêng bạn."),
            illustration);
    art.getStyleClass().add("auth-art");
    art.setPrefWidth(470);
    art.setMinWidth(360);
    art.setAlignment(Pos.CENTER_LEFT);
    return art;
  }

  public void connected(boolean value) {
    form.connected(value);
  }
}
