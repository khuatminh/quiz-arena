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
    b.setOnAction(e -> r.run());
    return b;
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
    return s;
  }
}
