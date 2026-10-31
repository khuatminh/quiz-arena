package vn.edu.nhom7.quiz.client.ui;

import java.util.List;
import java.util.Random;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

/** Vector flames, overlay effects (red flash, streak banner, confetti) and animation helpers. */
final class Effects {
  /**
   * Evidence renders set this so every effect stops at its most visible frame instead of playing.
   */
  static boolean preview;

  static final String FLAME =
      "M12 1 C13 5 18 7.5 18 13.5 C18 18 15.3 21.5 12 21.5 C8.7 21.5 6 18.4 6 14.3 C6 11.2"
          + " 7.4 9.2 9 7.6 C9 10 10 11.6 11.5 11.6 C11 8.2 10.4 4.6 12 1 Z";
  static final String CORE =
      "M12 10.5 C13.2 12.7 15.2 14 15.2 16.8 C15.2 18.9 13.8 20.4 12 20.4 C10.2 20.4 8.8 18.9"
          + " 8.8 17 C8.8 15.5 9.6 14.4 10.5 13.6 C10.7 14.7 11.3 15.3 12 15.3 C11.6 13.8 11.4"
          + " 12.2 12 10.5 Z";
  private static final String[] CONFETTI = {
    "#F5C65A", "#087F70", "#E8364F", "#2F6FB3", "#FF8A1F", "#7B5CFF", "#2BC4A9", "#FFFFFF"
  };

  private Effects() {}

  /** 1 for streaks of 3–4, 2 for 5–9, 3 for 10 and more; 0 when there is no streak to show. */
  static int tier(int streak) {
    return streak >= 10 ? 3 : streak >= 5 ? 2 : streak >= 3 ? 1 : 0;
  }

  /** Outer and inner flame colours: orange, then red, then a blue-violet "legend" flame. */
  static Color[] flameColors(int tier) {
    return switch (tier) {
      case 3 -> new Color[] {Color.web("#7B5CFF"), Color.web("#5CE1FF")};
      case 2 -> new Color[] {Color.web("#E8364F"), Color.web("#FF9A3C")};
      default -> new Color[] {Color.web("#FF8A1F"), Color.web("#FFD23F")};
    };
  }

  static Node flame(int tier, double size) {
    var colors = flameColors(tier);
    var outer = new SVGPath();
    outer.setContent(FLAME);
    outer.setFill(colors[0]);
    var inner = new SVGPath();
    inner.setContent(CORE);
    inner.setFill(colors[1]);
    var shape = new Group(outer, inner);
    double scale = size / 22;
    shape.setScaleX(scale);
    shape.setScaleY(scale);
    var holder = new StackPane(new Group(shape));
    holder.setMinSize(size, size);
    holder.setPrefSize(size, size);
    holder.setMaxSize(size, size);
    holder.getStyleClass().add("flame");
    return holder;
  }

  /** Pill with a flame and the streak count; the flame flickers unless motion is reduced. */
  static Node streakBadge(int streak, boolean animate, List<Animation> running) {
    int tier = tier(streak);
    var fire = flame(tier, 22);
    var count = new Label(Integer.toString(streak));
    count.getStyleClass().add("streak-count");
    var badge = new HBox(4, fire, count);
    badge.setAlignment(Pos.CENTER_LEFT);
    badge.getStyleClass().addAll("streak-badge", "tier-" + tier);
    badge.setAccessibleText(streak + " câu đúng liên tiếp");
    badge.setMinWidth(Region.USE_PREF_SIZE);
    if (animate) {
      var flicker = new ScaleTransition(Duration.millis(380 - tier * 60), fire);
      flicker.setFromY(0.94);
      flicker.setToY(1.1);
      flicker.setFromX(1.03);
      flicker.setToX(0.96);
      flicker.setAutoReverse(true);
      flicker.setCycleCount(Animation.INDEFINITE);
      run(flicker, running, Duration.millis(200));
    }
    return badge;
  }

  static String streakTitle(int streak) {
    return streak + " câu liền!";
  }

  static String streakLine(int streak) {
    return switch (tier(streak)) {
      case 3 -> "Huyền thoại! Không ai cản nổi";
      case 2 -> "Đang cháy hết cỡ";
      default -> "Đang nóng tay";
    };
  }

