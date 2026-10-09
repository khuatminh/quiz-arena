package vn.edu.nhom7.quiz.server.db;

import java.sql.*;

public final class SchemaVerifier {
  private final ConnectionFactory connections;

  public SchemaVerifier(ConnectionFactory c) {
    connections = c;
  }

  public void verify() {
    try (var c = connections.open()) {
      for (String table :
          new String[] {
            "USERS",
            "CATEGORIES",
            "QUIZZES",
            "QUESTIONS",
            "MATCHES",
            "MATCH_QUESTIONS",
            "MATCH_ANSWERS"
          })
        try (var p = c.prepareStatement("SELECT 1 FROM " + table + " LIMIT 0")) {
          p.executeQuery().close();
        }
      for (var pair :
          new String[][] {
            {"USERS", "idx_users_ranking"},
            {"QUESTIONS", "idx_questions_available"},
            {"MATCHES", "idx_matches_p1"},
            {"MATCHES", "idx_matches_p2"},
            {"MATCH_QUESTIONS", "uq_match_round"},
            {"MATCH_ANSWERS", "uq_round_user"}
          }) {
        boolean found = false;
        try (var indices =
            c.getMetaData().getIndexInfo(c.getCatalog(), null, pair[0], false, false)) {
          while (indices.next())
            if (pair[1].equalsIgnoreCase(indices.getString("INDEX_NAME"))) found = true;
        }
        if (!found) throw new SQLException("Required index missing");
      }
      try (var p = c.prepareStatement("SELECT version,asset_pack_version FROM SCHEMA_METADATA");
          var r = p.executeQuery()) {
        if (!r.next()
            || r.getInt(1) != 1
            || !r.getString(2)
                .equals(vn.edu.nhom7.quiz.common.assets.AssetRegistry.loadDefault().version()))
          throw new SQLException("Schema version mismatch");
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Database schema unavailable or incompatible; apply database/001_schema.sql and"
              + " database/002_seed.sql");
    }
  }
}
