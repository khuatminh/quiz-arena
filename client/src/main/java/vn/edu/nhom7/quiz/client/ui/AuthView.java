package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.function.BiConsumer;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class AuthView extends VBox {
  private final Button login, register;

  public AuthView(UiCommandSink sink, BiConsumer<String, Integer> connect) {
    setSpacing(16);
    setPadding(new Insets(48));
    setMaxWidth(600);
    getChildren()
        .addAll(
            Ui.title("QUIZ ARENA"), Ui.label("Đấu trí cùng bạn bè · 10 câu hỏi · 4 cách trả lời"));
    var host = new TextField("localhost");
    host.setPrefWidth(160);
    var port = new TextField("5555");
    port.setPrefWidth(80);
    var connection =
        Ui.button(
            "Kết nối",
            () -> {
              try {
                connect.accept(host.getText().strip(), Integer.parseInt(port.getText()));
              } catch (Exception e) {
                port.setPromptText("Port phải là số");
              }
            });
    connection.setMinWidth(110);
    getChildren().addAll(new HBox(12, host, port, connection));
    var username = new TextField();
    username.setPromptText("Tên đăng nhập");
    var password = new PasswordField();
    password.setPromptText("Mật khẩu");
    var display = new TextField();
    display.setPromptText("Tên hiển thị (khi đăng ký)");
    var confirm = new PasswordField();
    confirm.setPromptText("Nhập lại mật khẩu (khi đăng ký)");
    var avatars = new ComboBox<String>();
    for (int i = 1; i <= 8; i++) avatars.getItems().add("avatar-%02d".formatted(i));
    avatars.setValue("avatar-01");
    var feedback = Ui.label("");
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
    login.setDisable(true);
    register.setDisable(true);
    getChildren()
        .addAll(
            username, password, display, confirm, avatars, new HBox(12, login, register), feedback);
  }

  private void disableAll() {
    login.setDisable(true);
    register.setDisable(true);
  }

  public void connected(boolean value) {
    login.setDisable(!value);
    register.setDisable(!value);
  }
}
