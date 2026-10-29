package vn.edu.nhom7.quiz.client.ui;

import java.util.List;
import java.util.function.BiConsumer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Authentication inputs, login/register modes and server connection settings. */
final class AuthForm extends VBox {
  private final UiCommandSink sink;
  private final TextField username = new TextField();
  private final TextField display = new TextField();
  private final PasswordField password = new PasswordField();
  private final PasswordField confirm = new PasswordField();
  private final ComboBox<String> avatars = new ComboBox<>();
  private final Label feedback = Ui.label("");
  private final Label heading = Ui.title("Chào mừng trở lại");
  private final Button login;
  private final Button register;
  private final Button signIn = Ui.secondary("Đăng nhập", () -> showRegistration(false));
  private final Button signUp = Ui.secondary("Đăng ký", () -> showRegistration(true));
  private final List<Node> registrationFields;
  private final TitledPane settings;

  AuthForm(UiCommandSink sink, BiConsumer<String, Integer> connect) {
    super(14);
    this.sink = sink;
    username.setPromptText("Nhập tên tài khoản");
    password.setPromptText("Nhập mật khẩu");
    display.setPromptText("Tên bạn muốn mọi người nhìn thấy");
    confirm.setPromptText("Nhập lại mật khẩu");
    configureAvatars();
    feedback.getStyleClass().add("auth-feedback");
    feedback.managedProperty().bind(feedback.textProperty().isNotEmpty());
    feedback.visibleProperty().bind(feedback.managedProperty());
    login = Ui.button("Đăng nhập", this::login);
    register = Ui.button("Tạo tài khoản", this::register);
    login.setMaxWidth(Double.MAX_VALUE);
    register.setMaxWidth(Double.MAX_VALUE);
    var displayField = Ui.field("Tên hiển thị", display);
    var confirmField = Ui.field("Xác nhận mật khẩu", confirm);
    var avatarField = Ui.field("Ảnh đại diện", avatars);
    registrationFields = List.of(displayField, confirmField, avatarField, register);
    signIn.getStyleClass().add("topic");
    signUp.getStyleClass().add("topic");
    password.setOnAction(
        event -> {
          if (login.isVisible() && !login.isDisabled()) login.fire();
        });
    confirm.setOnAction(
        event -> {
          if (!register.isDisabled()) register.fire();
        });
    settings = connectionSettings(connect);
    getChildren()
        .addAll(
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
    getStyleClass().add("auth-form");
    setPrefWidth(440);
    setMinWidth(370);
    showRegistration(false);
  }

  private void login() {
    if (username.getText().isBlank() || password.getText().isEmpty()) {
      feedback.setText("Vui lòng nhập tên và mật khẩu");
      return;
    }
    sink.send(
        MessageType.LOGIN, null, null, new Payloads.Login(username.getText(), password.getText()));
    password.clear();
    disableAll();
  }

  private void register() {
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
            username.getText(), password.getText(), display.getText(), avatars.getValue()));
    password.clear();
    confirm.clear();
    disableAll();
  }

  private void showRegistration(boolean registering) {
    heading.setText(registering ? "Tạo tài khoản của bạn" : "Chào mừng trở lại");
    feedback.setText("");
    for (Node field : registrationFields) {
      field.setVisible(registering);
      field.setManaged(registering);
    }
    login.setVisible(!registering);
    login.setManaged(!registering);
    signIn.getStyleClass().remove("active");
    signUp.getStyleClass().remove("active");
    (registering ? signUp : signIn).getStyleClass().add("active");
  }

  private TitledPane connectionSettings(BiConsumer<String, Integer> connect) {
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
              } catch (Exception failure) {
                feedback.setText("Nhập host và cổng hợp lệ (1–65535).");
              }
            });
    var pane =
        new TitledPane(
            "Kết nối máy chủ",
            new VBox(10, Ui.actions(Ui.field("Host", host), Ui.field("Port", port)), connection));
    pane.setExpanded(true);
    return pane;
  }

  private void configureAvatars() {
    for (int index = 1; index <= 8; index++) avatars.getItems().add("avatar-%02d".formatted(index));
    avatars.setValue("avatar-01");
    avatars.setCellFactory(list -> avatarCell());
    avatars.setButtonCell(avatarCell());
    avatars.setMaxWidth(Double.MAX_VALUE);
  }

  private static ListCell<String> avatarCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(String id, boolean empty) {
        super.updateItem(id, empty);
        setText(empty || id == null ? null : "Ảnh " + Integer.parseInt(id.substring(7)));
        setGraphic(empty || id == null ? null : new AssetLoader().view(id, 28));
      }
    };
  }

  private void disableAll() {
    login.setDisable(true);
    register.setDisable(true);
  }

  void connected(boolean connected) {
    login.setDisable(!connected);
    register.setDisable(!connected);
    settings.setExpanded(!connected);
  }
}
