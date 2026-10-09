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
  private JsonNode lastQuiz, lastRanking, lastHistory, lastDetail, lastRematch;
  private GameView game;
  private ResultView result;
  private ChatPanel chat;
  private ChallengeDialog challenge;
  private LobbyView lobby;
  private final boolean reducedMotion = Boolean.getBoolean("quiz.reducedMotion");

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
            return sink.send(type, match, round, payload);
          } catch (Exception e) {
            message.setText(e.getMessage());
            return null;
          }
        };
    auth =
        new AuthView(
            commands,
            (host, port) -> {
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
            });
    setCenter(auth);
    setBottom(message);
    widthProperty().addListener((o, b, a) -> updateChatPlacement());
    clock =
        new Timeline(
            new KeyFrame(
                Duration.millis(100),
                e -> {
                  if (game != null) game.tick();
                  if (challenge != null)
                    challenge.tick(System.currentTimeMillis() + network.time.offsetMs());
                }));
    clock.setCycleCount(Animation.INDEFINITE);
    clock.play();
  }

  public void requestTimeout(vn.edu.nhom7.quiz.common.protocol.MessageType type) {
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
    message.setText(reason + " · Không tự nối lại trận đấu");
    auth.connected(false);
    if (game != null) game.setDisable(true);
  }

  public void onEvent(Envelope e) {
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
      commands.send(
          MessageType.QUIZ_LIST_REQUEST, null, null, new Payloads.QuizListRequest(null, 1, 20));
      commands.send(MessageType.ONLINE_LIST_REQUEST, null, null, new Payloads.OnlineListRequest());
    }
    if (e.type() == MessageType.CHALLENGE_ACK || e.type() == MessageType.CHALLENGE_RECEIVED) {
      challenge =
          new ChallengeDialog(e.payload(), e.type() == MessageType.CHALLENGE_RECEIVED, commands);
      setLeft(challenge);
    }
    if (e.type() == MessageType.CHALLENGE_CLOSED || e.type() == MessageType.MATCH_START) {
      challenge = null;
      setLeft(null);
    }
    if (e.type() == MessageType.RANKING_INVALIDATED && query.equals("RANKING"))
      commands.send(MessageType.RANKING_REQUEST, null, null, new Payloads.RankingRequest(1, 20));
    if (e.type() == MessageType.LOGOUT_ACK) {
      network.disconnect();
      auth.connected(false);
    }
  }

  private void header(ClientState s) {
    if (s.profile() == null) {
      setTop(null);
      return;
    }
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
                    store.leaveResult();
                  } else
                    commands.send(
                        MessageType.EXIT_MATCH, s.activeMatchId(), null, new Payloads.ExitMatch());
                }),
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
    bar.setPadding(new javafx.geometry.Insets(16));
    setTop(bar);
  }

  public void render(ClientState s) {
    if (!s.error().isBlank()) message.setText(s.error());
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
          !Objects.equals(lastRematch, s.payload(MessageType.REMATCH_STATUS))
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
      if (game != null) game.dispose();
      game = null;
      result = null;
      switch (view) {
        case "LOBBY" -> {
          chat = null;
          setRight(null);
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
                  });
          setCenter(lobby);
        }
        case "RANKING" -> {
          setRight(null);
          setCenter(Ui.scroll(new RankingView(lastRanking, commands)));
        }
        case "HISTORY" -> {
          setRight(null);
          setCenter(Ui.scroll(new HistoryView(lastHistory, lastDetail, commands)));
        }
        case "WAITING_READY" -> setCenter(new WaitingView(s, commands));
        case "RESULT" -> {
          result = new ResultView(s, commands);
          setCenter(Ui.scroll(result));
        }
        default -> {
          game = new GameView(s, commands, reducedMotion);
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
        Platform.runLater(
            () ->
                commands.send(
                    MessageType.QUESTION_READY,
                    s.activeMatchId(),
                    s.roundId(),
                    new Payloads.QuestionReady(s.question().path("questionId").asLong())));
      }
    }
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