  /** Big centred "N câu liền!" banner that pops in, holds and floats away. */
  static void streakBanner(Pane layer, int streak, List<Animation> running) {
    int tier = tier(streak);
    var title = new Label(streakTitle(streak));
    title.getStyleClass().add("streak-banner-title");
    var line = new Label(streakLine(streak));
    line.getStyleClass().add("streak-banner-line");
    var text = new VBox(2, title, line);
    text.setAlignment(Pos.CENTER_LEFT);
    var banner = new HBox(18, flame(tier, 72), text);
    banner.setAlignment(Pos.CENTER);
    banner.getStyleClass().addAll("streak-banner", "tier-" + tier);
    banner.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    banner.setAccessibleText(streakTitle(streak) + " " + streakLine(streak));
    var holder = centred(layer, banner);
    // Sits over the question card so the answer tiles stay readable during the reveal.
    StackPane.setAlignment(banner, Pos.TOP_CENTER);
    StackPane.setMargin(banner, new javafx.geometry.Insets(150, 0, 0, 0));
    banner.setOpacity(0);
    var pop =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(banner.opacityProperty(), 0),
                new KeyValue(banner.scaleXProperty(), 0.4),
                new KeyValue(banner.scaleYProperty(), 0.4),
                new KeyValue(banner.rotateProperty(), -8)),
            new KeyFrame(
                Duration.millis(380),
                new KeyValue(banner.opacityProperty(), 1),
                new KeyValue(banner.scaleXProperty(), 1, BACK_OUT),
                new KeyValue(banner.scaleYProperty(), 1, BACK_OUT),
                new KeyValue(banner.rotateProperty(), 0, BACK_OUT)),
            new KeyFrame(
                Duration.millis(1500),
                new KeyValue(banner.opacityProperty(), 1),
                new KeyValue(banner.translateYProperty(), 0)),
            new KeyFrame(
                Duration.millis(1900),
                new KeyValue(banner.opacityProperty(), 0),
                new KeyValue(banner.translateYProperty(), -60, Interpolator.EASE_IN)));
    pop.setOnFinished(e -> layer.getChildren().remove(holder));
    run(pop, running, Duration.millis(700));
  }

  /** Soft red vignette that pulses once when the player's answer is wrong. */
  static void wrongFlash(Pane layer, List<Animation> running) {
    var flash = new Region();
    flash.getStyleClass().add("wrong-flash");
    flash.setMouseTransparent(true);
    flash.setOpacity(0);
    layer.getChildren().add(flash);
    var pulse =
        new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(flash.opacityProperty(), 0)),
            new KeyFrame(
                Duration.millis(110),
                new KeyValue(flash.opacityProperty(), 1, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(260), new KeyValue(flash.opacityProperty(), 0.55)),
            new KeyFrame(Duration.millis(360), new KeyValue(flash.opacityProperty(), 0.85)),
            new KeyFrame(
                Duration.millis(900),
                new KeyValue(flash.opacityProperty(), 0, Interpolator.EASE_IN)));
    pulse.setOnFinished(e -> layer.getChildren().remove(flash));
    run(pulse, running, Duration.millis(120));
  }

  /** Paper confetti: a burst from the top centre, then a shower across the whole width. */
  static void confetti(Pane layer, List<Animation> running) {
    if (layer.getWidth() <= 0) {
      // Not laid out yet: start once the layer has a size, so the shower spans the screen.
      layer
          .widthProperty()
          .addListener(
              new javafx.beans.value.ChangeListener<Number>() {
                @Override
                public void changed(
                    javafx.beans.value.ObservableValue<? extends Number> o, Number b, Number a) {
                  if (a.doubleValue() <= 0) return;
                  layer.widthProperty().removeListener(this);
                  javafx.application.Platform.runLater(() -> confetti(layer, running));
                }
              });
      return;
    }
    var random = new Random(7);
    double w = Math.max(400, layer.getWidth()), h = Math.max(400, layer.getHeight());
    var pieces = new Pane();
    pieces.setMouseTransparent(true);
    layer.getChildren().add(pieces);
    var all = new ParallelTransition();
    for (int i = 0; i < 170; i++) {
      boolean burst = i < 50;
      Shape piece =
          i % 5 == 0
              ? new Circle(4 + random.nextDouble() * 2)
              : new Rectangle(7 + random.nextDouble() * 6, 10 + random.nextDouble() * 8);
      piece.setFill(Color.web(CONFETTI[random.nextInt(CONFETTI.length)]));
      if (piece.getFill().equals(Color.WHITE)) piece.setStroke(Color.web("#F5C65A"));
      double startX = burst ? w / 2 + (random.nextDouble() - 0.5) * 80 : random.nextDouble() * w;
      double startY = burst ? h * 0.18 : -30 - random.nextDouble() * h * 0.6;
      piece.setLayoutX(startX);
      piece.setLayoutY(startY);
      pieces.getChildren().add(piece);
      double fall = h + 60 - startY;
      double drift = (random.nextDouble() - 0.5) * (burst ? w * 0.9 : 160);
      double seconds = 2.4 + random.nextDouble() * 2.2;
      var motion = new Timeline();
      if (burst) {
        double up = -(80 + random.nextDouble() * 160);
        motion
            .getKeyFrames()
            .addAll(
                new KeyFrame(
                    Duration.ZERO,
                    new KeyValue(piece.translateXProperty(), 0),
                    new KeyValue(piece.translateYProperty(), 0)),
                new KeyFrame(
                    Duration.millis(420),
                    new KeyValue(piece.translateXProperty(), drift * 0.6, Interpolator.EASE_OUT),
                    new KeyValue(piece.translateYProperty(), up, Interpolator.EASE_OUT)),
                new KeyFrame(
                    Duration.seconds(seconds),
                    new KeyValue(piece.translateXProperty(), drift, Interpolator.LINEAR),
                    new KeyValue(piece.translateYProperty(), fall, Interpolator.EASE_IN)));
      } else
        motion
            .getKeyFrames()
            .addAll(
                new KeyFrame(
                    Duration.ZERO,
                    new KeyValue(piece.translateXProperty(), 0),
                    new KeyValue(piece.translateYProperty(), 0)),
                new KeyFrame(
                    Duration.seconds(seconds),
                    new KeyValue(piece.translateXProperty(), drift),
                    new KeyValue(piece.translateYProperty(), fall, Interpolator.LINEAR)));
      motion.setDelay(Duration.millis(burst ? random.nextInt(120) : random.nextInt(1400)));
      var spin = new RotateTransition(Duration.seconds(seconds), piece);
      spin.setByAngle((random.nextBoolean() ? 1 : -1) * (360 + random.nextInt(900)));
      spin.setDelay(motion.getDelay());
      var flutter = new ScaleTransition(Duration.millis(260 + random.nextInt(260)), piece);
      flutter.setFromX(1);
      flutter.setToX(0.15);
      flutter.setAutoReverse(true);
      flutter.setCycleCount((int) (seconds * 1000 / flutter.getDuration().toMillis()));
      flutter.setDelay(motion.getDelay());
      all.getChildren().addAll(motion, spin, flutter);
    }
    all.setOnFinished(e -> layer.getChildren().remove(pieces));
    run(all, running, Duration.millis(1150));
  }

  /** Scales shapes drawn on a 24-unit grid into a square of {@code size} pixels. */
  static StackPane glyph(double size, Node... shapes) {
    var shape = new Group(shapes);
    double scale = size / 24;
    shape.setScaleX(scale);
    shape.setScaleY(scale);
    var holder = new StackPane(new Group(shape));
    holder.setMinSize(size, size);
    holder.setPrefSize(size, size);
    holder.setMaxSize(size, size);
    return holder;
  }

  static SVGPath fill(String content, String color) {
    var path = new SVGPath();
    path.setContent(content);
    path.setFill(Color.web(color));
    return path;
  }

  static SVGPath stroke(String content, String color, double width) {
    var path = new SVGPath();
    path.setContent(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(Color.web(color));
    path.setStrokeWidth(width);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  static Node trophy(double size) {
    return glyph(
        size,
        stroke("M6.5 5 H3.5 V7 C3.5 9.2 5 10.6 6.8 10.9", "#F5C65A", 1.8),
        stroke("M17.5 5 H20.5 V7 C20.5 9.2 19 10.6 17.2 10.9", "#F5C65A", 1.8),
        fill("M6 3 H18 V8 C18 11.9 15.3 14.5 12 14.5 C8.7 14.5 6 11.9 6 8 Z", "#F5C65A"),
        fill(
            "M12 5.2 L12.9 7.1 L15 7.4 L13.5 8.8 L13.9 10.9 L12 9.9 L10.1 10.9 L10.5 8.8 L9 7.4"
                + " L11.1 7.1 Z",
            "#FFF4D0"),
        fill("M10.6 14.3 H13.4 V17.6 H10.6 Z", "#D9A93A"),
        fill(
            "M7.5 18 H16.5 C17.1 18 17.5 18.4 17.5 19 V21 H6.5 V19 C6.5 18.4 6.9 18 7.5 18 Z",
            "#F5C65A"));
  }

  static Node crown(double size) {
    return glyph(
        size,
        fill("M3 8 L7.5 12 L12 4.5 L16.5 12 L21 8 L19 18.5 H5 Z", "#F5C65A"),
        fill("M5 19.5 H19 V21 H5 Z", "#D9A93A"));
  }

  /** A rain cloud for a loss; the drops fall on a loop while it is shown. */
  static Node rainCloud(double size, boolean animate, List<Animation> running) {
    var drops = new Group();
    for (int i = 0; i < 3; i++) {
      var drop = stroke("M" + (8 + i * 4) + " 18.5 L" + (7.3 + i * 4) + " 21", "#8FC3F0", 1.6);
      drops.getChildren().add(drop);
      if (animate) {
        var fall =
            new Timeline(
                new KeyFrame(
                    Duration.ZERO,
                    new KeyValue(drop.translateYProperty(), -1.5),
                    new KeyValue(drop.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(200), new KeyValue(drop.opacityProperty(), 1)),
                new KeyFrame(
                    Duration.millis(800),
                    new KeyValue(drop.translateYProperty(), 2.5),
                    new KeyValue(drop.opacityProperty(), 0)));
        fall.setDelay(Duration.millis(i * 260));
        fall.setCycleCount(6);
        run(fall, running, Duration.millis(400));
      }
    }
    return glyph(
        size,
        fill(
            "M7 17 C4.5 17 2.8 15.2 2.8 12.9 C2.8 10.7 4.4 9 6.5 8.8 C7.3 6 9.6 4 12.4 4"
                + " C15.6 4 18.1 6.4 18.4 9.5 C20.2 9.8 21.4 11.3 21.4 13.2 C21.4 15.3 19.8 17"
                + " 17.6 17 Z",
            "#CFE0E6"),
        drops);
  }

  /** Two overlapping medals for a draw. */
  static Node draw(double size) {
    return glyph(
        size,
        fill("M8.5 6 A5.5 5.5 0 1 1 8.5 17 A5.5 5.5 0 1 1 8.5 6 Z", "#75D6AC"),
        fill("M15.5 6 A5.5 5.5 0 1 1 15.5 17 A5.5 5.5 0 1 1 15.5 6 Z", "#F5C65A"),
        fill("M7 11 H17 V12.6 H7 Z", "#123E43"));
  }

  /** Adds a full-size, click-through holder that centres {@code node} in the layer. */
  static StackPane centred(Pane layer, Node node) {
    var holder = new StackPane(node);
    holder.setPickOnBounds(false);
    holder.setMouseTransparent(true);
    layer.getChildren().add(holder);
    return holder;
  }

  /**
   * How hard an event hits, from the effects board: small events never shake or freeze, medium ones
   * shake a little, pause for a beat and throw a few sparks, large ones do all of it at full
   * strength. Callers skip every tier when motion is reduced.
   */
  enum Tier {
    SMALL(0, 0, 4),
    MEDIUM(0.4, 70, 8),
    LARGE(0.8, 120, 32);

    final double shake;
    final int hitStopMs, particles;

    Tier(double shake, int hitStopMs, int particles) {
      this.shake = shake;
      this.hitStopMs = hitStopMs;
      this.particles = particles;
    }
  }

  static final Color SPARK_GOOD = Color.web("#2BD48C"),
      SPARK_GOLD = Color.web("#F5C542"),
      SPARK_HOT = Color.web("#FF8A1F");

  /** A side-to-side shake that dies away; a large tier moves about 11 px at first. */
  static void shake(Node target, Tier tier, List<Animation> running) {
    if (tier.shake <= 0) return;
    double amplitude = 14 * tier.shake;
    int steps = 8;
    var shake = new Timeline();
    for (int i = 0; i <= steps; i++) {
      double x = i == steps ? 0 : amplitude * (1 - i / (double) steps) * (i % 2 == 0 ? 1 : -1);
      shake
          .getKeyFrames()
          .add(new KeyFrame(Duration.millis(i * 38), new KeyValue(target.translateXProperty(), x)));
    }
    shake.setOnFinished(e -> target.setTranslateX(0));
    run(shake, running, Duration.millis(steps * 38));
  }

  /** A tiny squash and spring back, for a pressed answer "locking in". */
  static void squash(Node target, List<Animation> running) {
    var squash =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(target.scaleXProperty(), 1),
                new KeyValue(target.scaleYProperty(), 1)),
            new KeyFrame(
                Duration.millis(70),
                new KeyValue(target.scaleXProperty(), 1.03),
                new KeyValue(target.scaleYProperty(), 0.92)),
            new KeyFrame(
                Duration.millis(260),
                new KeyValue(target.scaleXProperty(), 1, BACK_OUT),
                new KeyValue(target.scaleYProperty(), 1, BACK_OUT)));
    run(squash, running, Duration.millis(260));
  }

  /** Stamps {@code target} down from large and see-through, like a rubber stamp. */
  static void stamp(Node target, List<Animation> running) {
    var stamp =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(target.scaleXProperty(), 1.9),
                new KeyValue(target.scaleYProperty(), 1.9),
                new KeyValue(target.opacityProperty(), 0)),
            new KeyFrame(
                Duration.millis(180),
                new KeyValue(target.scaleXProperty(), 1, Interpolator.EASE_IN),
                new KeyValue(target.scaleYProperty(), 1, Interpolator.EASE_IN),
                new KeyValue(target.opacityProperty(), 1)));
    run(stamp, running, Duration.millis(180));
  }

  /** Big text slamming down into place, such as the question number. */
  static void slam(Node target, List<Animation> running) {
    var slam =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(target.scaleXProperty(), 1.8),
                new KeyValue(target.scaleYProperty(), 1.8),
                new KeyValue(target.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(120), new KeyValue(target.opacityProperty(), 1)),
            new KeyFrame(
                Duration.millis(320),
                new KeyValue(target.scaleXProperty(), 1, BACK_OUT),
                new KeyValue(target.scaleYProperty(), 1, BACK_OUT)));
    run(slam, running, Duration.millis(320));
  }

  /**
   * The hit-stop: a white flash held over {@code target} for the tier's pause, then released, with
   * {@code after} (usually the sparks) starting the moment the pause ends.
   */
  static void hitFlash(
      Pane layer, Node target, Tier tier, List<Animation> running, Runnable after) {
    var box = within(layer, target);
    var flash = new Region();
    flash.getStyleClass().add("hit-flash");
    flash.setManaged(false);
    flash.setMouseTransparent(true);
    flash.resizeRelocate(box.getMinX(), box.getMinY(), box.getWidth(), box.getHeight());
    layer.getChildren().add(flash);
    int hold = Math.max(40, tier.hitStopMs);
    var pulse =
        new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(flash.opacityProperty(), 0.85)),
            new KeyFrame(
                Duration.millis(hold),
                e -> {
                  if (after != null) after.run();
                },
                new KeyValue(flash.opacityProperty(), 0.85)),
            new KeyFrame(
                Duration.millis(hold + 200),
                new KeyValue(flash.opacityProperty(), 0, Interpolator.EASE_OUT)));
    pulse.setOnFinished(e -> layer.getChildren().remove(flash));
    if (preview) {
      if (after != null) after.run();
      flash.setOpacity(0.35);
      return;
    }
    run(pulse, running, Duration.millis(hold));
  }

  /**
   * Sparks flying out from the middle of {@code from}: a handful for medium, a shower for large.
   */
  static void burst(Pane layer, Node from, Tier tier, Color color, List<Animation> running) {
    var box = within(layer, from);
    double cx = box.getCenterX(), cy = box.getCenterY();
    var random = new Random(tier.particles * 31L);
    double reach = tier == Tier.LARGE ? 220 : 120;
    for (int i = 0; i < tier.particles; i++) {
      double angle = 2 * Math.PI * i / tier.particles + random.nextDouble() * 0.6;
      double distance = reach * (0.55 + random.nextDouble() * 0.45);
      boolean streak = i % 2 == 0;
      var spark = new Rectangle(streak ? 3 : 7, streak ? 12 : 7);
      spark.setArcWidth(streak ? 3 : 7);
      spark.setArcHeight(streak ? 3 : 7);
      spark.setFill(i % 3 == 0 ? Color.WHITE : color);
      spark.setManaged(false);
      spark.setMouseTransparent(true);
      spark.setRotate(Math.toDegrees(angle) + 90);
      spark.relocate(cx, cy);
      layer.getChildren().add(spark);
      int duration = 420 + random.nextInt(260);
      var fly =
          new Timeline(
              new KeyFrame(
                  Duration.millis(duration),
                  new KeyValue(
                      spark.translateXProperty(),
                      Math.cos(angle) * distance,
                      Interpolator.EASE_OUT),
                  new KeyValue(
                      spark.translateYProperty(),
                      Math.sin(angle) * distance,
                      Interpolator.EASE_OUT),
                  new KeyValue(spark.opacityProperty(), 0, Interpolator.EASE_IN),
                  new KeyValue(spark.scaleXProperty(), 0.4),
                  new KeyValue(spark.scaleYProperty(), 0.4)));
      fly.setOnFinished(e -> layer.getChildren().remove(spark));
      run(fly, running, Duration.millis(duration * 0.45));
    }
  }

  /** A ring that races outwards from {@code from} and fades: the large tier's shockwave. */
  static void shockwave(Pane layer, Node from, Color color, List<Animation> running) {
    var box = within(layer, from);
    var ring = new Circle(box.getCenterX(), box.getCenterY(), 24);
    ring.setFill(Color.TRANSPARENT);
    ring.setStroke(color);
    ring.setStrokeWidth(10);
    ring.setManaged(false);
    ring.setMouseTransparent(true);
    layer.getChildren().add(ring);
    var wave =
        new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(ring.radiusProperty(), 24),
                new KeyValue(ring.opacityProperty(), 0.9)),
            new KeyFrame(
                Duration.millis(720),
                new KeyValue(ring.radiusProperty(), 360, Interpolator.EASE_OUT),
                new KeyValue(ring.strokeWidthProperty(), 1, Interpolator.EASE_OUT),
                new KeyValue(ring.opacityProperty(), 0, Interpolator.EASE_IN)));
    wave.setOnFinished(e -> layer.getChildren().remove(ring));
    run(wave, running, Duration.millis(260));
  }

  /** Drains the colour out of {@code target} slowly, for a lost match. */
  static void fade(Node target, List<Animation> running) {
    var grey = new javafx.scene.effect.ColorAdjust();
    target.setEffect(grey);
    var drain =
        new Timeline(
            new KeyFrame(
                Duration.millis(1400),
                new KeyValue(grey.saturationProperty(), -0.65, Interpolator.EASE_OUT)));
    drain.setDelay(Duration.millis(400));
    run(drain, running, Duration.millis(1400));
  }

  /**
   * Runs {@code action} once {@code layer} and everything under its host are laid out, so effects
   * that start from a node's position find it where it is drawn. Runs at once when already laid
   * out.
   */
  static void whenLaidOut(Pane layer, Runnable action) {
    if (layer instanceof Layer pending
        && (layer.getScene() == null || layer.isNeedsLayout() || layer.getWidth() <= 0)) {
      pending.waiting.add(action);
      layer.requestLayout();
    } else action.run();
  }

  /** The bounds of {@code node} in {@code layer}'s coordinates. */
  private static javafx.geometry.Bounds within(Pane layer, Node node) {
    return layer.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
  }

  /** Plays an animation, or in preview mode freezes it at {@code peak} so a snapshot shows it. */
  static void run(Animation animation, List<Animation> running, Duration peak) {
    if (running != null) running.add(animation);
    if (preview) {
      animation.setOnFinished(null);
      animation.playFrom(peak);
      animation.pause();
    } else animation.play();
  }

  /** Overshoots slightly before settling, for things that "land". */
  static final Interpolator BACK_OUT =
      new Interpolator() {
        @Override
        protected double curve(double t) {
          double c1 = 1.9, c3 = c1 + 1, u = t - 1;
          return 1 + c3 * u * u * u + c1 * u * u;
        }
      };

  /**
   * A transparent, click-through pane that hosts overlay effects above a view. The host lays it out
   * over its whole area; every child is stretched to the layer's size.
   */
  static final class Layer extends Pane {
    private final Rectangle clip = new Rectangle();
    private final java.util.List<Runnable> waiting = new java.util.ArrayList<>();

    Layer() {
      setManaged(false);
      setPickOnBounds(false);
      getStyleClass().add("fx-layer");
      setClip(clip);
    }

    @Override
    protected void layoutChildren() {
      clip.setWidth(getWidth());
      clip.setHeight(getHeight());
      for (var child : getManagedChildren()) child.resizeRelocate(0, 0, getWidth(), getHeight());
      // The layer is its host's last child, so by now the rest of the host has its final layout.
      if (getScene() != null && getWidth() > 0 && !waiting.isEmpty()) {
        var ready = java.util.List.copyOf(waiting);
        waiting.clear();
        ready.forEach(Runnable::run);
      }
    }
  }
}
