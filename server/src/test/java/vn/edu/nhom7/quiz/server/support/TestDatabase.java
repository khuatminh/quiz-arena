package vn.edu.nhom7.quiz.server.support;

import java.net.URI;
import java.sql.*;
import vn.edu.nhom7.quiz.server.config.ServerConfig;
import vn.edu.nhom7.quiz.server.db.*;

public final class TestDatabase {
  public static ConnectionFactory factory() {
    String url = System.getenv("QUIZ_TEST_DB_URL"),
        user = System.getenv("QUIZ_TEST_DB_USER"),
        password = System.getenv("QUIZ_TEST_DB_PASSWORD");
    if (url == null || user == null || password == null)
      throw new IllegalStateException(
          "mysql-it requires QUIZ_TEST_DB_URL, QUIZ_TEST_DB_USER, QUIZ_TEST_DB_PASSWORD with a"
              + " dedicated *_test schema; see docs/operations/database-setup.md");
    validate(url);
    validateSeparate(url, System.getenv("QUIZ_DB_URL"));
    return new JdbcConnectionFactory(
        new ServerConfig(url, user, password == null ? "" : password, 5555));
  }

  public static void validate(String url) {
    try {
      var uri = URI.create(url.substring(5));
      if (!"mysql".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || !uri.getPath().matches("/[A-Za-z0-9_]+_test")) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Integration database URL must address a dedicated *_test schema");
    }
  }

  public static void validateSeparate(String testUrl, String runtimeUrl) {
    if (runtimeUrl == null) return;
    try {
      var t = URI.create(testUrl.substring(5));
      var r = URI.create(runtimeUrl.substring(5));
      String th = loopback(t.getHost()), rh = loopback(r.getHost());
      if (java.util.Objects.equals(th, rh)
          && (t.getPort() < 0 ? 3306 : t.getPort()) == (r.getPort() < 0 ? 3306 : r.getPort())
          && java.util.Objects.equals(t.getPath(), r.getPath()))
        throw new IllegalStateException("Test database must be separate from runtime database");
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Cannot validate runtime/test database separation");
    }
  }

  private static String loopback(String host) {
    return host != null
            && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("[::1]"))
        ? "loopback"
        : host;
  }
}
