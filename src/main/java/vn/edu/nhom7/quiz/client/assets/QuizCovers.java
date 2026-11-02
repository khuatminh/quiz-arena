package vn.edu.nhom7.quiz.client.assets;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.Locale;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.ImageView;
import vn.edu.nhom7.quiz.common.assets.AssetRegistry;

/** Bundled topic photos also cover quizzes published without a cover asset. */
public final class QuizCovers {
  private static final AssetRegistry registry = AssetRegistry.loadDefault();

  private QuizCovers() {}

  public static String assetId(JsonNode quiz) {
    String explicit = quiz.path("coverAssetId").asText("");
    if (explicit.startsWith("media-") || registry.contains(explicit) && !explicit.equals("placeholder"))
      return explicit;

    String title = normalize(quiz.path("title").asText(""));
    if (title.contains("mang may tinh")) return "quiz-cover-networks";
    if (title.matches(".*\\bjava\\b.*")) return "quiz-cover-java";
    if (title.contains("toan") || title.contains("logic")) return "quiz-cover-math";
    if (title.contains("muon thu") || title.contains("dong vat")) return "quiz-cover-animals";
    if (title.contains("do vui")) return "quiz-cover-riddles";
    if (title.contains("tap hoa")) return "quiz-cover-everyday";
    if (title.contains("the gioi") || title.contains("du lich")) return "quiz-cover-world";

    String category = normalize(quiz.path("categoryName").asText(""));
    if (category.contains("khoa hoc") || quiz.path("categoryId").asInt() == 1)
      return "quiz-cover-science";
    if (category.contains("lich su") || quiz.path("categoryId").asInt() == 2)
      return "quiz-cover-history";
    if (category.contains("cong nghe") || quiz.path("categoryId").asInt() == 3)
      return "quiz-cover-technology";
    return "quiz-cover-general";
  }

  /** Center-crop to fill each slot without stretching the photo. */
  public static ImageView view(JsonNode quiz, double width, double height) {
    var view = new AssetLoader().view(assetId(quiz), width);
    view.setFitHeight(height);
    view.setAccessibleText("Ảnh bìa: " + quiz.path("title").asText());
    Runnable crop =
        () -> {
          var image = view.getImage();
          if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
          double cropWidth = Math.min(image.getWidth(), image.getHeight() * width / height);
          double cropHeight = cropWidth * height / width;
          view.setViewport(
              new Rectangle2D(
                  (image.getWidth() - cropWidth) / 2,
                  (image.getHeight() - cropHeight) / 2,
                  cropWidth,
                  cropHeight));
        };
    view.imageProperty().addListener((observable, oldImage, newImage) -> crop.run());
    crop.run();
    return view;
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replace('đ', 'd');
  }
}
