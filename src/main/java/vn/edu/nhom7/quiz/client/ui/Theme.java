package vn.edu.nhom7.quiz.client.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/**
 * Look-and-feel settings: dark or light mode, text size and reduced motion. The stylesheet set of
 * every registered scene follows them. Settings persist only after {@link #remember()} (called by
 * the desktop client), so tests and evidence renders always start from the defaults.
 */
public final class Theme {
  public enum Mode {
    DARK,
    LIGHT
  }

  public enum TextSize {
    NORMAL("Vừa", 14),
    LARGE("Lớn", 16),
    XLARGE("Rất lớn", 18);

    public final String label;
    final int rootPx;

    TextSize(String label, int rootPx) {
      this.label = label;
      this.rootPx = rootPx;
    }
  }

  private static final String BASE = "/ui/quiz-arena.css", LIGHT = "/ui/quiz-arena-light.css";
  private static final String[] FONTS = {
    "BeVietnamPro-Regular",
    "BeVietnamPro-Medium",
    "BeVietnamPro-SemiBold",
    "BeVietnamPro-Bold",
    "ChakraPetch-SemiBold",
    "ChakraPetch-Bold"
  };
  private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^}]*)}");
  private static final Pattern FONT_SIZE = Pattern.compile("-fx-font-size:\\s*([0-9.]+)px");
  private static final Map<TextSize, String> scaled = new EnumMap<>(TextSize.class);
  private static final List<Scene> scenes = new ArrayList<>();
  private static Mode mode = Mode.DARK;
  private static TextSize textSize = TextSize.NORMAL;
  private static boolean reduceMotion, fontsLoaded;
  private static Preferences prefs;

  private Theme() {}

  /** Registers the bundled fonts; safe to call more than once. */
  public static synchronized void loadFonts() {
    if (fontsLoaded) return;
    fontsLoaded = true;
    for (String name : FONTS)
      try (var in = Theme.class.getResourceAsStream("/fonts/" + name + ".ttf")) {
        if (in != null) Font.loadFont(in, 14);
      } catch (Exception ignored) {
        // A missing font falls back to the system font.
      }
  }

  /** Loads saved settings and saves every later change. */
  public static void remember() {
    try {
      prefs = Preferences.userNodeForPackage(Theme.class);
      mode = Mode.valueOf(prefs.get("theme", mode.name()));
      textSize = TextSize.valueOf(prefs.get("textSize", textSize.name()));
      reduceMotion = prefs.getBoolean("reduceMotion", reduceMotion);
    } catch (Throwable ignored) {
      prefs = null;
    }
    refresh();
  }

  /** Styles {@code scene} now and whenever a setting changes. */
  public static void attach(Scene scene) {
    loadFonts();
    if (!scenes.contains(scene)) scenes.add(scene);
    scene.getStylesheets().setAll(stylesheets());
  }

  public static void detach(Scene scene) {
    scenes.remove(scene);
  }

  /** The stylesheet URLs for the current settings, in order. */
  public static List<String> stylesheets() {
    var urls = new ArrayList<String>();
    urls.add(url(BASE));
    if (mode == Mode.LIGHT) urls.add(url(LIGHT));
    if (textSize != TextSize.NORMAL) urls.add(scaled.computeIfAbsent(textSize, Theme::scaledFonts));
    return urls;
  }

  /**
   * A stylesheet repeating every font-size rule of the base stylesheet, scaled for {@code size}.
   * Generated rather than hand-kept so a new rule can never be left out of the larger settings.
   */
  static String scaledFonts(TextSize size) {
    String css;
    try (var in = Theme.class.getResourceAsStream(BASE)) {
      css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Missing stylesheet " + BASE, e);
    }
    double factor = size.rootPx / (double) TextSize.NORMAL.rootPx;
    var out = new StringBuilder();
    var rule = RULE.matcher(css.replaceAll("(?s)/\\*.*?\\*/", ""));
    while (rule.find()) {
      var font = FONT_SIZE.matcher(rule.group(2));
      if (font.find())
        out.append(rule.group(1).trim())
            .append(" { -fx-font-size: ")
            .append(String.format(Locale.ROOT, "%.1f", Double.parseDouble(font.group(1)) * factor))
            .append("px; }\n");
    }
    return "data:text/css;base64,"
        + Base64.getEncoder().encodeToString(out.toString().getBytes(StandardCharsets.UTF_8));
  }

  public static Mode mode() {
    return mode;
  }

  public static TextSize textSize() {
    return textSize;
  }

  public static boolean reduceMotion() {
    return reduceMotion;
  }

  public static void setMode(Mode value) {
    mode = value;
    save("theme", value.name());
    refresh();
  }

  public static void setTextSize(TextSize value) {
    textSize = value;
    save("textSize", value.name());
    refresh();
  }

  public static void setReduceMotion(boolean value) {
    reduceMotion = value;
    save("reduceMotion", Boolean.toString(value));
  }

  private static void refresh() {
    var urls = stylesheets();
    for (var scene : List.copyOf(scenes)) scene.getStylesheets().setAll(urls);
  }

  private static String url(String path) {
    return Theme.class.getResource(path).toExternalForm();
  }

  private static void save(String key, String value) {
    if (prefs == null) return;
    try {
      prefs.put(key, value);
    } catch (Throwable ignored) {
    }
  }
}
