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

  private enum SaveAction {
    SAVE_META,
    PUBLISH
  }

  private record PendingRequest(String action, long quizId, long generation) {}

  private final Map<UUID, PendingRequest> pending = new HashMap<>();
  private long quizId;
  private int count, index, page = 1;
  private String questionAsset, explanationAsset;
  private SaveAction afterSave, publishAfterQuestion;
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
      renderOwnedQuizzes(d);
    } else if (action.equals("GET_QUESTION")) {
      index = d.path("index").asInt();
      fillQuestion(d.path("question"));
    } else {
      applyQuizUpdate(action, d);
    }
  }

  private void renderOwnedQuizzes(JsonNode d) {
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
            Ui.pager(
                Ui.button(
                    "← Trước",
                    () -> {
                      page = Math.max(1, page - 1);
                      showList();
                    }),
                "Trang " + page,
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
      if (node instanceof HBox row && !row.getStyleClass().contains("pager")) {
        row.getStyleClass().add("data-row");
        if (!row.getChildren().isEmpty() && row.getChildren().getFirst() instanceof Label label) {
          label.setMaxWidth(Double.MAX_VALUE);
          HBox.setHgrow(label, Priority.ALWAYS);
        }
      }
  }

  private void applyQuizUpdate(String action, JsonNode d) {
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
      SaveAction next = afterSave;
      afterSave = null;
      if (next == SaveAction.SAVE_META) saveMeta();
    } else if (action.equals("SAVE_META") && afterSave == SaveAction.PUBLISH) {
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
      ChoiceAnswerEditor.render(
          answerEditor,
          kind,
          optionDraft,
          choiceAnswer == null ? Arrays.asList(answer.getText().split(",")) : choiceAnswer,
          (options, keys) -> {
            optionDraft = options;
            choiceAnswer = new ArrayList<>(keys);
            answer.setText(String.join(",", keys));
          });
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
    return DraftQuestionMapper.serialize(
        type.getValue(),
        content.getText(),
        explanation.getText(),
        optionDraft,
        choiceAnswer,
        answer.getText(),
        questionAsset,
        explanationAsset);
  }

  private void saveQuestion(SaveAction next) {
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
    afterSave = publish ? SaveAction.PUBLISH : null;
    if (formLoaded) {
      SaveAction finalAction = afterSave;
      saveQuestion(SaveAction.SAVE_META);
      publishAfterQuestion = finalAction;
    } else saveMeta();
  }

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
      QuestionPreview.show(question(), reveal, getScene().getWindow());
    } catch (Exception e) {
      status.setText("Không thể xem trước: " + e.getMessage());
    }
  }
}
