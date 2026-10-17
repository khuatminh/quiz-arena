package vn.edu.nhom7.quiz.client.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

final class Ui {
  static Label label(String text) {
    var l = new Label(text);
    l.setWrapText(true);
    return l;
  }

  static Label title(String text) {
    var l = label(text);
    l.getStyleClass().add("title");
    return l;
  }

  static Button button(String text, Runnable r) {
    var b = new Button(text);
    b.setMinHeight(40);
    b.setMinWidth(Region.USE_PREF_SIZE);
    b.setOnAction(e -> r.run());
    return b;
  }

  static Label muted(String text) {
    var l = label(text);
    l.getStyleClass().add("muted");
    return l;
  }

  static Label badge(String text) {
    var l = label(text);
    l.getStyleClass().add("badge");
    return l;
  }

  static Button secondary(String text, Runnable r) {
    var b = button(text, r);
    b.getStyleClass().add("secondary");
    return b;
  }

  static HBox actions(javafx.scene.Node... nodes) {
    var row = new HBox(10, nodes);
    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    return row;
  }

  static VBox field(String text, javafx.scene.Node control) {
    var l = label(text);
    l.setLabelFor(control);
    var v = new VBox(6, l, control);
    return v;
  }

  /** Leaving before the result is a forfeit on the server, so ask first. */
  static boolean confirmLeaveMatch(javafx.scene.Node owner) {
    var alert =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "Nếu rời trận bây giờ, bạn sẽ bị xử thua và trận đấu kết thúc cho cả hai người.",
            new ButtonType("Rời trận", ButtonBar.ButtonData.OK_DONE),
            new ButtonType("Ở lại", ButtonBar.ButtonData.CANCEL_CLOSE));
    alert.setTitle("Rời trận?");
    alert.setHeaderText("Bạn chắc chắn muốn rời trận?");
    if (owner != null && owner.getScene() != null) {
      alert.initOwner(owner.getScene().getWindow());
      alert.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
    }
    return alert
        .showAndWait()
        .map(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
        .orElse(false);
  }

  /** One item of a keyboard hint bar: a keycap and what it does. */
  static HBox prompt(String keys, String action) {
    var text = label(action);
    text.getStyleClass().add("prompt-text");
    text.setWrapText(false);
    var item = new HBox(8, text);
    if (keys != null) {
      var cap = label(keys);
      cap.getStyleClass().add("prompt-key");
      cap.setWrapText(false);
      cap.setMinWidth(Region.USE_PREF_SIZE);
      item.getChildren().addFirst(cap);
    }
    item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    item.setMinWidth(Region.USE_PREF_SIZE);
    return item;
  }

  /** A small uppercase heading in the display font. */
  static Label kicker(String text) {
    var l = new Label(text);
    l.getStyleClass().add("kicker");
    return l;
  }

  static Region spacer() {
    var r = new Region();
    HBox.setHgrow(r, Priority.ALWAYS);
    return r;
  }

  static VBox stack(javafx.scene.Node... nodes) {
    var v = new VBox(16, nodes);
    v.setPadding(new Insets(24));
    v.setFillWidth(true);
    return v;
  }

  static ScrollPane scroll(javafx.scene.Node node) {
    var s = new ScrollPane(node);
    s.setFitToWidth(true);
    s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    s.setMinWidth(0);
    s.setFocusTraversable(false);
    if (node.getStyleClass().contains("waiting-panel")) s.getStyleClass().add("waiting-scroll");
    return s;
  }
}
