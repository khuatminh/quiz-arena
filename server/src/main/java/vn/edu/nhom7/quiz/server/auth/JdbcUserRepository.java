package vn.edu.nhom7.quiz.server.auth;

import java.sql.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;

public final class JdbcUserRepository {
  private final ConnectionFactory connections;

  public JdbcUserRepository(ConnectionFactory c) {
    connections = c;
  }

  public record Account(Payloads.Profile profile, String passwordHash) {}

  public Optional<Account> find(String username) {
    try (var c = connections.open();
        var p = c.prepareStatement("SELECT * FROM USERS WHERE username=?")) {
      p.setString(1, username);
      try (var r = p.executeQuery()) {
        return r.next()
            ? Optional.of(new Account(profile(r), r.getString("password_hash")))
            : Optional.empty();
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  public Payloads.Profile profile(long userId) {
    try (var c = connections.open();
        var p = c.prepareStatement("SELECT * FROM USERS WHERE id=?")) {
      p.setLong(1, userId);
      try (var r = p.executeQuery()) {
        if (!r.next())
          throw new ServiceException("INVALID_CREDENTIALS", "Invalid username or password");
        return profile(r);
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  public Payloads.Profile insert(String username, String name, String avatar, String hash) {
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "INSERT INTO USERS(username,display_name,avatar_id,password_hash,created_at)"
                    + " VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
                Statement.RETURN_GENERATED_KEYS)) {
      p.setString(1, username);
      p.setString(2, name);
      p.setString(3, avatar);
      p.setString(4, hash);
      p.executeUpdate();
      try (var r = p.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("Missing generated key");
        return new Payloads.Profile(r.getLong(1), username, name, avatar, 0, 0, 0, 0, 0);
      }
    } catch (SQLException e) {
      if (e.getErrorCode() == 1062)
        throw new ServiceException("USERNAME_TAKEN", "Username already taken");
      throw ServiceException.database();
    }
  }

  private static Payloads.Profile profile(ResultSet r) throws SQLException {
    return new Payloads.Profile(
        r.getLong("id"),
        r.getString("username"),
        r.getString("display_name"),
        r.getString("avatar_id"),
        r.getLong("total_score"),
        r.getInt("total_matches"),
        r.getInt("wins"),
        r.getInt("losses"),
        r.getInt("draws"));
  }
}
