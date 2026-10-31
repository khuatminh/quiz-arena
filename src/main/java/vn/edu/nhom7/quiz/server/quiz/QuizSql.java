package vn.edu.nhom7.quiz.server.quiz;

import java.sql.*;

/** Parameter binding and generated IDs shared by draft storage and publication. */
final class QuizSql {
  private QuizSql() {}

  static void bind(PreparedStatement statement, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
  }

  static int update(Connection connection, String sql, Object... args) throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      bind(statement, args);
      return statement.executeUpdate();
    }
  }

  static long insert(Connection connection, String sql, Object... args) throws SQLException {
    try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      bind(statement, args);
      statement.executeUpdate();
      try (var rows = statement.getGeneratedKeys()) {
        if (!rows.next()) throw new SQLException("No generated ID");
        return rows.getLong(1);
      }
    }
  }
}
