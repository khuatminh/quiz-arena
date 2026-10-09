package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vn.edu.nhom7.quiz.client.assets.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Owner-only draft editor. Server responses never replace unsaved fields on failure. */
public final class CommunityQuizView extends BorderPane {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final UiCommandSink sink;
  private final JsonNode categories;
  private final Label status = Ui.label("");
  private final VBox main = new VBox(12);
  private final TextField title = new TextField(), description = new TextField();
  private final ComboBox<String> category = new ComboBox<>(), type = new ComboBox<>();
  private final CheckBox shuffle = new CheckBox("Xáo trộn thứ tự câu");
  private final TextArea content = new TextArea(),
      explanation = new TextArea(),
      answer = new TextArea();
  private ArrayNode optionDraft = JSON.createArrayNode();
  private List<String> choiceAnswer;
  private final VBox answerEditor = new VBox(10);
  private final VBox questionList = new VBox(6),
      form = new VBox(10),
      questionImage = new VBox(6),
      explanationImage = new VBox(6);

  private record PendingRequest(String action, long quizId, long generation) {}

  private final Map<UUID, PendingRequest> pending = new HashMap<>();
  private long quizId;
  private int count, index, page = 1;
  private String questionAsset, explanationAsset, afterSave;
  private boolean formLoaded, busy;
  private long editGeneration;
  private long questionImageGeneration, explanationImageGeneration;

  public CommunityQuizView(UiCommandSink sink, JsonNode categories) {
    this.sink = sink;
    this.categories = categories == null ? JSON.createArrayNode() : categories;
    setPadding(new Insets(24));
    main.getChildren().addAll(Ui.title("Quiz của tôi"), status);
    setCenter(Ui.scroll(main));
    title.setPromptText("Tên quiz · tối đa 128 ký tự");
    description.setPromptText("Mô tả · tối đa 1000 ký tự");
    for (JsonNode c : this.categories) category.getItems().add(c.path("categoryName").asText());
    type.getItems().addAll("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER");
    type.setValue("SINGLE_CHOICE");
    type.setConverter(
        new javafx.util.StringConverter<>() {
          public String toString(String value) {
            if (value == null) return "";
            return switch (value) {
              case "SINGLE_CHOICE" -> "Một đáp án";
              case "MULTIPLE_CHOICE" -> "Nhiều đáp án";
              case "TRUE_FALSE" -> "Đúng / Sai";
              case "SHORT_ANSWER" -> "Trả lời ngắn";
              default -> value;
            };
          }

          public String fromString(String value) {
            return value;
          }
        });
    content.setPromptText("Nội dung câu hỏi · tối đa 500 ký tự");
    content.setPrefRowCount(3);
    content.setWrapText(true);
    explanation.setPromptText("Giải thích · tối đa 500 ký tự");
    explanation.setPrefRowCount(2);
    explanation.setWrapText(true);
    answer.setPromptText(
        "Một đáp án: A · nhiều: A,B · đúng/sai: true hoặc false · ngắn: mỗi dòng một đáp án, tối đa"
            + " 20 đáp án");
    answer.setPrefRowCount(2);
    form.getChildren()
        .addAll(
            Ui.title("Soạn câu hỏi"),
            Ui.label("Loại câu hỏi"),
            type,
            Ui.label("Nội dung câu hỏi · tối đa 500 ký tự"),
            content,
            answerEditor,
            Ui.label("Giải thích · tối đa 500 ký tự"),
            explanation,
            imageControls(false),
            questionImage,
            imageControls(true),
            explanationImage,
            new FlowPane(
                10,
                10,
                Ui.button("Lưu câu", () -> saveQuestion(null)),
                Ui.secondary("Xem trước câu", () -> preview(false)),
                Ui.secondary("Xem công bố đáp án", () -> preview(true))));
    form.getStyleClass().add("editor-panel");
    status.getStyleClass().add("badge");
    status.setMaxWidth(Double.MAX_VALUE);
    main.setMaxWidth(1000);
    type.valueProperty()
        .addListener(
            (o, old, value) -> {
              if (old != null && !old.equals(value)) {
                if (old.endsWith("CHOICE") && value.endsWith("CHOICE")) {
                  if (value.equals("SINGLE_CHOICE")) {
                    if (choiceAnswer != null && !choiceAnswer.isEmpty())
                      choiceAnswer = new ArrayList<>(List.of(choiceAnswer.getFirst()));
                    answer.setText(
                        choiceAnswer == null
                            ? answer.getText().split(",", 2)[0]
                            : choiceAnswer.isEmpty() ? "" : choiceAnswer.getFirst());
                  }
                } else {
                  choiceAnswer = null;
                  answer.clear();
                }
              }
              buildAnswerEditor();
            });
    buildAnswerEditor();
    showList();
  }

