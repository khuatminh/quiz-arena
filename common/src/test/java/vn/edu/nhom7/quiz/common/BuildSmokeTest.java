package vn.edu.nhom7.quiz.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {
  @Test
  void usesAgreedRuntime() {
    assertEquals(21, Runtime.version().feature());
  }
}
