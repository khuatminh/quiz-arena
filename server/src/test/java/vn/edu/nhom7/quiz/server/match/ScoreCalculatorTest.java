package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {
  @Test
  void exactNanosecondBoundaries() {
    assertEquals(3, ScoreCalculator.calculate(true, 0));
    assertEquals(3, ScoreCalculator.calculate(true, 5_000_000_000L));
    assertEquals(2, ScoreCalculator.calculate(true, 5_000_000_001L));
    assertEquals(2, ScoreCalculator.calculate(true, 10_000_000_000L));
    assertEquals(1, ScoreCalculator.calculate(true, 10_000_000_001L));
    assertEquals(1, ScoreCalculator.calculate(true, 15_000_000_000L));
    assertEquals(0, ScoreCalculator.calculate(false, 1));
    assertThrows(IllegalArgumentException.class, () -> ScoreCalculator.calculate(true, -1));
    assertThrows(
        IllegalArgumentException.class, () -> ScoreCalculator.calculate(true, 15_000_000_001L));
  }

  @Test
  void normalization() {
    assertEquals("hà nội", ShortAnswerNormalizer.normalize("  HÀ\u00a0  NỘI  "));
    assertEquals(
        ShortAnswerNormalizer.normalize("Ha\u0300 Nội"), ShortAnswerNormalizer.normalize("Hà Nội"));
    assertNotEquals(
        ShortAnswerNormalizer.normalize("Ha Noi"), ShortAnswerNormalizer.normalize("Hà Nội"));
  }
}
