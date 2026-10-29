package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.BooleanSupplier;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;
import vn.edu.nhom7.quiz.client.state.ClientState;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Lifeline controls, request state and feedback; positioned by the enclosing game view. */
final class LifelineDock extends HBox {
  private final BooleanSupplier reducedMotion;
  private final List<Animation> animations;
  private final Runnable requestOverlayLayout;
  private final Map<String, Button> lifelineButtons = new LinkedHashMap<>();
  private final VBox popover = new VBox(10);
  private final Polygon tail = new Polygon(0, 0, 18, 0, 9, 10);
  private final Label popIcon = Ui.label(""), popName = Ui.label(""), popKey = Ui.label("");
  private final Label popText = Ui.label(""), popStatus = Ui.label("");
  private final Button popUse = new Button("Sử dụng");
  private String openKind;
  private final Label selfDouble = Ui.label("x2");
  private final Label opponentLifeline = Ui.label("");
  private final UiCommandSink sink;
  private ClientState current;
  private long questionId = -1;
  private boolean lifelineFits, lifelinePending, doubled;
  private QuestionRenderer renderer;
  private long selfId, opponentId;
  private String errorAtSend = "";

  /** Lifeline uses already announced with sound and motion, as round id plus user id. */
  static final Set<String> announcedLifelines = new HashSet<>();

  static final Map<String, String[]> LIFELINE_TEXT =
      Map.of(
          "DOUBLE_POINTS",
          new String[] {
            "×2",
            "x2 điểm",
            "Nhân đôi điểm của câu này: trả lời đúng được 6, 4 hoặc 2 điểm. Trả lời sai vẫn 0"
                + " điểm.",
            "X"
          },
          "REMOVE_ONE",
          new String[] {
            "−1", "Loại 1 đáp án", "Ẩn ngẫu nhiên một phương án sai, giúp bạn chọn dễ hơn.", "L"
          },
          "FIFTY_FIFTY",
          new String[] {"½", "50-50", "Chỉ giữ lại đáp án đúng và một phương án sai.", "N"});

  LifelineDock(
      UiCommandSink sink,
      ClientState initial,
      BooleanSupplier reducedMotion,
      List<Animation> animations,
      Runnable requestOverlayLayout) {
    super(12);
    this.sink = sink;
    this.current = initial;
    this.reducedMotion = reducedMotion;
    this.animations = animations;
    this.requestOverlayLayout = requestOverlayLayout;
  }

  void bind(JsonNode question, QuestionRenderer renderer, long selfId, long opponentId) {
    this.renderer = renderer;
    this.selfId = selfId;
    this.opponentId = opponentId;
    questionId = question.path("questionId").asLong(-1);
    lifelineFits =
        question.path("questionType").asText().equals("SINGLE_CHOICE")
            && question.path("options").size() >= 3;
    buildLifelines();
  }

  List<Node> overlays() {
    return hasQuestion() ? List.of(tail, popover) : List.of();
  }

  boolean hasQuestion() {
    return !lifelineButtons.isEmpty();
  }

  boolean doubled() {
    return doubled;
  }

  Label selfIndicator() {
    return selfDouble;
  }

  Label opponentIndicator() {
    return opponentLifeline;
  }

  void layoutOverlay(Region host) {
    if (openKind != null && getScene() != null) {
      // The card floats above the open icon, right-aligned with the dock.
      var icon = lifelineButtons.get(openKind);
      var iconBox = host.sceneToLocal(icon.localToScene(icon.getBoundsInLocal()));
      var dockBox = host.sceneToLocal(localToScene(getBoundsInLocal()));
      double pw = popover.prefWidth(-1), ph = popover.prefHeight(pw);
      double right = Math.min(host.getWidth() - 16, dockBox.getMaxX());
      double top = iconBox.getMinY() - 14 - ph;
      popover.resizeRelocate(right - pw, top, pw, ph);
      tail.relocate(iconBox.getCenterX() - 9, top + ph - 1);
    }
  }

  boolean handleKey(javafx.scene.input.KeyEvent event) {
    if (openKind != null && event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
      closeLifeline();
      return true;
    }
    if (openKind != null && event.getCode() == javafx.scene.input.KeyCode.ENTER) {
      if (!popUse.isDisabled()) popUse.fire();
      return true;
    }
    String key = event.getCode().getName();
    if (key.length() != 1) key = event.getText();
    if (key == null || key.length() != 1) return false;
    char letter = Character.toUpperCase(key.charAt(0));
    for (var entry : LIFELINE_TEXT.entrySet()) {
      if (letter == entry.getValue()[3].charAt(0) && lifelineButtons.containsKey(entry.getKey())) {
        lifelineButtons.get(entry.getKey()).fire();
        return true;
      }
    }
    return false;
  }