  private void request(String action, JsonNode data) {
    if (busy) {
      status.setText("Hãy chờ thao tác hiện tại hoàn tất.");
      return;
    }
    UUID id =
        sink.send(
            MessageType.AUTHOR_REQUEST,
            null,
            null,
            new CommunityPayloads.AuthorRequest(action, quizId, data));
    if (id != null) {
      pending.put(id, new PendingRequest(action, quizId, editGeneration));
      if (action.equals("GET") || action.equals("GET_QUESTION") || action.equals("CREATE"))
        setLoadingFields(true);
      busy = true;
      status.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"), false);
      status.setText("Đang xử lý…");
    } else status.setText("Không thể gửi yêu cầu. Nội dung vẫn được giữ.");
  }

  private ObjectNode data() {
    return JSON.createObjectNode();
  }

  private void showList() {
    if (busy) {
      status.setText("Hãy chờ thao tác hiện tại hoàn tất.");
      return;
    }
    editGeneration++;
    quizId = 0;
    formLoaded = false;
    main.getChildren()
        .setAll(
            Ui.title("Quiz của tôi"),
            status,
            Ui.button("Tạo quiz", () -> request("CREATE", data())));
    request("LIST", data().put("page", page));
  }

  public void onEvent(Envelope e) {
    if (e.type() != MessageType.AUTHOR_RESULT && e.type() != MessageType.ERROR) return;
    PendingRequest request = pending.remove(e.requestId());
    if (request == null || request.quizId() != quizId || request.generation() != editGeneration)
      return;
    String action = request.action();
    busy = false;
    setLoadingFields(false);
    status.pseudoClassStateChanged(
        javafx.css.PseudoClass.getPseudoClass("error"), e.type() == MessageType.ERROR);
    if (e.type() == MessageType.ERROR) {
      afterSave = null;
      publishAfterQuestion = null;
      status.setText(
          e.payload().path("message").asText("Không thể lưu") + " · Nội dung nhập vẫn được giữ.");
      return;
    }
    if (e.type() != MessageType.AUTHOR_RESULT) return;
    JsonNode d = e.payload().path("data");
    status.setText("Đã xử lý thành công");
    if (action.equals("LIST")) {
      if (d.path("quizzes").isEmpty())
        main.getChildren()
            .add(Ui.muted("Chưa có quiz nào. Tạo quiz đầu tiên để chia sẻ kiến thức của bạn."));
      for (JsonNode q : d.path("quizzes")) {
        main.getChildren()
            .add(
                new HBox(
                    10,
                    Ui.label(
                        q.path("title").asText()
                            + " · "
                            + q.path("questionCount").asInt()
                            + " câu · "
                            + (q.path("status").asText().equals("PUBLISHED")
                                ? "Đã xuất bản"
                                : "Bản nháp")),
                    Ui.button(
                        "Mở",
                        () -> {
                          if (busy) return;
                          editGeneration++;
                          quizId = q.path("quizId").asLong();
                          request("GET", data());
                        })));
      }
      main.getChildren()
          .add(
              new HBox(
                  10,
                  Ui.button(
                      "← Trước",
                      () -> {
                        page = Math.max(1, page - 1);
                        showList();
                      }),
                  Ui.label("Trang " + page),
                  Ui.button(
                      "Tiếp →",
                      () -> {
                        page++;
                        showList();
                      })));
      var pagination = (HBox) main.getChildren().getLast();
      ((Button) pagination.getChildren().get(0)).setDisable(page <= 1);
      ((Button) pagination.getChildren().get(2))
          .setDisable((long) page * d.path("pageSize").asInt(20) >= d.path("total").asLong());
      for (var node : main.getChildren())
        if (node instanceof HBox row) {
          row.getStyleClass().add("data-row");
          if (!row.getChildren().isEmpty() && row.getChildren().getFirst() instanceof Label label) {
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
          }
        }
    } else if (action.equals("GET_QUESTION")) {
      index = d.path("index").asInt();
      fillQuestion(d.path("question"));
    } else {
      quizId = d.path("quizId").asLong(quizId);
      count = d.path("questionCount").asInt(count);
      if (action.equals("GET") || action.equals("CREATE")) {
        title.setText(d.path("title").asText());
        description.setText(d.path("description").asText());
        shuffle.setSelected(d.path("shuffleQuestions").asBoolean());
        for (int i = 0; i < categories.size(); i++)
          if (categories.get(i).path("categoryId").asLong() == d.path("categoryId").asLong())
            category.getSelectionModel().select(i);
        formLoaded = false;
        editor();
      }
      if (d.has("questions")) refreshQuestions(d.path("questions"));
      if (action.equals("SAVE_QUESTION")) {
        String next = afterSave;
        afterSave = null;
        if ("SAVE_META".equals(next)) saveMeta();
      } else if (action.equals("SAVE_META") && "PUBLISH".equals(afterSave)) {
        afterSave = null;
        request("PUBLISH", data());
      }
      if (action.equals("DELETE_QUESTION") || action.equals("MOVE_QUESTION")) {
        formLoaded = false;
        form.setVisible(false);
        form.setManaged(false);
        editGeneration++;
      }
      if (action.equals("PUBLISH")) status.setText("Đã xuất bản · Cộng đồng · Không tính hạng");
      if (action.equals("UNPUBLISH")) status.setText("Đã gỡ khỏi sảnh. Trận đã tạo vẫn tiếp tục.");
    }
  }

