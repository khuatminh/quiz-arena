package vn.edu.nhom7.quiz.server.db;

import java.sql.*;
import java.util.Properties;
import vn.edu.nhom7.quiz.server.config.ServerConfig;

public final class JdbcConnectionFactory implements ConnectionFactory {
  private final ServerConfig config;

  public JdbcConnectionFactory(ServerConfig config) {
    this.config = config;
  }

  public Connection open() throws SQLException {
    var p = new Properties();
    p.setProperty("user", config.dbUser());
    p.setProperty("password", config.dbPassword());
    p.setProperty("connectTimeout", "5000");
    p.setProperty("socketTimeout", "5000");
    p.setProperty("connectionTimeZone", "UTC");
    p.setProperty("forceConnectionTimeZoneToSession", "true");
    Connection c = DriverManager.getConnection(config.dbUrl(), p);
    try (var s = c.createStatement()) {
      s.execute("SET time_zone = '+00:00'");
    } catch (SQLException e) {
      c.close();
      throw e;
    }
    return c;
  }
}
