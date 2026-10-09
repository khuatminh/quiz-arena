package vn.edu.nhom7.quiz.server.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
  @Test
  void saltedHashPreservesWhitespace() {
    var h = new PasswordHasher();
    var first = h.hash(" Abc12345 ".toCharArray());
    assertNotEquals(first, h.hash(" Abc12345 ".toCharArray()));
    assertTrue(h.verify(" Abc12345 ".toCharArray(), first));
    assertFalse(h.verify("Abc12345".toCharArray(), first));
  }

  @Test
  void malformedEncodingIsRejected() {
    var h = new PasswordHasher();
    for (var s : new String[] {"", "v2$pbkdf2-sha256$600000$a$b", "v1$pbkdf2-sha256$999999999$a$b"})
      assertFalse(h.verify("12345678".toCharArray(), s));
  }
}
