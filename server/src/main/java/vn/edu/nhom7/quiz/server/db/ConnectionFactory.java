package vn.edu.nhom7.quiz.server.db;

import java.sql.*;

@FunctionalInterface
public interface ConnectionFactory {
  Connection open() throws SQLException;
}