  public void timeout() {
    pending.clear();
    busy = false;
    setLoadingFields(false);
    afterSave = null;
    publishAfterQuestion = null;
    status.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"), true);
    status.setText("Yêu cầu hết thời gian chờ. Nội dung vẫn được giữ; hãy thử lại.");
  }

  private void setLoadingFields(boolean loading) {
    title.setDisable(loading);
    description.setDisable(loading);
    category.setDisable(loading);
    shuffle.setDisable(loading);
    form.setDisable(loading);
  }

  private void editor() {
    form.setVisible(formLoaded);
    form.setManaged(formLoaded);
    main.getChildren()
        .setAll(
            Ui.title("Soạn quiz cộng đồng"),
            status,
            Ui.label("Tên quiz · tối đa 128 ký tự"),
            title,
            Ui.label("Mô tả · tối đa 1000 ký tự"),
            description,
            Ui.label("Danh mục"),
            category,
            shuffle,
            new FlowPane(
                10,
                10,
                Ui.secondary("Lưu nháp", () -> saveAll(false)),
                Ui.button("Xuất bản", () -> saveAll(true)),
                Ui.secondary("Gỡ khỏi sảnh", () -> request("UNPUBLISH", data())),
                Ui.secondary("Danh sách", this::showList)),
            Ui.label("1–50 câu khi xuất bản · 15 giây/câu · Không tính hạng"),
            questionList,
            Ui.button(
                "Thêm câu",
                () -> {
                  if (busy) return;
                  if (count >= 50) {
                    status.setText("Tối đa 50 câu");
                    return;
                  }
                  index = count;
                  fillQuestion(
                      data()
                          .put("questionType", "SINGLE_CHOICE")
                          .put("content", "")
                          .put("explanation", "")
                          .set("options", JSON.createArrayNode()));
                }),
            form);
    var metadata = new VBox(14);
    var all = new ArrayList<javafx.scene.Node>(main.getChildren());
    // Preserve the first heading/status, move metadata into its own collapsible section.
    for (int n = 2; n <= 8; n++) metadata.getChildren().add(all.get(n));
    var information = new TitledPane("Thông tin bộ quiz", metadata);
    information.setExpanded(!formLoaded);
    main.getChildren().setAll(all.get(0), all.get(1), information);
    for (int n = 9; n < all.size(); n++) main.getChildren().add(all.get(n));
  }

