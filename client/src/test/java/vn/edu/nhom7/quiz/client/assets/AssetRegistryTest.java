package vn.edu.nhom7.quiz.client.assets;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.assets.AssetRegistry;

class AssetRegistryTest {
  @Test
  void allBundledAssetsExistAndUnknownUsesPlaceholder() {
    var loader = new AssetLoader();
    for (var a : AssetRegistry.loadDefault().assets())
      assertNotNull(getClass().getResource(a.path()), a.id());
    assertEquals(loader.resource("placeholder"), loader.resource("does-not-exist"));
  }
}
