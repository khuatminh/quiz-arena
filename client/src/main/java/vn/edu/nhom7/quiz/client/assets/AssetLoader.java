package vn.edu.nhom7.quiz.client.assets;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.image.*;
import vn.edu.nhom7.quiz.common.assets.AssetRegistry;

public final class AssetLoader {
  private static final AssetRegistry registry = AssetRegistry.loadDefault();
  private static final java.util.concurrent.ConcurrentMap<String, Image> bundled =
      new java.util.concurrent.ConcurrentHashMap<>();

  public URL resource(String id) {
    var asset = registry.find(id).orElse(null);
    URL u = asset == null ? null : getClass().getResource(asset.path());
    if (u == null && asset != null && asset.required())
      throw new IllegalStateException("Missing required asset: " + id);
    return u != null ? u : getClass().getResource("/assets/images/placeholder.png");
  }

  /** Completes only after bytes are verified and image pixels are decoded. */
  public CompletableFuture<Image> load(String id) {
    if (id != null && id.startsWith("media-"))
      return RemoteMedia.fetch(id)
          .thenApplyAsync(
              bytes -> {
                Image image = new Image(new ByteArrayInputStream(bytes));
                if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0)
                  throw new IllegalArgumentException("Không thể đọc ảnh câu hỏi.");
                return image;
              });
    return CompletableFuture.completedFuture(
        bundled.computeIfAbsent(resource(id).toExternalForm(), Image::new));
  }

  public ImageView view(String id, double size) {
    var v = new ImageView(bundled.computeIfAbsent(resource(null).toExternalForm(), Image::new));
    v.setFitWidth(size);
    v.setFitHeight(size);
    v.setPreserveRatio(true);
    load(id)
        .whenComplete(
            (value, error) ->
                Platform.runLater(
                    () -> {
                      if (error == null) v.setImage(value);
                      else {
                        var tip =
                            new javafx.scene.control.Tooltip("Không tải được ảnh · bấm để thử lại");
                        javafx.scene.control.Tooltip.install(v, tip);
                        v.setOnMouseClicked(
                            e ->
                                load(id)
                                    .thenAccept(
                                        image -> Platform.runLater(() -> v.setImage(image))));
                      }
                    }));
    return v;
  }
}
