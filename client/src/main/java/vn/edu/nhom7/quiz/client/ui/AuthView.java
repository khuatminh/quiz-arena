package vn.edu.nhom7.quiz.client.ui;

import java.util.function.BiConsumer;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class AuthView extends VBox {
  private final Button login, register;
  private final TitledPane settings;

  public AuthView(UiCommandSink sink, BiConsumer<String, Integer> connect) {
    setAlignment(Pos.CENTER);
    getStyleClass().add("auth-view");
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
    var username = new TextField();
    username.setPromptText("Nhập tên tài khoản");
    var password = new PasswordField();
    password.setPromptText("Nhập mật khẩu");
    var display = new TextField();
    display.setPromptText("Tên bạn muốn mọi người nhìn thấy");
    var confirm = new PasswordField();
    confirm.setPromptText("Nhập lại mật khẩu");
    var avatars = new ComboBox<String>();
    for (int i = 1; i <= 8; i++) avatars.getItems().add("avatar-%02d".formatted(i));
    avatars.setValue("avatar-01");
    avatars.setCellFactory(list -> avatarCell());
    avatars.setButtonCell(avatarCell());
    avatars.setMaxWidth(Double.MAX_VALUE);
    var feedback = Ui.label("");
    feedback.getStyleClass().add("auth-feedback");
    feedback.managedProperty().bind(feedback.textProperty().isNotEmpty());
    feedback.visibleProperty().bind(feedback.managedProperty());
    login =
        Ui.button(
            "Đăng nhập",
            () -> {
              if (username.getText().isBlank() || password.getText().isEmpty()) {
                feedback.setText("Vui lòng nhập tên và mật khẩu");
                return;
              }
              sink.send(
                  MessageType.LOGIN,
                  null,
                  null,
                  new Payloads.Login(username.getText(), password.getText()));
              password.clear();
              disableAll();
            });
    register =
        Ui.button(
            "Tạo tài khoản",
            () -> {
              if (username.getText().isBlank()
                  || display.getText().isBlank()
                  || password.getText().isEmpty()) {
                feedback.setText("Hãy điền tên tài khoản, tên hiển thị và mật khẩu.");
                return;
              }
              if (!password.getText().equals(confirm.getText())) {
                feedback.setText("Mật khẩu xác nhận chưa khớp");
                return;
              }
              sink.send(
                  MessageType.REGISTER,
                  null,
                  null,
                  new Payloads.Register(
                      username.getText(),
                      password.getText(),
                      display.getText(),
                      avatars.getValue()));
              password.clear();
              confirm.clear();
              disableAll();
            });
    login.setMaxWidth(Double.MAX_VALUE);
    register.setMaxWidth(Double.MAX_VALUE);
    var heading = Ui.title("Chào mừng trở lại");
    var displayField = Ui.field("Tên hiển thị", display);
    var confirmField = Ui.field("Xác nhận mật khẩu", confirm);
    var avatarField = Ui.field("Ảnh đại diện", avatars);
    var signIn = Ui.secondary("Đăng nhập", () -> {});
    var signUp = Ui.secondary("Đăng ký", () -> {});
    Runnable loginMode =
        () -> {
          heading.setText("Chào mừng trở lại");
          feedback.setText("");
          for (var n : java.util.List.of(displayField, confirmField, avatarField, register)) {
            n.setVisible(false);
            n.setManaged(false);
          }
          login.setVisible(true);
          login.setManaged(true);
          signIn.getStyleClass().add("active");
          signUp.getStyleClass().remove("active");
        };
    Runnable registerMode =
        () -> {
          heading.setText("Tạo tài khoản của bạn");
          feedback.setText("");
          for (var n : java.util.List.of(displayField, confirmField, avatarField, register)) {
            n.setVisible(true);
            n.setManaged(true);
          }
          login.setVisible(false);
          login.setManaged(false);
          signUp.getStyleClass().add("active");
          signIn.getStyleClass().remove("active");
        };
    signIn.getStyleClass().add("topic");
    signUp.getStyleClass().add("topic");
    signIn.setOnAction(e -> loginMode.run());
    signUp.setOnAction(e -> registerMode.run());
    password.setOnAction(
        e -> {
          if (login.isVisible() && !login.isDisabled()) login.fire();
        });
    confirm.setOnAction(
        e -> {
          if (!register.isDisabled()) register.fire();
        });
    var host = new TextField("localhost");
    var port = new TextField("5555");
    host.setPrefColumnCount(12);
    port.setPrefColumnCount(5);
    var connection =
        Ui.secondary(
            "Kết nối",
            () -> {
              try {
                int value = Integer.parseInt(port.getText());
                if (value < 1 || value > 65535 || host.getText().isBlank())
                  throw new IllegalArgumentException();
                feedback.setText("");
                connect.accept(host.getText().strip(), value);
              } catch (Exception e) {
                feedback.setText("Nhập host và cổng hợp lệ (1–65535).");
              }
            });
    settings =
        new TitledPane(
            "Kết nối máy chủ",
            new VBox(10, Ui.actions(Ui.field("Host", host), Ui.field("Port", port)), connection));
    settings.setExpanded(true);
    var form =
        new VBox(
            14,
            Ui.actions(signIn, signUp),
            heading,
            Ui.field("Tên đăng nhập", username),
            Ui.field("Mật khẩu", password),
            displayField,
            confirmField,
            avatarField,
            feedback,
            login,
            register,
            settings);
    form.getStyleClass().add("auth-form");
    form.setPrefWidth(440);
    form.setMinWidth(370);
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
    loginMode.run();
    connected(false);
  }

  private ListCell<String> avatarCell() {
    return new ListCell<>() {
      protected void updateItem(String id, boolean empty) {
        super.updateItem(id, empty);
        setText(empty || id == null ? null : "Ảnh " + Integer.parseInt(id.substring(7)));
        setGraphic(
            empty || id == null
                ? null
                : new vn.edu.nhom7.quiz.client.assets.AssetLoader().view(id, 28));
      }
    };
  }

  private void disableAll() {
    login.setDisable(true);
    register.setDisable(true);
  }

  public void connected(boolean value) {
    login.setDisable(!value);
    register.setDisable(!value);
    settings.setExpanded(!value);
  }
}
