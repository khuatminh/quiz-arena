package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StickersTest {
  @Test
  void onlyAnExactKnownTokenIsASticker() {
    for (var sticker : Stickers.ALL) {
      String token = Stickers.token(sticker);
      assertEquals(sticker, Stickers.parse(token).orElseThrow());
      assertTrue(token.codePointCount(0, token.length()) <= 300);
    }
    assertTrue(Stickers.parse("[sticker:nope]").isEmpty());
    assertTrue(Stickers.parse("haha [sticker:fire]").isEmpty());
    assertTrue(Stickers.parse("Cháy quá!").isEmpty());
  }

  @Test
  void flameTiersChangeAtThreeFiveAndTen() {
    assertEquals(0, Effects.tier(2));
    assertEquals(1, Effects.tier(3));
    assertEquals(1, Effects.tier(4));
    assertEquals(2, Effects.tier(5));
    assertEquals(2, Effects.tier(9));
    assertEquals(3, Effects.tier(10));
    assertNotEquals(Effects.flameColors(1)[0], Effects.flameColors(2)[0]);
    assertNotEquals(Effects.flameColors(2)[0], Effects.flameColors(3)[0]);
  }
}
