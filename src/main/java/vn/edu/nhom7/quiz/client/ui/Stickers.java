package vn.edu.nhom7.quiz.client.ui;

import java.util.*;
import java.util.regex.Pattern;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * Chat stickers. A sticker travels as an ordinary chat message {@code [sticker:id]}, so the server
 * applies its usual length and rate limits; clients draw known ids as vector art.
 */
final class Stickers {
  record Sticker(String id, String caption, String top, String bottom) {}

  static final List<Sticker> ALL =
      List.of(
          new Sticker("fire", "Cháy quá!", "#FFB23F", "#FF5A1F"),
          new Sticker("gg", "GG", "#F7CF6A", "#D99A1E"),
          new Sticker("like", "Hay!", "#2BC4A9", "#087F70"),
          new Sticker("hurry", "Nhanh lên!", "#5AA9F0", "#2F6FB3"),
          new Sticker("love", "Tuyệt!", "#FF7A9A", "#E8364F"),
          new Sticker("haha", "Haha", "#FFD84D", "#F2A516"),
          new Sticker("go", "Cố lên!", "#A08BFF", "#7B5CFF"),
          new Sticker("huh", "Hả?", "#9AAFB2", "#5E7A7E"));

  private static final Pattern TOKEN = Pattern.compile("^\\[sticker:([a-z]+)]$");

  private Stickers() {}

  static String token(Sticker sticker) {
    return "[sticker:" + sticker.id() + "]";
  }

  /** The sticker a chat message stands for, if it is exactly a known sticker token. */
  static Optional<Sticker> parse(String text) {
    if (text == null) return Optional.empty();
    var m = TOKEN.matcher(text.strip());
    if (!m.matches()) return Optional.empty();
    return ALL.stream().filter(s -> s.id().equals(m.group(1))).findFirst();
  }

  /** A rounded, gradient tile with a white icon and the caption underneath. */
  static StackPane view(Sticker sticker, double size) {
    return view(sticker, size, true);
  }

  /** Small tray tiles leave the caption out; it is in the tooltip and the sent bubble. */
  static StackPane view(Sticker sticker, double size, boolean captioned) {
    var icon = icon(sticker, size * (captioned ? 0.5 : 0.58));
    var caption = new Label(sticker.caption());
    caption.getStyleClass().add("sticker-caption");
    caption.setStyle("-fx-font-size: " + Math.round(Math.max(10, size * 0.15)) + "px;");
    var content = captioned ? new VBox(size * 0.04, icon, caption) : new VBox(icon);
    content.setAlignment(Pos.CENTER);
    var tile = new StackPane(content);
    tile.getStyleClass().add("sticker");
    tile.setStyle(
        "-fx-background-color: linear-gradient(to bottom, "
            + sticker.top()
            + ", "
            + sticker.bottom()
            + "); -fx-background-radius: "
            + Math.round(size * 0.24)
            + ";");
    tile.setMinSize(size, size);
    tile.setPrefSize(size, size);
    tile.setMaxSize(size, size);
    tile.setAccessibleText("Sticker " + sticker.caption());
    return tile;
  }

  private static Node icon(Sticker sticker, double size) {
    String white = "#FFFFFF", ink = sticker.bottom();
    return switch (sticker.id()) {
      case "fire" ->
          Effects.glyph(
              size, Effects.fill(Effects.FLAME, white), Effects.fill(Effects.CORE, "#FFD23F"));
      case "gg" ->
          Effects.glyph(
              size,
              Effects.stroke("M6.5 5 H3.5 V7 C3.5 9.2 5 10.6 6.8 10.9", white, 1.8),
              Effects.stroke("M17.5 5 H20.5 V7 C20.5 9.2 19 10.6 17.2 10.9", white, 1.8),
              Effects.fill("M6 3 H18 V8 C18 11.9 15.3 14.5 12 14.5 C8.7 14.5 6 11.9 6 8 Z", white),
              Effects.fill("M10.6 14.3 H13.4 V17.6 H10.6 Z", white),
              Effects.fill("M7 18 H17 V21 H7 Z", white));
      case "like" ->
          Effects.glyph(
              size,
              Effects.fill("M2.5 10 H6.5 V21 H2.5 Z", white),
              Effects.fill(
                  "M8 21 V10 L12 2.5 C13.6 2.5 14.6 3.6 14.3 5.2 L13.6 9 H19.4 C20.9 9 22 10.4"
                      + " 21.7 11.9 L20.3 19.3 C20.1 20.3 19.2 21 18.2 21 Z",
                  white));
      case "hurry" ->
          Effects.glyph(
              size,
              Effects.fill("M12 2 A10 10 0 1 1 12 22 A10 10 0 1 1 12 2 Z", white),
              Effects.stroke("M12 6.5 V12 L15.8 14.2", ink, 2.4),
              Effects.fill("M10 0.5 H14 V2.5 H10 Z", white));
      case "love" ->
          Effects.glyph(
              size,
              Effects.fill(
                  "M12 21 C12 21 3 15.2 3 8.9 C3 5.7 5.4 3.6 8 3.6 C9.8 3.6 11.2 4.7 12 6.1 C12.8"
                      + " 4.7 14.2 3.6 16 3.6 C18.6 3.6 21 5.7 21 8.9 C21 15.2 12 21 12 21 Z",
                  white));
      case "haha" ->
          Effects.glyph(
              size,
              Effects.fill("M12 2 A10 10 0 1 1 12 22 A10 10 0 1 1 12 2 Z", white),
              Effects.stroke("M6.8 10 Q8.4 7.6 10 10 M14 10 Q15.6 7.6 17.2 10", "#B86E00", 1.8),
              Effects.fill(
                  "M6.5 13 H17.5 C17.5 16.5 15 19 12 19 C9 19 6.5 16.5 6.5 13 Z", "#B86E00"));
      case "go" ->
          Effects.glyph(size, Effects.fill("M13.5 1.5 L4 14 H11 L10 22.5 L20 9.5 H13 Z", white));
      default ->
          Effects.glyph(
              size,
              Effects.stroke(
                  "M8.3 8.4 C8.3 6 10 4.4 12.3 4.4 C14.6 4.4 16.2 5.9 16.2 8 C16.2 10.8 12.6 11.2"
                      + " 12.6 14.6",
                  white,
                  3),
              Effects.fill("M12.6 17.4 A1.8 1.8 0 1 1 12.6 21 A1.8 1.8 0 1 1 12.6 17.4 Z", white));
    };
  }
}
