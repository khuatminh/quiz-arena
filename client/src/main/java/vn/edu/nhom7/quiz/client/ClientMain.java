package vn.edu.nhom7.quiz.client;

import java.nio.file.*;
import java.util.*;
import javafx.animation.*;
import javafx.application.*;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.nhom7.quiz.client.network.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.client.support.*;
import vn.edu.nhom7.quiz.client.ui.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ClientMain extends Application {
  private ClientStore store;
  private NetworkClient network;
  private AppShell shell;
  private FixtureReplay replay;
  private CommandGateway gateway;
  private Timeline pendingTimer;

  public static Map<String, String> parseArgs(List<String> args) {
    var result = new HashMap<String, String>();
    for (String arg : args)
      if (arg.startsWith("--")) {
        var pair = arg.substring(2).split("=", 2);
        result.put(pair[0], pair.length > 1 ? pair[1] : "true");
      }
    return Map.copyOf(result);
  }

  public void start(Stage stage) {
    var args = parseArgs(getParameters().getRaw());
    store = new ClientStore();
    var tracker = new RequestTracker();
    network =
        new NetworkClient(
            e -> {
              if (e.requestId() != null) tracker.resolve(e.requestId());
              if (gateway != null) gateway.onEvent(e);
              store.accept(e);
              Platform.runLater(() -> shell.onEvent(e));
            },
            reason -> Platform.runLater(() -> shell.disconnected(reason)));
    gateway = new CommandGateway(network, store, tracker);
    UiCommandSink sink =
        args.containsKey("fixture") ? (type, match, round, payload) -> UUID.randomUUID() : gateway;
    shell = new AppShell(store, network, sink);
    var captured = new HashSet<String>();
    store.subscribe(
        s ->
            Platform.runLater(
                () -> {
                  shell.render(s);
                  if (args.containsKey("evidence")) {
                    if (captured.add(s.phase()))
                      scheduleCapture(
                          shell, Path.of(args.get("evidence"), s.phase().toLowerCase() + ".png"));
                    if (s.question() != null && !s.question().isNull()) {
                      String variant =
                          s.phase().toLowerCase()
                              + "-"
                              + s.question().path("questionType").asText().toLowerCase();
                      if (captured.add(variant))
                        scheduleCapture(shell, Path.of(args.get("evidence"), variant + ".png"));
                    }
                  }
                }));
    var scene =
        new Scene(
            shell,
            Integer.parseInt(args.getOrDefault("width", "1280")),
            Integer.parseInt(args.getOrDefault("height", "800")));
    scene.getStylesheets().add(getClass().getResource("/ui/quiz-arena.css").toExternalForm());
    stage.setTitle("Quiz Arena · Đấu trí 1 đối 1");
    stage.setMinWidth(1024);
    stage.setMinHeight(720);
    stage.setScene(scene);
    stage.setOnCloseRequest(
        e -> {
          if (network.isConnected()) {
            e.consume();
            gateway.send(MessageType.LOGOUT, null, null, new Payloads.Logout());
            var pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(done -> Platform.exit());
            pause.play();
          }
        });
    stage.show();
    if (args.containsKey("exit-after-seconds")) {
      var exit =
          new PauseTransition(Duration.seconds(Double.parseDouble(args.get("exit-after-seconds"))));
      exit.setOnFinished(e -> Platform.exit());
      exit.play();
    }
    var notified = new HashSet<UUID>();
    long[] epoch = {-1};
    pendingTimer =
        new Timeline(
            new KeyFrame(
                Duration.millis(250),
                e -> {
                  if (epoch[0] != store.state().connectionEpoch()) {
                    tracker.clear();
                    notified.clear();
                    epoch[0] = store.state().connectionEpoch();
                  }
                  for (UUID id : tracker.expired(System.nanoTime()))
                    if (notified.add(id)) {
                      var pending = tracker.resolve(id);
                      pending.ifPresent(p -> shell.requestTimeout(p.type()));
                    }
                }));
    pendingTimer.setCycleCount(Animation.INDEFINITE);
    pendingTimer.play();
    if (args.containsKey("evidence")) capture(shell, Path.of(args.get("evidence"), "auth.png"));
    if (args.containsKey("fixture")) {
      replay = new FixtureReplay();
      replay.play(Long.parseLong(args.getOrDefault("participant", "101")), store::accept);
    }
  }

  private static void scheduleCapture(javafx.scene.Parent node, Path path) {
    var pause = new PauseTransition(Duration.millis(650));
    pause.setOnFinished(e -> capture(node, path));
    pause.play();
  }

  private static void capture(javafx.scene.Parent node, Path path) {
    try {
      node.applyCss();
      node.layout();
      var shot = node.snapshot(null, null);
      var image =
          new java.awt.image.BufferedImage(
              (int) shot.getWidth(),
              (int) shot.getHeight(),
              java.awt.image.BufferedImage.TYPE_INT_ARGB);
      var reader = shot.getPixelReader();
      for (int y = 0; y < image.getHeight(); y++)
        for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, reader.getArgb(x, y));
      Files.createDirectories(path.getParent());
      javax.imageio.ImageIO.write(image, "png", path.toFile());
    } catch (Exception e) {
      System.err.println("Cannot save UI evidence: " + e.getClass().getSimpleName());
    }
  }

  public void stop() {
    if (pendingTimer != null) pendingTimer.stop();
    if (replay != null) replay.close();
    if (shell != null) shell.close();
    if (network != null) network.close();
    if (store != null) store.close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
