package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ThemeTest {
  private static String decode(String url) {
    String prefix = "data:text/css;base64,";
    assertTrue(url.startsWith(prefix));
    return new String(
        Base64.getDecoder().decode(url.substring(prefix.length())), StandardCharsets.UTF_8);
  }

  @Test
  void largerTextScalesEveryFontSizeRule() throws Exception {
    String base;
    try (var in = Theme.class.getResourceAsStream("/ui/quiz-arena.css")) {
      base = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    long rules = base.lines().filter(l -> l.contains("-fx-font-size")).count();
    String xlarge = decode(Theme.scaledFonts(Theme.TextSize.XLARGE));
    assertEquals(rules, xlarge.lines().count());
    assertTrue(xlarge.contains("-fx-font-size: 18.0px"), "root text grows from 14px to 18px");
    assertTrue(xlarge.contains(".title { -fx-font-size: 32.1px; }"));
    assertTrue(
        decode(Theme.scaledFonts(Theme.TextSize.LARGE))
            .contains(".title { -fx-font-size: 28.6px; }"));
  }

  @Test
  void stylesheetsFollowTheSettings() {
    try {
      assertEquals(1, Theme.stylesheets().size());
      Theme.setMode(Theme.Mode.LIGHT);
      assertTrue(Theme.stylesheets().get(1).endsWith("/ui/quiz-arena-light.css"));
      Theme.setTextSize(Theme.TextSize.LARGE);
      assertEquals(3, Theme.stylesheets().size());
    } finally {
      Theme.setMode(Theme.Mode.DARK);
      Theme.setTextSize(Theme.TextSize.NORMAL);
    }
  }
}