  /**
   * Lifeline icons in the bottom-right corner. Clicking one (or its key) opens a small card above
   * it with what the lifeline does and a "Sử dụng" button.
   */
  private void buildLifelines() {
    getStyleClass().add("lifeline-dock");
    setAlignment(Pos.CENTER_RIGHT);
    for (String kind : Payloads.LIFELINES) {
      String[] text = LIFELINE_TEXT.get(kind);
      var glyph = Ui.label(text[0]);
      glyph.getStyleClass().add("lifeline-glyph");
      var cap = Ui.label(text[3]);
      cap.getStyleClass().add("lifeline-key");
      StackPane.setAlignment(cap, Pos.BOTTOM_RIGHT);
      cap.setTranslateX(6);
      cap.setTranslateY(6);
      var face = new StackPane(glyph, cap);
      face.setMinSize(56, 56);
      face.setMaxSize(56, 56);
      var b = new Button();
      b.setGraphic(face);
      b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      b.setAccessibleText(text[1] + ". Phím " + text[3]);
      b.getStyleClass().addAll("lifeline", "lifeline-" + kind.toLowerCase(Locale.ROOT));
      b.setTooltip(new Tooltip(text[1] + " · phím " + text[3]));
      b.setOnAction(e -> toggleLifeline(kind));
      lifelineButtons.put(kind, b);
      getChildren().add(b);
    }
    popIcon.getStyleClass().add("lifeline-pop-icon");
    popIcon.setMinSize(40, 40);
    popIcon.setAlignment(Pos.CENTER);
    popName.getStyleClass().add("lifeline-pop-name");
    popKey.getStyleClass().add("lifeline-pop-key");
    var close = new Button("✕");
    close.getStyleClass().add("lifeline-pop-close");
    close.setAccessibleText("Đóng");
    close.setOnAction(e -> closeLifeline());
    var header = new HBox(10, popIcon, popName, Ui.spacer(), popKey, close);
    header.setAlignment(Pos.CENTER_LEFT);
    popText.getStyleClass().add("lifeline-pop-text");
    popStatus.getStyleClass().add("lifeline-pop-status");
    var rule = Ui.label("Mỗi quyền dùng 1 lần mỗi trận, tối đa 1 quyền mỗi câu.");
    rule.getStyleClass().add("lifeline-pop-rule");
    popUse.getStyleClass().add("lifeline-use");
    popUse.setMaxWidth(Double.MAX_VALUE);
    popUse.setOnAction(e -> useLifeline(openKind));
    popover.getChildren().addAll(header, popText, popStatus, popUse, rule);
    popover.getStyleClass().add("lifeline-pop");
    popover.setPrefWidth(330);
    popover.setFillWidth(true);
    tail.getStyleClass().add("lifeline-pop-tail");
    for (Node n : List.of(tail, popover)) {
      n.setManaged(false);
    }
    popover.setVisible(false);
    tail.setVisible(false);
  }

  private void toggleLifeline(String kind) {
    if (kind.equals(openKind)) closeLifeline();
    else openLifeline(kind);
  }

  void openLifeline(String kind) {
    if (!lifelineButtons.containsKey(kind)) return;
    openKind = kind;
    String[] text = LIFELINE_TEXT.get(kind);
    popIcon.setText(text[0]);
    popIcon
        .getStyleClass()
        .removeIf(c -> c.startsWith("lifeline-") && !c.equals("lifeline-pop-icon"));
    popIcon.getStyleClass().add("lifeline-" + kind.toLowerCase(Locale.ROOT));
    popName.setText(text[1]);
    popKey.setText("Phím " + text[3]);
    popText.setText(text[2]);
    lifelineButtons.forEach((k, b) -> b.getStyleClass().remove("open"));
    lifelineButtons.get(kind).getStyleClass().add("open");
    popover.setVisible(true);
    tail.setVisible(true);
    render(current);
    requestOverlayLayout.run();
    if (!reducedMotion.getAsBoolean()) {
      popover.setScaleX(0.85);
      popover.setScaleY(0.85);
      popover.setOpacity(0);
      var grow = new ScaleTransition(Duration.millis(180), popover);
      grow.setToX(1);
      grow.setToY(1);
      grow.setInterpolator(Effects.BACK_OUT);
      var fade = new FadeTransition(Duration.millis(140), popover);
      fade.setToValue(1);
      Effects.run(new ParallelTransition(grow, fade), animations, Duration.millis(180));
    }
  }

  private void closeLifeline() {
    openKind = null;
    lifelineButtons.forEach((k, b) -> b.getStyleClass().remove("open"));
    popover.setVisible(false);
    tail.setVisible(false);
  }

  private void useLifeline(String kind) {
    if (kind == null || !current.canAnswer() || lifelinePending || questionId < 0) return;
    lifelinePending = true;
    errorAtSend = current.error();
    try {
      sink.send(
          MessageType.LIFELINE_USE,
          current.activeMatchId(),
          current.roundId(),
          new Payloads.LifelineUse(questionId, kind));
      closeLifeline();
    } catch (RuntimeException failure) {
      lifelinePending = false;
    }
    render(current);
  }

