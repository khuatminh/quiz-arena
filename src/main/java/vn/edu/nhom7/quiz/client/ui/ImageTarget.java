package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import vn.edu.nhom7.quiz.client.assets.AssetLoader;

/** All coordinates refer to the actual image bounds, excluding letterboxing. */
final class ImageTarget extends Pane {
  private final ImageView image = new ImageView();
  private final Label loading = Ui.muted("Đang tải ảnh…");
  private final Rectangle region = new Rectangle();
  private final List<Circle> pins = new ArrayList<>();
  private JsonNode bounds;
  private boolean ready, enabled = true, dragging;
  private double startX, startY, cursorX = .5, cursorY = .5;
  private double left, top, imageWidth, imageHeight;

  ImageTarget(String asset, boolean selectRegion, Consumer<JsonNode> selected) {
    setId(selectRegion ? "pinpoint-region-editor" : "pinpoint-image");
    setPrefSize(560, 250);
    setMinSize(120, 180);
    setMaxHeight(280);
    setFocusTraversable(true);
    setAccessibleText(
        selectRegion
            ? "Kéo trên ảnh để khoanh vùng đúng"
            : "Bấm ảnh hoặc dùng phím mũi tên để đặt ghim");
    image.setMouseTransparent(true);
    region.setFill(Color.web("#40b778", .25));
    region.setStroke(Color.web("#2d945e"));
    region.setStrokeWidth(2);
    region.setMouseTransparent(true);
    region.setVisible(false);
    loading.setMouseTransparent(true);
    getChildren().addAll(image, region, loading);
    if (asset == null || asset.isBlank()) loading.setText("Chọn ảnh câu hỏi để khoanh vùng đúng.");
    else
      new AssetLoader()
          .load(asset)
          .whenComplete(
              (value, error) ->
                  Platform.runLater(
                      () -> {
                        if (error != null) {
                          loading.setText("Không tải được ảnh. Hãy mở lại câu hỏi để thử lại.");
                          return;
                        }
                        image.setImage(value);
                        ready = true;
                        loading.setVisible(false);
                        requestLayout();
                      }));
    setOnMousePressed(
        e -> {
          if (!enabled || !ready || !inside(e.getX(), e.getY())) return;
          requestFocus();
          startX = nx(e.getX());
          startY = ny(e.getY());
          dragging = selectRegion;
          if (!selectRegion) {
            cursorX = startX;
            cursorY = startY;
            JsonNode point = point(cursorX, cursorY);
            clearPins();
            addPin(point, Color.web("#e49c23"));
            selected.accept(point);
          }
          e.consume();
        });
    setOnMouseDragged(
        e -> {
          if (!selectRegion || !dragging || !enabled || !ready || !e.isPrimaryButtonDown()) return;
          setRegion(rectangle(startX, startY, nx(e.getX()), ny(e.getY())));
          e.consume();
        });
    setOnMouseReleased(
        e -> {
          if (!selectRegion || !dragging || !enabled || !ready) return;
          dragging = false;
          JsonNode rect = rectangle(startX, startY, nx(e.getX()), ny(e.getY()));
          if (rect.path("width").asDouble() > 0 && rect.path("height").asDouble() > 0) {
            setRegion(rect);
            selected.accept(rect);
          }
          e.consume();
        });
    setOnKeyPressed(
        e -> {
          if (selectRegion || !enabled || !ready) return;
          double dx = e.getCode() == KeyCode.LEFT ? -.01 : e.getCode() == KeyCode.RIGHT ? .01 : 0;
          double dy = e.getCode() == KeyCode.UP ? -.01 : e.getCode() == KeyCode.DOWN ? .01 : 0;
          if (dx == 0 && dy == 0 && e.getCode() != KeyCode.SPACE) return;
          cursorX = Math.clamp(cursorX + dx, 0, 1);
          cursorY = Math.clamp(cursorY + dy, 0, 1);
          var point = point(cursorX, cursorY);
          clearPins();
          addPin(point, Color.web("#e49c23"));
          selected.accept(point);
          e.consume();
        });
  }

  void setInputEnabled(boolean value) {
    enabled = value;
  }

  void setRegion(JsonNode value) {
    bounds = value;
    requestLayout();
  }

  void clearPins() {
    getChildren().removeAll(pins);
    pins.clear();
  }

  void addPin(JsonNode point, Color color) {
    var circle = new Circle(7, color);
    circle.setStroke(Color.WHITE);
    circle.setStrokeWidth(2);
    circle.setMouseTransparent(true);
    circle.setUserData(point.deepCopy());
    pins.add(circle);
    getChildren().add(circle);
    requestLayout();
  }

  private boolean inside(double x, double y) {
    return x >= left && x <= left + imageWidth && y >= top && y <= top + imageHeight;
  }

  private double nx(double x) {
    return Math.clamp((x - left) / imageWidth, 0, 1);
  }

  private double ny(double y) {
    return Math.clamp((y - top) / imageHeight, 0, 1);
  }

  static BigDecimal decimal(double n) {
    return BigDecimal.valueOf(n).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
  }

  static ObjectNode point(double x, double y) {
    return JsonNodeFactory.instance.objectNode().put("x", decimal(x)).put("y", decimal(y));
  }

  static ObjectNode rectangle(double x1, double y1, double x2, double y2) {
    var x = decimal(Math.min(x1, x2));
    var y = decimal(Math.min(y1, y2));
    return JsonNodeFactory.instance
        .objectNode()
        .put("x", x)
        .put("y", y)
        .put("width", decimal(Math.max(x1, x2)).subtract(x))
        .put("height", decimal(Math.max(y1, y2)).subtract(y));
  }

  @Override
  protected void layoutChildren() {
    loading.relocate(12, 12);
    loading.autosize();
    if (!ready) return;
    double scale =
        Math.min(
            getWidth() / image.getImage().getWidth(), getHeight() / image.getImage().getHeight());
    imageWidth = image.getImage().getWidth() * scale;
    imageHeight = image.getImage().getHeight() * scale;
    left = (getWidth() - imageWidth) / 2;
    top = (getHeight() - imageHeight) / 2;
    image.setFitWidth(imageWidth);
    image.setFitHeight(imageHeight);
    image.relocate(left, top);
    region.setVisible(bounds != null);
    if (bounds != null) {
      region.setX(left + bounds.path("x").asDouble() * imageWidth);
      region.setY(top + bounds.path("y").asDouble() * imageHeight);
      region.setWidth(bounds.path("width").asDouble() * imageWidth);
      region.setHeight(bounds.path("height").asDouble() * imageHeight);
    }
    for (var circle : pins) {
      JsonNode p = (JsonNode) circle.getUserData();
      circle.setCenterX(left + p.path("x").asDouble() * imageWidth);
      circle.setCenterY(top + p.path("y").asDouble() * imageHeight);
    }
  }
}
