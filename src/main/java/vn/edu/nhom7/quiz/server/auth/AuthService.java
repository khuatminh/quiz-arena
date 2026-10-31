package vn.edu.nhom7.quiz.server.auth;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;

public final class AuthService {
  private final JdbcUserRepository users;
  private final PasswordHasher hasher;
  private final Set<String> avatars;
  private final String dummy;

  public AuthService(JdbcUserRepository users, PasswordHasher hasher, Set<String> avatars) {
    this.users = users;
    this.hasher = hasher;
    this.avatars = Set.copyOf(avatars);
    dummy = hasher.hash("dummy-password-unavailable".toCharArray());
  }

  public Payloads.Profile register(
      String username, String displayName, String password, String avatarId) {
    try {
      String name = AccountValidator.username(username);
      AccountValidator.password(password);
      return users.insert(
          name,
          AccountValidator.displayName(displayName, name),
          AccountValidator.avatar(avatarId, avatars),
          hasher.hash(password.toCharArray()));
    } catch (IllegalArgumentException e) {
      throw new ServiceException("INVALID_INPUT", e.getMessage());
    }
  }

  public Payloads.Profile login(String username, String password) {
    String canonical;
    try {
      canonical = AccountValidator.username(username);
      AccountValidator.password(password);
    } catch (IllegalArgumentException e) {
      throw invalid();
    }
    var found = users.find(canonical);
    boolean valid =
        hasher.verify(
            password.toCharArray(),
            found.map(JdbcUserRepository.Account::passwordHash).orElse(dummy));
    if (!valid || found.isEmpty()) throw invalid();
    return found.get().profile();
  }

  public Payloads.Profile profile(long userId) {
    return users.profile(userId);
  }

  private static ServiceException invalid() {
    return new ServiceException("INVALID_CREDENTIALS", "Invalid username or password");
  }
}
