package vn.edu.nhom7.quiz.server.db;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.server.support.TestDatabase;

class SchemaIT {
  @Test
  void countersAndSevenBusinessTablesAreConstrained() throws Exception {
    var factory = TestDatabase.factory();
    try (var c = factory.open();
        var p =
            c.prepareStatement(
                "INSERT INTO"
                    + " USERS(username,display_name,avatar_id,password_hash,total_matches,wins,created_at)"
                    + " VALUES(?,?,?,?,2,1,UTC_TIMESTAMP(3))")) {
      p.setString(1, "invalid_" + System.nanoTime());
      p.setString(2, "Test");
      p.setString(3, "avatar-01");
      p.setString(4, "unused");
      assertThrows(java.sql.SQLException.class, p::executeUpdate);
    }
    try (var c = factory.open();
        var p =
            c.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                    + " table_name IN"
                    + " ('USERS','CATEGORIES','QUIZZES','QUESTIONS','MATCHES','MATCH_QUESTIONS','MATCH_ANSWERS')"
                    + " AND table_collation LIKE 'utf8mb4%'");
        var r = p.executeQuery()) {
      r.next();
      assertEquals(7, r.getInt(1));
    }
  }

  @Test
  void configuredSchemaIsRequired() {
    new SchemaVerifier(TestDatabase.factory()).verify();
  }

  @Test
  void refusesUnsafeSchema() {
    assertThrows(
        IllegalArgumentException.class, () -> TestDatabase.validate("jdbc:mysql://localhost/quiz"));
  }
}
