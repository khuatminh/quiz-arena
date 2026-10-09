package vn.edu.nhom7.quiz.client.assets;

import java.net.URL;
import javafx.scene.image.*;
import vn.edu.nhom7.quiz.common.assets.AssetRegistry;

public final class AssetLoader {
  private final AssetRegistry registry = AssetRegistry.loadDefault();

  public URL resource(String id) {
    var asset = registry.find(id).orElse(null);
    URL u = asset == null ? null : getClass().getResource(asset.path());
    if (u == null && asset != null && asset.required())
      throw new IllegalStateException("Missing required asset: " + id);
    return u != null ? u : getClass().getResource("/assets/images/placeholder.png");
  }

  public ImageView view(String id, double size) {
    URL url = resource(id);
    var v = new ImageView(new Image(url.toExternalForm(), size, size, true, true));
    v.setFitWidth(size);
    v.setFitHeight(size);
    v.setPreserveRatio(true);
    return v;
  }
}
