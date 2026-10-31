package vn.edu.nhom7.quiz.common.assets;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AssetRegistryTest {
  @Test
  void bundledRegistry() {
    var r = AssetRegistry.loadDefault();
    assertEquals("1", r.version());
    for (int i = 1; i <= 8; i++) assertTrue(r.isAvatar("avatar-%02d".formatted(i)));
    assertFalse(r.isAvatar("https://bad"));
    assertTrue(
        r.assets().stream()
            .allMatch(a -> a.path().startsWith("/assets/") && !a.attribution().isBlank()));
  }
}
