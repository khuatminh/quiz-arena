package vn.edu.nhom7.quiz.server.db;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.nhom7.quiz.server.config.ServerConfig;
import vn.edu.nhom7.quiz.server.support.TestDatabase;

class ServerConfigTest {
  @TempDir Path temp;

  @Test
  void environmentOverridesLocalWithoutLeakingSecrets() throws Exception {
    var path = temp.resolve("server.properties");
    Files.writeString(
        path, "db.url=jdbc:mysql://localhost/local\ndb.username=local\ndb.password=secret\n");
    var c =
        ServerConfig.load(
            Map.of(
                "QUIZ_DB_URL",
                "jdbc:mysql://localhost/override",
                "QUIZ_DB_USER",
                "env",
                "QUIZ_DB_PASSWORD",
                "private"),
            path);
    assertEquals("env", c.dbUser());
    assertEquals("private", c.dbPassword());
    assertFalse(c.toString().contains("private"));
    assertFalse(c.toString().contains("jdbc:"));
  }

  @Test
  void rejectsUnsafeTestSchema() {
    for (var url :
        List.of(
            "jdbc:mysql://localhost/prod",
            "jdbc:mysql://user:pass@localhost/quiz_test",
            "jdbc:postgres://localhost/quiz_test",
            "jdbc:mysql://localhost/a_test/other"))
      assertThrows(IllegalArgumentException.class, () -> TestDatabase.validate(url));
    TestDatabase.validate("jdbc:mysql://127.0.0.1:13306/quiz_arena_test");
    assertThrows(
        IllegalStateException.class,
        () ->
            TestDatabase.validateSeparate(
                "jdbc:mysql://localhost/quiz_test?useSSL=false",
                "jdbc:mysql://127.0.0.1:3306/quiz_test?useSSL=true"));
  }
}