  private void refreshQuestions(JsonNode rows) {
    questionList.getChildren().clear();
    if (rows.isEmpty())
      questionList
          .getChildren()
          .add(Ui.muted("Bộ quiz chưa có câu hỏi. Nhấn Thêm câu để bắt đầu."));
    for (JsonNode q : rows) {
      int n = q.path("index").asInt();
      questionList
          .getChildren()
          .add(
              new HBox(
                  8,
                  Ui.label((n + 1) + ". " + q.path("content").asText()),
                  Ui.button("Sửa", () -> request("GET_QUESTION", data().put("index", n))),
                  Ui.button(
                      "↑",
                      () -> {
                        if (n > 0)
                          request("MOVE_QUESTION", data().put("index", n).put("toIndex", n - 1));
                      }),
                  Ui.button(
                      "↓",
                      () -> {
                        if (n + 1 < count)
                          request("MOVE_QUESTION", data().put("index", n).put("toIndex", n + 1));
                      }),
                  Ui.button("Xóa", () -> request("DELETE_QUESTION", data().put("index", n)))));
    }
  }

  private void buildAnswerEditor() {
    answerEditor.getChildren().clear();
    String kind = type.getValue();
    if (kind.endsWith("CHOICE")) {
      answerEditor
          .getChildren()
          .addAll(
              Ui.label("Lựa chọn và đáp án đúng"),
              Ui.muted(
                  kind.equals("SINGLE_CHOICE")
                      ? "2–6 lựa chọn · Nội dung ≤120 ký tự · Mã ≤64 ký tự · Chọn một đáp án đúng"
                      : "2–6 lựa chọn · Nội dung ≤120 ký tự · Mã ≤64 ký tự · Chọn tất cả đáp án"
                          + " đúng"));
      var rows = new VBox(8);
      var ids = new ArrayList<TextField>();
      var texts = new ArrayList<TextField>();
      var checks = new ArrayList<CheckBox>();
      var lines = optionDraft.deepCopy();
      if (lines.isEmpty())
        for (String id : List.of("A", "B", "C", "D"))
          lines.add(data().put("id", id).put("text", ""));
      var correct =
          new HashSet<>(
              choiceAnswer == null ? Arrays.asList(answer.getText().split(",")) : choiceAnswer);
      Runnable sync =
          () -> {
            var values = JSON.createArrayNode();
            var keys = new ArrayList<String>();
            for (int i = 0; i < ids.size(); i++) {
              values.add(
                  data().put("id", ids.get(i).getText()).put("text", texts.get(i).getText()));
              if (checks.get(i).isSelected()) keys.add(ids.get(i).getText());
            }
            optionDraft = values;
            choiceAnswer = keys;
            answer.setText(String.join(",", keys));
          };
      for (JsonNode line : lines) {
        String key = line.path("id").asText();
        var id = new TextField(key);
        id.setPrefWidth(65);
        id.setMinWidth(65);
        id.setMaxWidth(65);
        id.setAccessibleText("Mã lựa chọn");
        var text = new TextField(line.path("text").asText());
        text.setPromptText("Nội dung lựa chọn · tối đa 120 ký tự");
        HBox.setHgrow(text, Priority.ALWAYS);
        var check = new CheckBox("Đúng");
        check.setSelected(correct.contains(key));
        check.setMinWidth(70);
        ids.add(id);
        texts.add(text);
        checks.add(check);
        id.textProperty().addListener((o, old, value) -> sync.run());
        text.textProperty().addListener((o, old, value) -> sync.run());
        check.setOnAction(
            e -> {
              if (kind.equals("SINGLE_CHOICE") && check.isSelected())
                for (var c : checks) if (c != check) c.setSelected(false);
              sync.run();
            });
        var remove =
            Ui.secondary(
                "×",
                () -> {
                  int n = ids.indexOf(id);
                  ids.remove(n);
                  texts.remove(n);
                  checks.remove(n);
                  sync.run();
                  buildAnswerEditor();
                });
        remove.setAccessibleText("Xóa lựa chọn " + key);
        remove.setDisable(lines.size() <= 2);
        rows.getChildren().add(Ui.actions(id, text, check, remove));
      }
      var add =
          Ui.secondary(
              "+ Thêm lựa chọn",
              () -> {
                sync.run();
                var used = new HashSet<String>();
                ids.forEach(v -> used.add(v.getText()));
                String next =
                    java.util.stream.IntStream.range(0, 26)
                        .mapToObj(i -> "" + (char) ('A' + i))
                        .filter(v -> !used.contains(v))
                        .findFirst()
                        .orElse("G");
                optionDraft.add(data().put("id", next).put("text", ""));
                buildAnswerEditor();
              });
      add.setDisable(lines.size() >= 6);
      answerEditor.getChildren().addAll(rows, add);
    } else if (kind.equals("TRUE_FALSE")) {
      var value = new ComboBox<String>();
      value.getItems().addAll("Chưa chọn", "Đúng", "Sai");
      value.setValue(
          answer.getText().equals("true")
              ? "Đúng"
              : answer.getText().equals("false") ? "Sai" : "Chưa chọn");
      value.setOnAction(
          e ->
              answer.setText(
                  value.getValue().equals("Đúng")
                      ? "true"
                      : value.getValue().equals("Sai") ? "false" : ""));
      answerEditor.getChildren().add(Ui.field("Đáp án đúng", value));
    } else {
      answer.setPromptText("Ví dụ: Hà Nội\nHa Noi");
      answerEditor
          .getChildren()
          .addAll(
              Ui.field("Các đáp án được chấp nhận", answer),
              Ui.muted("Mỗi dòng một đáp án · tối đa 20 dòng, 120 ký tự mỗi dòng"));
    }
  }

