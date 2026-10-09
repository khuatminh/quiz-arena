package vn.edu.nhom7.quiz.server.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public record ServerConfig(String dbUrl, String dbUser, String dbPassword, int port) {
  public ServerConfig {
    if (dbUrl == null || !dbUrl.startsWith("jdbc:mysql://") || dbUser == null || dbUser.isBlank())
      throw new IllegalArgumentException(
          "Configure QUIZ_DB_URL and QUIZ_DB_USER; see docs/operations/database-setup.md");
    if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid server port");
  }

  public static ServerConfig load() {
    return load(System.getenv(), Path.of("config/server.local.properties"));
  }

  public static ServerConfig load(Map<String, String> env, Path path) {
    var p = new Properties();
    if (Files.exists(path))
      try (var in = Files.newInputStream(path)) {
        p.load(in);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot read local server configuration");
      }
    return new ServerConfig(
        value(env, p, "QUIZ_DB_URL", "db.url", null),
        value(env, p, "QUIZ_DB_USER", "db.user", p.getProperty("db.username")),
        value(env, p, "QUIZ_DB_PASSWORD", "db.password", ""),
        Integer.parseInt(value(env, p, "QUIZ_PORT", "server.port", "5555")));
  }

  private static String value(
      Map<String, String> e, Properties p, String name, String key, String fallback) {
    return e.containsKey(name) ? e.get(name) : p.getProperty(key, fallback);
  }

  @Override
  public String toString() {
    return "ServerConfig[dbUser=" + dbUser + ", port=" + port + ", credentials=redacted]";
  }
}
