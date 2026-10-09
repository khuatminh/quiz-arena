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
      options = new TextArea(),
      answer = new TextArea();
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
    options.setPromptText(
        "Mỗi dòng: ID|nội dung (ví dụ A|Hà Nội) · 2–6 lựa chọn · nội dung ≤120 ký tự · ID ≤64 ký"
            + " tự");
    options.setPrefRowCount(4);
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
            Ui.label(
                "Lựa chọn cho câu một/nhiều đáp án · 2–6 dòng ID|nội dung · nội dung ≤120 ký tự ·"
                    + " ID ≤64 ký tự"),
            options,
            Ui.label(
                "Đáp án · một: A · nhiều: A,B · đúng/sai: true hoặc false · ngắn: tối đa 20 dòng,"
                    + " mỗi dòng ≤120 ký tự"),
            answer,
            Ui.label("Giải thích · tối đa 500 ký tự"),
            explanation,
            imageControls(false),
            questionImage,
            imageControls(true),
            explanationImage,
            new HBox(
                10,
                Ui.button("Lưu câu", () -> saveQuestion(null)),
                Ui.button("Xem trước câu", () -> preview(false)),
                Ui.button("Xem công bố đáp án", () -> preview(true))));
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
                            + q.path("status").asText()),
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
            new HBox(
                10,
                Ui.button("Lưu nháp", () -> saveAll(false)),
                Ui.button("Xuất bản", () -> saveAll(true)),
                Ui.button("Gỡ khỏi sảnh", () -> request("UNPUBLISH", data())),
                Ui.button("Danh sách", this::showList)),
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
  }

  private void refreshQuestions(JsonNode rows) {
    questionList.getChildren().clear();
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

  private void fillQuestion(JsonNode q) {
    editGeneration++;
    formLoaded = true;
    form.setVisible(true);
    form.setManaged(true);
    type.setValue(q.path("questionType").asText("SINGLE_CHOICE"));
    content.setText(q.path("content").asText());
    explanation.setText(q.path("explanation").asText());
    List<String> lines = new ArrayList<>();
    for (JsonNode o : q.path("options"))
      lines.add(o.path("id").asText() + "|" + o.path("text").asText());
    options.setText(String.join("\n", lines));
    JsonNode key = q.path("answerKey");
    if (key.has("value")) key = key.path("value");
    if (key.has("acceptedAnswers")) key = key.path("acceptedAnswers");
    if (key.isArray()) {
      lines.clear();
      key.forEach(v -> lines.add(v.asText()));
      answer.setText(String.join(type.getValue().equals("SHORT_ANSWER") ? "\n" : ",", lines));
    } else answer.setText(key.isMissingNode() || key.isNull() ? "" : key.asText());
    questionAsset = q.hasNonNull("questionAssetId") ? q.path("questionAssetId").asText() : null;
    explanationAsset =
        q.hasNonNull("explanationAssetId") ? q.path("explanationAssetId").asText() : null;
    renderImage(false);
    renderImage(true);
  }

  ObjectNode question() {
    ObjectNode q =
        data()
            .put("questionType", type.getValue())
            .put("content", content.getText())
            .put("explanation", explanation.getText());
    ArrayNode opts = JSON.createArrayNode();
    if (type.getValue().endsWith("CHOICE"))
      for (String line : options.getText().split("\\R")) {
        if (line.isBlank()) continue;
        String[] pair = line.split("\\|", 2);
        opts.add(data().put("id", pair[0].trim()).put("text", pair.length > 1 ? pair[1] : ""));
      }
    q.set("options", opts);
    String text = answer.getText().trim();
    if (type.getValue().equals("TRUE_FALSE")) {
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
      dialog.show();
    } catch (Exception e) {
      status.setText("Không thể xem trước: " + e.getMessage());
    }
  }
}
