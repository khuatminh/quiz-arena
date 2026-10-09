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