  private void fillQuestion(JsonNode q) {
    editGeneration++;
    formLoaded = true;
    for (var node : main.getChildren())
      if (node instanceof TitledPane pane) pane.setExpanded(false);
    form.setVisible(true);
    form.setManaged(true);
    type.setValue(q.path("questionType").asText("SINGLE_CHOICE"));
    content.setText(q.path("content").asText());
    explanation.setText(q.path("explanation").asText());
    List<String> lines = new ArrayList<>();
    optionDraft =
        q.path("options").isArray()
            ? (ArrayNode) q.path("options").deepCopy()
            : JSON.createArrayNode();
    JsonNode key = q.path("answerKey");
    if (key.has("value")) key = key.path("value");
    if (key.has("acceptedAnswers")) key = key.path("acceptedAnswers");
    if (key.isArray()) {
      lines.clear();
      key.forEach(v -> lines.add(v.asText()));
      answer.setText(String.join(type.getValue().equals("SHORT_ANSWER") ? "\n" : ",", lines));
    } else answer.setText(key.isMissingNode() || key.isNull() ? "" : key.asText());
    choiceAnswer = null;
    if (type.getValue().endsWith("CHOICE")) {
      choiceAnswer = new ArrayList<>();
      if (key.isArray()) key.forEach(v -> choiceAnswer.add(v.asText()));
      else if (!key.isNull() && !key.isMissingNode() && !key.asText().isEmpty())
        choiceAnswer.add(key.asText());
    }
    questionAsset = q.hasNonNull("questionAssetId") ? q.path("questionAssetId").asText() : null;
    explanationAsset =
        q.hasNonNull("explanationAssetId") ? q.path("explanationAssetId").asText() : null;
    buildAnswerEditor();
    renderImage(false);
    renderImage(true);
  }

