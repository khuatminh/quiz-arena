package vn.edu.nhom7.quiz.server.auth;

import java.util.*;

public final class AccountValidator {
  private AccountValidator() {}

  public static String username(String value) {
    if (value == null) throw new IllegalArgumentException("Username required");
    String normalized = value.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9_.]{3,24}"))
      throw new IllegalArgumentException(
          "Username must contain 3–24 ASCII letters, digits, underscore or dot");
    return normalized;
  }

  public static String displayName(String value, String username) {
    String result = value == null || value.isBlank() ? username : value.strip();
    int n = result.codePointCount(0, result.length());
    if (n < 2 || n > 32 || result.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(
          "Display name must contain 2–32 characters without controls");
    return result;
  }

  public static String password(String value) {
    if (value == null || value.length() < 8 || value.length() > 128)
      throw new IllegalArgumentException("Password must contain 8–128 characters");
    return value;
  }

  public static String avatar(String value, Set<String> allowed) {
    if (value == null || !allowed.contains(value))
      throw new IllegalArgumentException("Unknown avatar");
    return value;
  }
}
