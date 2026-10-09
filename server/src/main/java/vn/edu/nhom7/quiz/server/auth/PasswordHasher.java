package vn.edu.nhom7.quiz.server.auth;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
  private static final int ITERATIONS = 600_000;

  public String hash(char[] password) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return "v1$pbkdf2-sha256$600000$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt));
  }

  public boolean verify(char[] password, String encoded) {
    try {
      var parts = encoded.split("\\$", -1);
      if (parts.length != 5
          || !parts[0].equals("v1")
          || !parts[1].equals("pbkdf2-sha256")
          || !parts[2].equals("600000")) return false;
      byte[] salt = Base64.getDecoder().decode(parts[3]),
          key = Base64.getDecoder().decode(parts[4]);
      return salt.length == 16
          && key.length == 32
          && MessageDigest.isEqual(key, derive(password, salt));
    } catch (IllegalArgumentException | NullPointerException e) {
      return false;
    }
  }

  private byte[] derive(char[] password, byte[] salt) {
    var spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Password hashing unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