  static List<String> texts(JsonNode array) {
    var values = new ArrayList<String>();
    array.forEach(n -> values.add(n.asText()));
    return values;
  }

  /** Button states, removed options, the x2 chip and the opponent's lifeline notice. */
  void render(ClientState s) {
    current = s;
    JsonNode result = s.payload(MessageType.LIFELINE_RESULT);
    boolean thisQuestion = result != null && result.path("questionId").asLong() == questionId;
    String active = thisQuestion ? result.path("kind").asText("") : "";
    if (thisQuestion || (!s.error().isBlank() && !s.error().equals(errorAtSend)))
      lifelinePending = false;
    List<String> remaining = result == null ? Payloads.LIFELINES : texts(result.path("remaining"));
    for (var entry : lifelineButtons.entrySet()) {
      String kind = entry.getKey();
      Button b = entry.getValue();
      boolean used = !remaining.contains(kind), isActive = kind.equals(active);
      boolean fits = kind.equals("DOUBLE_POINTS") || lifelineFits;
      b.getStyleClass().removeAll("used", "active", "unavailable");
      if (isActive) b.getStyleClass().add("active");
      else if (used) b.getStyleClass().add("used");
      else if (!fits) b.getStyleClass().add("unavailable");
      if (!kind.equals(openKind)) continue;
      String why =
          isActive
              ? kind.equals("DOUBLE_POINTS")
                  ? s.phase().equals("OPEN") ? "Đang nhân đôi câu này" : "Đã nhân đôi câu này"
                  : "Đã loại phương án ở câu này"
              : used
                  ? "Đã dùng trong trận này"
                  : !fits
                      ? "Chỉ dùng cho câu một đáp án có từ 3 phương án"
                      : !active.isEmpty()
                          ? "Câu này đã dùng một quyền trợ giúp"
                          : s.accepted() || s.ownPending()
                              ? "Bạn đã trả lời câu này"
                              : !s.phase().equals("OPEN")
                                  ? "Dùng được khi câu hỏi bắt đầu tính giờ"
                                  : lifelinePending ? "Đang gửi…" : "Còn 1 lần trong trận";
      boolean usable = s.canAnswer() && !used && fits && active.isEmpty() && !lifelinePending;
      popStatus.setText(why);
      popStatus.getStyleClass().removeAll("ok", "blocked");
      popStatus.getStyleClass().add(usable || isActive ? "ok" : "blocked");
      popUse.setDisable(!usable);
      popUse.setText(usable ? "Sử dụng · Enter" : isActive ? "Đang dùng" : "Sử dụng");
    }
    doubled = active.equals("DOUBLE_POINTS");
    selfDouble.setVisible(doubled);
    String key = s.roundId() + ":" + selfId;
    boolean fresh = !active.isEmpty() && announcedLifelines.add(key);
    if (fresh) Sound.play(Sound.Effect.LIFELINE);
    if (doubled && fresh && !reducedMotion.getAsBoolean()) pop(selfDouble);
    if (thisQuestion && renderer instanceof BaseQuestionRenderer base) {
      var removed = base.eliminate(texts(result.path("removedOptionIds")));
      if (fresh && !reducedMotion.getAsBoolean()) removed.forEach(this::crumble);
    }
    var status = s.payload(MessageType.LIFELINE_STATUS);
    var theirs = status == null ? null : status.path("byUser").get(Long.toString(opponentId));
    boolean shown = theirs != null && theirs.path("questionId").asLong() == questionId;
    opponentLifeline.setText(
        shown ? "dùng " + LIFELINE_TEXT.get(theirs.path("kind").asText("DOUBLE_POINTS"))[1] : "");
    boolean wasShown = opponentLifeline.isVisible();
    opponentLifeline.setVisible(shown);
    if (shown
        && !wasShown
        && announcedLifelines.add(s.roundId() + ":" + opponentId)
        && !reducedMotion.getAsBoolean()) pop(opponentLifeline);
  }

  private void pop(Node node) {
    var pop = new ScaleTransition(Duration.millis(380), node);
    pop.setFromX(0.2);
    pop.setFromY(0.2);
    pop.setToX(1);
    pop.setToY(1);
    pop.setInterpolator(Effects.BACK_OUT);
    Effects.run(pop, animations, Duration.millis(380));
  }

  /** A removed option shudders, shrinks a little and fades back. */
  private void crumble(Node tile) {
    var shake = new TranslateTransition(Duration.millis(60), tile);
    shake.setFromX(0);
    shake.setByX(7);
    shake.setAutoReverse(true);
    shake.setCycleCount(4);
    var shrink = new ScaleTransition(Duration.millis(280), tile);
    shrink.setFromX(1);
    shrink.setFromY(1);
    shrink.setToX(0.96);
    shrink.setToY(0.96);
    var fade = new FadeTransition(Duration.millis(280), tile);
    fade.setFromValue(1);
    fade.setToValue(0.45);
    var all = new SequentialTransition(shake, new ParallelTransition(shrink, fade));
    Effects.run(all, animations, Duration.millis(520));
  }
}
