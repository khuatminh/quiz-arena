package vn.edu.nhom7.quiz.server.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AccountValidatorTest {
  @Test
  void canonicalizesUsernameAndCountsCodepoints() {
    assertEquals("minh_1", AccountValidator.username("MiNh_1"));
    assertEquals("Minh", AccountValidator.displayName(" Minh ", "minh"));
    assertThrows(IllegalArgumentException.class, () -> AccountValidator.username("x';--"));
    assertThrows(IllegalArgumentException.class, () -> AccountValidator.displayName("a\nb", "abc"));
    assertThrows(IllegalArgumentException.class, () -> AccountValidator.password("short"));
  }
}