  ObjectNode question() {
    ObjectNode q =
        data()
            .put("questionType", type.getValue())
            .put("content", content.getText())
            .put("explanation", explanation.getText());
    q.set(
        "options",
        type.getValue().endsWith("CHOICE") ? optionDraft.deepCopy() : JSON.createArrayNode());
    String text = answer.getText().trim();
    if (type.getValue().endsWith("CHOICE") && choiceAnswer != null) {
      if (type.getValue().equals("MULTIPLE_CHOICE"))
        q.set("answerKey", JSON.valueToTree(choiceAnswer));
      else q.put("answerKey", choiceAnswer.isEmpty() ? "" : choiceAnswer.getFirst());
    } else if (type.getValue().equals("TRUE_FALSE")) {
      if (!text.isEmpty() && !text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
        throw new IllegalArgumentException("Đáp án đúng/sai phải là true hoặc false");
      q.set(
          "answerKey",
          text.isEmpty() ? NullNode.instance : BooleanNode.valueOf(Boolean.parseBoolean(text)));
    } else if (type.getValue().equals("MULTIPLE_CHOICE")
        || type.getValue().equals("SHORT_ANSWER")) {
      ArrayNode a = JSON.createArrayNode();
      for (String v : text.split(type.getValue().equals("SHORT_ANSWER") ? "\\R" : ","))
        if (!v.isBlank()) a.add(v.trim());
      q.set("answerKey", a);
    } else q.put("answerKey", text);
    q.put("questionAssetId", questionAsset);
    q.put("explanationAssetId", explanationAsset);
    return q;
  }

  private void saveQuestion(String next) {
    if (busy) {
      status.setText("Hãy chờ thao tác hiện tại hoàn tất.");
      return;
    }
    try {
      ObjectNode q = question();
      afterSave = next;
      request("SAVE_QUESTION", data().put("index", index).set("question", q));
    } catch (Exception ex) {
      status.setText(ex.getMessage());
    }
  }

  private void saveAll(boolean publish) {
    if (busy) {
      status.setText("Hãy chờ thao tác hiện tại hoàn tất.");
      return;
    }
    afterSave = publish ? "PUBLISH" : null;
    if (formLoaded) {
      String finalAction = afterSave;
      saveQuestion("SAVE_META");
      publishAfterQuestion = finalAction;
    } else saveMeta();
  }

  private String publishAfterQuestion;

  private void saveMeta() {
    if (publishAfterQuestion != null) {
      afterSave = publishAfterQuestion;
      publishAfterQuestion = null;
    }
    int c = category.getSelectionModel().getSelectedIndex();
    request(
        "SAVE_META",
        data()
            .put("title", title.getText())
            .put("description", description.getText())
            .put("shuffleQuestions", shuffle.isSelected())
            .put("categoryId", c < 0 ? 0 : categories.get(c).path("categoryId").asLong()));
  }

  private HBox imageControls(boolean reveal) {
    return new HBox(
        10,
        Ui.label(reveal ? "Ảnh giải thích" : "Ảnh câu hỏi"),
        Ui.button(
            "Chọn PNG/JPEG ≤5 MiB",
            () -> {
              FileChooser chooser = new FileChooser();
              chooser
                  .getExtensionFilters()
                  .add(new FileChooser.ExtensionFilter("PNG/JPEG", "*.png", "*.jpg", "*.jpeg"));
              var file = chooser.showOpenDialog(getScene().getWindow());
              if (file == null) return;
              long generation = editGeneration;
              long imageGeneration =
                  reveal ? ++explanationImageGeneration : ++questionImageGeneration;
              status.setText("Đang tải ảnh…");
              RemoteMedia.upload(file.toPath())
                  .whenComplete(
                      (id, error) ->
                          Platform.runLater(
                              () -> {
                                if (generation != editGeneration
                                    || imageGeneration
                                        != (reveal
                                            ? explanationImageGeneration
                                            : questionImageGeneration)) return;
                                if (error != null) {
                                  status.setText(
                                      "Tải ảnh thất bại: "
                                          + error.getMessage()
                                          + " · Nội dung vẫn được giữ.");
                                  return;
                                }
                                if (reveal) explanationAsset = id;
                                else questionAsset = id;
                                renderImage(reveal);
                                status.setText("Ảnh đã tải. Lưu câu để gắn ảnh.");
                              }));
            }),
        Ui.button(
            "Bỏ ảnh",
            () -> {
              if (reveal) {
                explanationAsset = null;
                explanationImageGeneration++;
              } else {
                questionAsset = null;
                questionImageGeneration++;
              }
              renderImage(reveal);
            }));
  }

  private void renderImage(boolean reveal) {
    VBox box = reveal ? explanationImage : questionImage;
    box.getChildren().clear();
    String id = reveal ? explanationAsset : questionAsset;
    if (id != null) box.getChildren().add(new AssetLoader().view(id, 180));
  }

  private void preview(boolean reveal) {
    try {
      ObjectNode q = question();
      q.put("questionId", 0);
      ObjectNode publicQ = q.deepCopy();
      publicQ.remove(List.of("answerKey", "explanation", "explanationAssetId"));
      publicQ.put("timeLimitMs", 15000);
      QuestionRenderer renderer =
          switch (type.getValue()) {
            case "MULTIPLE_CHOICE" -> new MultipleChoiceRenderer();
            case "TRUE_FALSE" -> new TrueFalseRenderer();
            case "SHORT_ANSWER" -> new ShortAnswerRenderer();
            default -> new SingleChoiceRenderer();
          };
      renderer.bind(JSON.treeToValue(publicQ, Payloads.PublicQuestion.class), a -> {});
      renderer.setControlsEnabled(!reveal);
      if (reveal)
        renderer.showResult(
            new Payloads.QuestionResult(
                0,
                q.path("answerKey"),
                explanation.getText(),
                explanationAsset,
                List.of(),
                0,
                0,
                0),
            0);
      VBox body = new VBox(12, Ui.title(content.getText()));
      if (questionAsset != null) body.getChildren().add(new AssetLoader().view(questionAsset, 180));
      body.getChildren().add(renderer.view());
      if (reveal) {
        body.getChildren()
            .addAll(
                Ui.label("Đáp án: " + PresentationText.answer(publicQ, q.path("answerKey"))),
                Ui.label(explanation.getText()));
        if (explanationAsset != null)
          body.getChildren().add(new AssetLoader().view(explanationAsset, 180));
      }
      Dialog<Void> dialog = new Dialog<>();
      dialog.setTitle(reveal ? "Xem trước công bố đáp án" : "Xem trước câu hỏi");
      dialog.getDialogPane().setContent(Ui.scroll(body));
      dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
      dialog.getDialogPane().setPrefSize(700, 600);
      dialog.initOwner(getScene().getWindow());
      dialog
          .getDialogPane()
          .getStylesheets()
          .add(getClass().getResource("/ui/quiz-arena.css").toExternalForm());
      body.getStyleClass().add("question-panel");
      dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setAccessibleText("Đóng xem trước");
      ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
      dialog.show();
    } catch (Exception e) {
      status.setText("Không thể xem trước: " + e.getMessage());
    }
  }
}
