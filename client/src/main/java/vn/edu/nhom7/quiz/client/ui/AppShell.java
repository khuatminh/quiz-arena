package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.edu.nhom7.quiz.client.network.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Views send intents only. All authoritative transitions arrive through ClientStore. */
public final class AppShell extends BorderPane implements AutoCloseable {
  private final ClientStore store;
  private final NetworkClient network;
  private final UiCommandSink commands;
  private final AuthView auth;
  private final Label message = Ui.label("");
  private final Timeline clock;
  private final Set<UUID> readyRounds = new HashSet<>();
  private String visible = "", query = "";
  private boolean showChat;
  private long presentation = -1, onlineRevision = -1;
  private JsonNode lastQuiz, lastRanking, lastHistory, lastDetail, lastRematch, lastReview;
  private GameView game;
  private CommunityQuizView community;
  private ResultView result;
  private ChatPanel chat;
  private ChallengeDialog challenge;
  private LobbyView lobby;
  private Long selectedQuizCategory;
  private final boolean reducedMotionFlag = Boolean.getBoolean("quiz.reducedMotion");
  private final Effects.Layer fx = new Effects.Layer();
  private StackPane inviteHost;
  private SettingsOverlay settings;
  private StackPane offline;
  private String lastHost;
  private int lastPort;
  private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(5));
  private boolean settingsInMatch;
  private UUID celebratedMatch;

  public AppShell(ClientStore store, NetworkClient network, UiCommandSink sink) {
    this.store = store;
    this.network = network;
    this.commands =
        (type, match, round, payload) -> {
          try {
            if (type == MessageType.EXIT_MATCH && match == null) {
              store.leaveResult();
              return UUID.randomUUID();
            }
            UUID sent = sink.send(type, match, round, payload);
            if (sent != null
                && type == MessageType.QUIZ_LIST_REQUEST
                && payload instanceof Payloads.QuizListRequest listRequest)
              selectedQuizCategory = listRequest.categoryId();
            return sent;
          } catch (Exception e) {
            Platform.runLater(() -> message.setText(e.getMessage()));
            return null;
          }
        };
    vn.edu.nhom7.quiz.client.assets.RemoteMedia.configure(commands);
    auth = new AuthView(commands, this::connect);
    setCenter(auth);
    // Messages are toasts: they float above the bottom edge and clear themselves.
    message.getStyleClass().add("toast");
    message.setWrapText(false);
    message.setMaxWidth(Region.USE_PREF_SIZE);
    message.visibleProperty().bind(message.textProperty().isNotEmpty());
    message.managedProperty().bind(message.visibleProperty());
    var toastHost = new StackPane(message);
    toastHost.setPickOnBounds(false);
    toastHost.setMouseTransparent(true);
    StackPane.setAlignment(message, javafx.geometry.Pos.BOTTOM_CENTER);
    StackPane.setMargin(message, new javafx.geometry.Insets(0, 24, 64, 24));
    toastTimer.setOnFinished(e -> message.setText(""));
    message
        .textProperty()
        .addListener(
            (o, before, text) -> {
              if (text.isEmpty()) return;
              // Evidence renders keep the toast up until the snapshot.
              if (Effects.preview) return;
              toastTimer.playFromStart();
              if (reducedMotion()) return;
              var in = new FadeTransition(Duration.millis(160), message);
              in.setFromValue(0);
              in.setToValue(1);
              in.play();
            });
    fx.getChildren().add(toastHost);
    widthProperty().addListener((o, b, a) -> updateChatPlacement());
    clock =
        new Timeline(
            new KeyFrame(
                Duration.millis(100),
                e -> {
                  if (game != null) game.tick();
                  if (settings != null) settings.tick();
                  if (challenge != null)
                    challenge.tick(System.currentTimeMillis() + network.time.offsetMs());
                }));
    clock.setCycleCount(Animation.INDEFINITE);
    clock.play();
    getChildren().add(fx);
    // Esc opens the settings menu from anywhere; views that use Esc themselves consume it first.
    addEventHandler(
        javafx.scene.input.KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() != javafx.scene.input.KeyCode.ESCAPE) return;
          if (settings != null) closeSettings();
          else if (challenge == null || !challenge.incoming()) openSettings();
          else return;
          e.consume();
        });
  }

  /** Connects to {@code host}; the login screen and "Kết nối lại" both start here. */
  private void connect(String host, int port) {
    lastHost = host;
    lastPort = port;
    vn.edu.nhom7.quiz.client.assets.RemoteMedia.clear();
    community = null;
    lobby = null;
    store.resetConnection();
    message.setText("Đang kết nối…");
    network
        .connect(host, port)
        .whenComplete(
            (v, e) ->
                Platform.runLater(
                    () ->
                        message.setText(
                            e == null
                                ? "Đang xác nhận phiên bản…"
                                : "Không thể kết nối tới server")));
  }

  /**
   * Covers the screen when the server connection drops after login, so the player sees it at once
   * instead of a frozen screen, and offers to reconnect (which means signing in again).
   */
  private void showOffline(String reason) {
    hideOffline();
    boolean inMatch = inMatch(store.state());
    var card = new VBox(14);
    card.getStyleClass().add("offline-card");
    card.setMaxWidth(460);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    var kicker = Ui.kicker("MẤT KẾT NỐI");
    kicker.getStyleClass().add("offline-kicker");
    var retry =
        Ui.button(
            "Kết nối lại",
            () -> {
              hideOffline();
              if (lastHost != null) connect(lastHost, lastPort);
              else store.resetConnection();
            });
    retry.setMaxWidth(Double.MAX_VALUE);
    retry.setDefaultButton(true);
    var login =
        Ui.secondary(
            "Về màn đăng nhập",
            () -> {
              hideOffline();
              store.resetConnection();
            });
    login.setMaxWidth(Double.MAX_VALUE);
    card.getChildren()
        .addAll(
            kicker,
            Ui.title("Không còn kết nối tới server"),
            Ui.muted(
                switch (reason) {
                  case "Server response timed out" -> "Server không phản hồi kịp.";
                  case "Connection interrupted" -> "Kết nối bị gián đoạn.";
                  case "Server disconnected" -> "Server đã đóng kết nối.";
                  case "Connection or protocol error" -> "Lỗi kết nối hoặc giao thức.";
                  default -> reason;
                }),
            Ui.label(
                inMatch
                    ? "Trận đang chơi không tự nối lại được. Kết nối lại rồi đăng nhập để chơi tiếp"
                        + " các trận mới."
                    : "Kết nối lại rồi đăng nhập để tiếp tục."),
            retry,
            login);
    offline = new StackPane(card);
    offline.getStyleClass().add("settings-backdrop");
    offline.setPickOnBounds(true);
    fx.getChildren().add(offline);
    Platform.runLater(retry::requestFocus);
  }

  private void hideOffline() {
    if (offline != null) fx.getChildren().remove(offline);
    offline = null;
  }

  private boolean reducedMotion() {
    return reducedMotionFlag || Theme.reduceMotion();
  }

  private static boolean inMatch(ClientState s) {
    return s.activeMatchId() != null && !s.phase().equals("RESULT");
  }

  /** Shows the Esc menu over the current screen; during a match it can also forfeit. */
  public void openSettings() {
    if (settings != null) return;
    var s = store.state();
    settingsInMatch = inMatch(s);
    settings =
        new SettingsOverlay(
            () -> game == null ? -1 : game.secondsLeft(),
            this::closeSettings,
            settingsInMatch
                ? () -> {
                  closeSettings();
                  commands.send(
                      MessageType.EXIT_MATCH,
                      store.state().activeMatchId(),
                      null,
                      new Payloads.ExitMatch());
                }
                : null);
    fx.getChildren().add(settings);
    Platform.runLater(() -> settings.focus());
  }

  public void closeSettings() {
    if (settings == null) return;
    fx.getChildren().remove(settings);
    settings = null;
    if (getCenter() != null) getCenter().requestFocus();
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();
    fx.resizeRelocate(0, 0, getWidth(), getHeight());
    if (getChildren().getLast() != fx) fx.toFront();
  }

  /** An incoming challenge floats in over a dimmed screen; our own invite docks bottom-right. */
  private void showChallenge(ChallengeDialog dialog) {
    if (inviteHost != null) fx.getChildren().remove(inviteHost);
    boolean incoming = dialog.incoming();
    var host = new StackPane(dialog);
    inviteHost = host;
    host.getStyleClass().add(incoming ? "invite-backdrop" : "invite-dock");
    host.setPickOnBounds(incoming);
    StackPane.setAlignment(
        dialog, incoming ? javafx.geometry.Pos.CENTER : javafx.geometry.Pos.BOTTOM_RIGHT);
    if (!incoming) StackPane.setMargin(dialog, new javafx.geometry.Insets(0, 24, 56, 0));
    fx.getChildren().add(host);
    dialog.tick(System.currentTimeMillis() + network.time.offsetMs());
    Platform.runLater(dialog::focusAction);
    if (reducedMotion()) return;
    var in =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(host.opacityProperty(), 0),
                new KeyValue(dialog.translateYProperty(), incoming ? -90 : 0),
                new KeyValue(dialog.translateXProperty(), incoming ? 0 : 460),
                new KeyValue(dialog.scaleXProperty(), incoming ? 0.86 : 1),
                new KeyValue(dialog.scaleYProperty(), incoming ? 0.86 : 1)),
            new KeyFrame(Duration.millis(200), new KeyValue(host.opacityProperty(), 1)),
            new KeyFrame(
                Duration.millis(460),
                new KeyValue(dialog.translateYProperty(), 0, Effects.BACK_OUT),
                new KeyValue(dialog.translateXProperty(), 0, Effects.BACK_OUT),
                new KeyValue(dialog.scaleXProperty(), 1, Effects.BACK_OUT),
                new KeyValue(dialog.scaleYProperty(), 1, Effects.BACK_OUT)));
    Effects.run(in, null, Duration.millis(460));
  }

  private void hideChallenge() {
    var host = inviteHost;
    inviteHost = null;
    challenge = null;
    if (host == null) return;
    if (reducedMotion() || host.getChildren().isEmpty()) {
      fx.getChildren().remove(host);
      return;
    }
    var dialog = host.getChildren().getFirst();
    boolean incoming = host.getStyleClass().contains("invite-backdrop");
    host.setMouseTransparent(true);
    var out =
        new Timeline(
            new KeyFrame(
                Duration.millis(240),
                new KeyValue(host.opacityProperty(), 0, Interpolator.EASE_IN),
                new KeyValue(dialog.translateYProperty(), incoming ? 40 : 0, Interpolator.EASE_IN),
                new KeyValue(
                    dialog.translateXProperty(), incoming ? 0 : 460, Interpolator.EASE_IN)));
    out.setOnFinished(e -> fx.getChildren().remove(host));
    out.play();
  }

  private String onlineName(long userId) {
    JsonNode online = store.state().payload(MessageType.ONLINE_LIST);
    if (online != null)
      for (var user : online.path("users"))
        if (user.path("userId").asLong() == userId) return user.path("displayName").asText();
    return null;
  }

  private Button settingsButton() {
    var glyph = new javafx.scene.shape.SVGPath();
    glyph.setContent(
        "M3 5h18v2H3z M3 11h18v2H3z M3 17h18v2H3z M7 3.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z"
            + " M15 9.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z M10 15.5a2.5 2.5 0 1 1 0 5a2.5 2.5"
            + " 0 1 1 0-5z");
    glyph.getStyleClass().add("sound-glyph");
    var icon = new StackPane(glyph);
    icon.setMinSize(22, 22);
    icon.setPrefSize(22, 22);
    var b = new Button();
    b.setGraphic(icon);
    b.setMinHeight(40);
    b.setMinWidth(Region.USE_PREF_SIZE);
    b.getStyleClass().addAll("icon-toggle", "on");
    b.setAccessibleText("Cài đặt (Esc)");
    b.setTooltip(new Tooltip("Cài đặt · Esc"));
    b.setOnAction(e -> openSettings());
    return b;
  }

  public void requestTimeout(vn.edu.nhom7.quiz.common.protocol.MessageType type) {
    if (type == MessageType.AUTHOR_REQUEST && community != null) community.timeout();
    message.setText(
        type == MessageType.ANSWER
            ? "Server chưa xác nhận câu trả lời · đang chờ, không gửi lại"
            : "Yêu cầu hết thời gian chờ. Bạn có thể thử lại.");
    if (type == MessageType.LOGIN || type == MessageType.REGISTER)
      auth.connected(network.isConnected());
    if (type == MessageType.LOGOUT) {
      network.disconnect();
      store.resetConnection();
    }
  }

  public void disconnected(String reason) {
    vn.edu.nhom7.quiz.client.assets.RemoteMedia.clear();
    // "Disconnected" is our own logout or reconnect, not a lost connection.
    if (store.state().profile() != null && !reason.equals("Disconnected")) {
      closeSettings();
      showOffline(reason);
    } else message.setText(reason + " · Không tự nối lại trận đấu");
    Sound.music(false);
    hideChallenge();
    auth.connected(false);
    if (game != null) game.setDisable(true);
  }

  public void onEvent(Envelope e) {
    vn.edu.nhom7.quiz.client.assets.RemoteMedia.onEvent(e);
    if (community != null) community.onEvent(e);
    if (e.type() == MessageType.AUTHOR_RESULT
        && Set.of("PUBLISH", "UNPUBLISH").contains(e.payload().path("action").asText()))
      refreshQuizzes();
    if (e.type() == MessageType.QUIZ_DETAIL && getLeft() instanceof QuizDetailView detail)
      detail.render(e.payload());
    if (e.type() == MessageType.HELLO_ACK) {
      auth.connected(true);
      message.setText("Đã kết nối. Bạn có thể đăng nhập.");
    }
    if (e.type() == MessageType.REGISTER_RESULT) {
      message.setText("Tạo tài khoản thành công. Hãy đăng nhập.");
      auth.connected(true);
    }
    if (e.type() == MessageType.ERROR) {
      auth.connected(network.isConnected());
      String code = e.payload().path("code").asText();
      if ((code.equals("INVALID_STATE") || code.equals("STALE_ROUND"))
          && store.state().activeMatchId() != null)
        commands.send(
            MessageType.MATCH_SNAPSHOT_REQUEST,
            store.state().activeMatchId(),
            null,
            new Payloads.MatchSnapshotRequest());
    }
    if (e.type() == MessageType.LOGIN_RESULT) {
      Sound.music(true);
      commands.send(
          MessageType.QUIZ_LIST_REQUEST, null, null, new Payloads.QuizListRequest(null, 1, 20));
      commands.send(MessageType.ONLINE_LIST_REQUEST, null, null, new Payloads.OnlineListRequest());
    }
    if (e.type() == MessageType.CHALLENGE_ACK || e.type() == MessageType.CHALLENGE_RECEIVED) {
      boolean incoming = e.type() == MessageType.CHALLENGE_RECEIVED;
      challenge =
          new ChallengeDialog(
              e.payload(),
              incoming,
              commands,
              incoming ? null : onlineName(e.payload().path("targetUserId").asLong()));
      showChallenge(challenge);
      if (incoming) Sound.play(Sound.Effect.INVITE);
    }
    if (e.type() == MessageType.CHALLENGE_CLOSED) {
      String status = e.payload().path("status").asText();
      message.setText(
          switch (status) {
            case "EXPIRED" -> "Lời mời đã hết hạn. Bạn có thể gửi lời mời mới.";
            case "REJECTED" -> "Đối thủ đã từ chối lời mời.";
            case "CANCELLED" -> "Lời mời đã được hủy.";
            default -> "Lời mời đã đóng.";
          });
    }
    if (e.type() == MessageType.CHALLENGE_CLOSED || e.type() == MessageType.MATCH_START)
      hideChallenge();
    if (e.type() == MessageType.RANKING_INVALIDATED && query.equals("RANKING"))
      commands.send(MessageType.RANKING_REQUEST, null, null, new Payloads.RankingRequest(1, 20));
    if (e.type() == MessageType.LOGOUT_ACK) {
      Sound.music(false);
      hideChallenge();
      vn.edu.nhom7.quiz.client.assets.RemoteMedia.clear();
      community = null;
      lobby = null;
      readyRounds.clear();
      selectedQuizCategory = null;
      query = "";
      visible = "";
      network.disconnect();
      auth.connected(false);
    }
  }

  private void refreshQuizzes() {
    commands.send(
        MessageType.QUIZ_LIST_REQUEST,
        null,
        null,
        new Payloads.QuizListRequest(selectedQuizCategory, 1, 20));
  }

  private void header(ClientState s) {
    if (s.profile() == null) {
      setTop(null);
      return;
    }
    Button myQuizzes =
        Ui.button(
            "Quiz của tôi",
            () -> {
              if (s.activeMatchId() != null) return;
              query = "COMMUNITY";
              if (community == null)
                community =
                    new CommunityQuizView(
                        commands,
                        s.payload(MessageType.QUIZ_LIST) == null
                            ? null
                            : s.payload(MessageType.QUIZ_LIST).path("categories"));
              visible = "";
              setLeft(null);
              render(s);
            });
    JsonNode quizList = s.payload(MessageType.QUIZ_LIST);
    myQuizzes.setDisable(
        s.activeMatchId() != null
            || quizList == null
            || !quizList.path("categories").isArray()
            || quizList.path("categories").isEmpty());
    var bar =
        new HBox(
            16,
            Ui.title("QUIZ ARENA"),
            Ui.label(
                s.profile().path("displayName").asText()
                    + " · "
                    + s.profile().path("totalScore").asLong()
                    + " điểm"),
            Ui.button(
                "Sảnh",
                () -> {
                  if (s.activeMatchId() == null) {
                    query = "";
                    visible = "";
                    refreshQuizzes();
                    store.leaveResult();
                  } else if (s.phase().equals("RESULT") || Ui.confirmLeaveMatch(this))
                    commands.send(
                        MessageType.EXIT_MATCH, s.activeMatchId(), null, new Payloads.ExitMatch());
                }),
            myQuizzes,
            Ui.button(
                "Xếp hạng",
                () -> {
                  if (s.activeMatchId() != null) return;
                  query = "RANKING";
                  visible = "";
                  commands.send(
                      MessageType.RANKING_REQUEST, null, null, new Payloads.RankingRequest(1, 20));
                  render(s);
                }),
            Ui.button(
                "Lịch sử",
                () -> {
                  if (s.activeMatchId() != null) return;
                  query = "HISTORY";
                  visible = "";
                  commands.send(
                      MessageType.HISTORY_REQUEST, null, null, new Payloads.HistoryRequest(1, 20));
                  render(s);
                }),
            Ui.button(
                "Chat",
                () -> {
                  showChat = !showChat;
                  updateChatPlacement();
                }),
            Ui.button(
                "Đăng xuất",
                () -> commands.send(MessageType.LOGOUT, null, null, new Payloads.Logout())));
    bar.getChildren().add(2, Ui.spacer());
    // Sound levels, mute, theme and text size all live in the Esc menu behind this button.
    bar.getChildren().add(3, settingsButton());
    bar.getStyleClass().add("app-header");
    ((Label) bar.getChildren().get(0)).getStyleClass().add("brand");
    ((Label) bar.getChildren().get(0)).setWrapText(false);
    ((Label) bar.getChildren().get(0)).setMinWidth(Region.USE_PREF_SIZE);
    ((Label) bar.getChildren().get(1)).setMaxWidth(160);
    ((Label) bar.getChildren().get(1)).setWrapText(false);
    HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
    for (var node : bar.getChildren()) {
      if (node instanceof Button b) {
        b.getStyleClass().add("nav");
        boolean active =
            (b.getText().equals("Sảnh") && query.isEmpty())
                || (b.getText().equals("Quiz của tôi") && query.equals("COMMUNITY"))
                || (b.getText().equals("Xếp hạng") && query.equals("RANKING"))
                || (b.getText().equals("Lịch sử") && query.equals("HISTORY"));
        if (active) b.getStyleClass().add("active");
        if (b.getText().equals("Đăng xuất")) b.getStyleClass().add("danger");
        if (s.activeMatchId() != null && b.getText().equals("Sảnh")) {
          b.getStyleClass().remove("active");
          // On the result screen leaving is harmless, so it stays a plain "back to lobby".
          if (!s.phase().equals("RESULT")) {
            b.setText("Rời trận");
            b.getStyleClass().add("danger");
          }
        }
        // Chat only exists during a match and on its result screen.
        if (b.getText().equals("Chat")
            && s.activeMatchId() == null
            && !s.phase().equals("RESULT_DETACHED")) {
          b.setVisible(false);
          b.setManaged(false);
        }
        if (s.activeMatchId() != null
            && java.util.Set.of("Quiz của tôi", "Xếp hạng", "Lịch sử").contains(b.getText()))
          b.setDisable(true);
      }
    }
    setTop(bar);
  }

  public void render(ClientState s) {
    if (!s.error().isBlank()) message.setText(s.error());
    // A match that ends (or starts) under the menu changes what the menu may offer.
    if (settings != null && settingsInMatch != inMatch(s)) {
      closeSettings();
      openSettings();
    }
    header(s);
    if (s.profile() == null) {
      setCenter(auth);
      setRight(null);
      return;
    }
    String view =
        s.activeMatchId() == null
            ? (query.isEmpty() ? (s.phase().equals("RESULT_DETACHED") ? "RESULT" : "LOBBY") : query)
            : s.phase();
    boolean rebuild = !view.equals(visible) || presentation != s.presentationVersion();
    if (view.equals("LOBBY"))
      rebuild |=
          s.onlineRevision() != onlineRevision
              || !Objects.equals(lastQuiz, s.payload(MessageType.QUIZ_LIST));
    if (view.equals("RANKING"))
      rebuild |= !Objects.equals(lastRanking, s.payload(MessageType.RANKING));
    if (view.equals("HISTORY"))
      rebuild |=
          !Objects.equals(lastHistory, s.payload(MessageType.HISTORY))
              || !Objects.equals(lastDetail, s.payload(MessageType.MATCH_DETAIL));
    if (view.equals("RESULT"))
      rebuild |=
          !Objects.equals(lastReview, s.payload(MessageType.LIVE_REVIEW))
              || !Objects.equals(lastRematch, s.payload(MessageType.REMATCH_STATUS))
              || s.data().containsKey(MessageType.RESULT_SESSION_CLOSED);
    if (rebuild) {
      visible = view;
      presentation = s.presentationVersion();
      onlineRevision = s.onlineRevision();
      lastQuiz = s.payload(MessageType.QUIZ_LIST);
      lastRanking = s.payload(MessageType.RANKING);
      lastHistory = s.payload(MessageType.HISTORY);
      lastDetail = s.payload(MessageType.MATCH_DETAIL);
      lastRematch = s.payload(MessageType.REMATCH_STATUS);
      lastReview = s.payload(MessageType.LIVE_REVIEW);
      if (game != null) game.dispose();
      game = null;
      result = null;
      switch (view) {
        case "LOBBY" -> {
          chat = null;
          setRight(null);
          var selection = lobby == null ? null : lobby.selection();
          lobby =
              new LobbyView(
                  s,
                  commands,
                  q -> {
                    lobby.selectQuiz(q);
                    var detail = new QuizDetailView(q, commands);
                    var close = Ui.button("Đóng", () -> setLeft(null));
                    detail.getChildren().add(close);
                    setLeft(detail);
                  },
                  selectedQuizCategory);
          lobby.restore(selection);
          if (selection != null
              && lobby.selection().quiz() == 0
              && getLeft() instanceof QuizDetailView) setLeft(null);
          setCenter(lobby);
        }
        case "COMMUNITY" -> {
          setRight(null);
          setCenter(community);
        }
        case "RANKING" -> {
          setRight(null);
          setCenter(Ui.scroll(new RankingView(lastRanking, commands)));
        }
        case "HISTORY" -> {
          setRight(null);
          setCenter(Ui.scroll(new HistoryView(lastHistory, lastDetail, commands)));
        }
        case "WAITING_READY" -> setCenter(Ui.scroll(new WaitingView(s, commands)));
        case "RESULT" -> {
          var shown = new ResultView(s, commands);
          result = shown;
          setCenter(Ui.scroll(shown));
          // Confetti and the win/lose sound play once per match, not on every rebuild.
          if (s.resultMatchId() != null && !s.resultMatchId().equals(celebratedMatch)) {
            celebratedMatch = s.resultMatchId();
            Platform.runLater(() -> shown.celebrate(fx, reducedMotion()));
          }
        }
        default -> {
          game = new GameView(s, commands, reducedMotion());
          setCenter(game);
        }
      }
      if (s.activeMatchId() != null || view.equals("RESULT")) {
        if (chat == null)
          chat =
              new ChatPanel(
                  text ->
                      commands.send(
                          MessageType.CHAT,
                          store.state().activeMatchId(),
                          null,
                          new Payloads.Chat(text)));
        updateChatPlacement();
      }
      if (view.equals("QUESTION_PREPARE")
          && s.roundId() != null
          && readyRounds.add(s.roundId())
          && s.question() != null) {
        var prepared =
            s.question().hasNonNull("questionAssetId")
                ? new vn.edu.nhom7.quiz.client.assets.AssetLoader()
                    .load(s.question().path("questionAssetId").asText())
                : java.util.concurrent.CompletableFuture.completedFuture(null);
        prepared.whenComplete(
            (image, error) ->
                Platform.runLater(
                    () -> {
                      if (!Objects.equals(store.state().roundId(), s.roundId())
                          || !Objects.equals(store.state().activeMatchId(), s.activeMatchId()))
                        return;
                      if (error != null) {
                        message.setText(
                            "Không thể tải/giải mã ảnh câu hỏi. Đang chờ server hủy trận an toàn.");
                      } else
                        commands.send(
                            MessageType.QUESTION_READY,
                            s.activeMatchId(),
                            s.roundId(),
                            new Payloads.QuestionReady(s.question().path("questionId").asLong()));
                    }));
      }
    }
    if (getCenter() instanceof ScrollPane scroll
        && scroll.getContent() instanceof WaitingView waiting) waiting.render(s);
    if (game != null) game.render(s);
    if (result != null) result.render(s);
    if (chat != null) chat.render(s);
  }

  private void updateChatPlacement() {
    if (chat == null) return;
    if (store.state().activeMatchId() != null || visible.equals("RESULT"))
      setRight(getWidth() >= 1120 || showChat ? chat : null);
  }

  public void close() {
    clock.stop();
    if (game != null) game.dispose();
  }
}
