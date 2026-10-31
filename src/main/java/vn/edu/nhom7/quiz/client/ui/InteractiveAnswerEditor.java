package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.common.protocol.InteractiveQuestions;

/** Visual authoring controls; private keys are never passed to a live question renderer. */
final class InteractiveAnswerEditor {
  private static final ObjectMapper JSON = new ObjectMapper();

  private InteractiveAnswerEditor() {}

  static void render(
      VBox target,
      String kind,
      ArrayNode options,
      JsonNode key,
      String asset,
      BiConsumer<ArrayNode, JsonNode> changed) {
    if (kind.equals("REORDER")) {
      reorder(target, options, key, changed);
      return;
    }
    ObjectNode values =
        key != null && key.isObject() ? (ObjectNode) key.deepCopy() : JSON.createObjectNode();
    if (kind.equals("RANGE")) {
      String[][] fields = {
        {"min", "Giá trị nhỏ nhất", "0"},
        {"max", "Giá trị lớn nhất", "100"},
        {"step", "Bước nhảy", "1"},
        {"unit", "Đơn vị", ""},
        {"acceptedMin", "Chấp nhận từ", ""},
        {"acceptedMax", "Chấp nhận đến", ""}
      };
      target
          .getChildren()
          .add(Ui.muted("Đặt thang số, bước nhảy và khoảng đáp án đúng (bao gồm hai đầu)."));
      for (String[] f : fields) {
        var input = new TextField(values.hasNonNull(f[0]) ? values.path(f[0]).asText() : f[2]);
        input.setId("range-editor-" + f[0]);
        Runnable sync =
            () -> {
              if (f[0].equals("unit")) values.put(f[0], input.getText());
              else values.set(f[0], numeric(input.getText(), false));
              changed.accept(JSON.createArrayNode(), values.deepCopy());
            };
        input.textProperty().addListener((o, a, b) -> sync.run());
        sync.run();
        target.getChildren().add(Ui.field(f[1], input));
      }
    } else {
      target
          .getChildren()
          .add(
              Ui.muted(
                  "Kéo trên ảnh để khoanh vùng đúng; có thể nhập vị trí/kích thước theo % ảnh."));
      var inputs = new LinkedHashMap<String, TextField>();
      var image =
          new ImageTarget(
              asset,
              true,
              region -> {
                for (var field : inputs.entrySet())
                  field
                      .getValue()
                      .setText(
                          region
                              .path(field.getKey())
                              .decimalValue()
                              .multiply(new BigDecimal("100"))
                              .stripTrailingZeros()
                              .toPlainString());
              });
      target.getChildren().add(image);
      for (String[] f :
          new String[][] {
            {"x", "Trái (%)"}, {"y", "Trên (%)"}, {"width", "Rộng (%)"}, {"height", "Cao (%)"}
          }) {
        var input =
            new TextField(
                values.path(f[0]).isNumber()
                    ? values
                        .path(f[0])
                        .decimalValue()
                        .multiply(new BigDecimal("100"))
                        .stripTrailingZeros()
                        .toPlainString()
                    : values.path(f[0]).asText(""));
        input.setId("pinpoint-editor-" + f[0]);
        inputs.put(f[0], input);
        input
            .textProperty()
            .addListener(
                (o, a, b) -> {
                  values.set(f[0], numeric(input.getText(), true));
                  try {
                    InteractiveQuestions.validateRegion(values);
                    image.setRegion(values.deepCopy());
                  } catch (IllegalArgumentException invalid) {
                    image.setRegion(null);
                  }
                  changed.accept(JSON.createArrayNode(), values.deepCopy());
                });
        target.getChildren().add(Ui.field(f[1], input));
      }
      try {
        InteractiveQuestions.validateRegion(values);
        image.setRegion(values);
      } catch (IllegalArgumentException ignored) {
      }
      changed.accept(JSON.createArrayNode(), values.deepCopy());
    }
  }

  private static JsonNode numeric(String text, boolean percent) {
    try {
      BigDecimal value = new BigDecimal(text.strip().replace(',', '.'));
      if (percent) value = value.movePointLeft(2);
      return DecimalNode.valueOf(value.stripTrailingZeros());
    } catch (NumberFormatException e) {
      return TextNode.valueOf(text);
    }
  }

  private static void reorder(
      VBox target, ArrayNode options, JsonNode key, BiConsumer<ArrayNode, JsonNode> changed) {
    var items = new ArrayList<ObjectNode>();
    if (key != null && key.isArray())
      for (var id : key)
        for (var option : options)
          if (id.asText().equals(option.path("id").asText()))
            items.add((ObjectNode) option.deepCopy());
    for (var option : options)
      if (items.stream().noneMatch(v -> v.path("id").equals(option.path("id"))))
        items.add((ObjectNode) option.deepCopy());
    if (items.isEmpty()) for (int i = 0; i < 3; i++) items.add(item());
    reorderRows(target, items, changed);
  }

  private static ObjectNode item() {
    return JSON.createObjectNode().put("id", UUID.randomUUID().toString()).put("text", "");
  }

  private static void reorderRows(
      VBox target, List<ObjectNode> items, BiConsumer<ArrayNode, JsonNode> changed) {
    target
        .getChildren()
        .setAll(
            Ui.label("Nhập các mục theo thứ tự ĐÚNG"),
            Ui.muted("3–6 mục · Khi chơi, các mục sẽ được xáo trộn."));
    Runnable sync =
        () -> {
          ArrayNode options = JSON.createArrayNode(), key = JSON.createArrayNode();
          // Canonical option order is independent of the answer order.
          items.stream()
              .sorted(Comparator.comparing(v -> v.path("id").asText()))
              .forEach(v -> options.add(v.deepCopy()));
          items.forEach(v -> key.add(v.path("id").asText()));
          changed.accept(options, key);
        };
    for (int i = 0; i < items.size(); i++) {
      int n = i;
      ObjectNode item = items.get(i);
      var input = new TextField(item.path("text").asText());
      input.setId("reorder-editor-" + i);
      input.setPromptText("Mục " + (i + 1) + " · tối đa 120 ký tự");
      HBox.setHgrow(input, Priority.ALWAYS);
      input
          .textProperty()
          .addListener(
              (o, old, value) -> {
                item.put("text", value);
                sync.run();
              });
      var up =
          Ui.secondary(
              "↑",
              () -> {
                Collections.swap(items, n, n - 1);
                reorderRows(target, items, changed);
              });
      var down =
          Ui.secondary(
              "↓",
              () -> {
                Collections.swap(items, n, n + 1);
                reorderRows(target, items, changed);
              });
      up.setDisable(i == 0);
      down.setDisable(i == items.size() - 1);
      up.setAccessibleText("Đưa mục " + (i + 1) + " lên");
      down.setAccessibleText("Đưa mục " + (i + 1) + " xuống");
      var remove =
          Ui.secondary(
              "×",
              () -> {
                items.remove(n);
                reorderRows(target, items, changed);
              });
      remove.setAccessibleText("Xóa mục " + (i + 1));
      remove.setDisable(items.size() <= 3);
      target.getChildren().add(new HBox(8, Ui.label((i + 1) + "."), input, up, down, remove));
    }
    var add =
        Ui.secondary(
            "+ Thêm mục",
            () -> {
              items.add(item());
              reorderRows(target, items, changed);
            });
    add.setDisable(items.size() >= 6);
    target.getChildren().add(add);
    sync.run();
  }
}
