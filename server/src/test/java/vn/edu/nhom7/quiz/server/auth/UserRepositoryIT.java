package vn.edu.nhom7.quiz.server.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.support.*;

class UserRepositoryIT {
  @Test
  void registerLoginDuplicateAndInjection() {
    var auth =
        new AuthService(
            new JdbcUserRepository(TestDatabase.factory()),
            new PasswordHasher(),
            Set.of("avatar-1"));
    String name = "t_" + UUID.randomUUID().toString().substring(0, 12).replace('-', '_');
    var p = auth.register(name, null, "password123", "avatar-1");
    assertEquals(p, auth.login(name.toUpperCase(Locale.ROOT), "password123"));
    assertEquals(
        "USERNAME_TAKEN",
        assertThrows(
                ServiceException.class,
                () -> auth.register(name, "Name", "password123", "avatar-1"))
            .code());
    assertEquals(
        "INVALID_CREDENTIALS",
        assertThrows(ServiceException.class, () -> auth.login("a' OR 1=1 --", "password123"))
            .code());
    assertEquals(
        "INVALID_CREDENTIALS",
        assertThrows(ServiceException.class, () -> auth.login(name, "wrongpass")).code());
  }
}
