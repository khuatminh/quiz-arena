package vn.edu.nhom7.quiz.server.db;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.quiz.JdbcQuizRepository;
import vn.edu.nhom7.quiz.server.support.TestDatabase;

class StartupChecksIT {
  @Test
  void configuredDatabaseAndSeedAreReady() {
    var c = TestDatabase.factory();
    new StartupValidator(c).verify();
    var quizzes = new JdbcQuizRepository(c).list(null, 1, 50);
    assertTrue(quizzes.items().size() >= 3);
    assertTrue(quizzes.items().stream().allMatch(q -> q.availability().equals("AVAILABLE")));
  }

  @Test
  void unavailableDatabaseCannotPassStartup() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new SchemaVerifier(
                    () -> {
                      throw new SQLException("offline secret");
                    })
                .verify());
  }
}
