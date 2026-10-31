package vn.edu.nhom7.quiz.server.match;

import java.text.Normalizer;
import java.util.Locale;

public final class ShortAnswerNormalizer {
  private ShortAnswerNormalizer() {}

  public static String normalize(String input) {
    String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
    StringBuilder out = new StringBuilder();
    boolean pending = false;
    for (int cp : nfc.codePoints().toArray()) {
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
        pending = out.length() > 0;
      } else {
        if (pending) out.append(' ');
        out.appendCodePoint(cp);
        pending = false;
      }
    }
    return out.toString().toLowerCase(Locale.ROOT);
  }
}
