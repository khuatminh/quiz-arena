package vn.edu.nhom7.quiz.server.media;

import java.sql.*;
import java.util.Optional;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;

public final class JdbcMediaStore implements MediaService.MetadataStore {
  private final ConnectionFactory connections;

  public JdbcMediaStore(ConnectionFactory connections) {
    this.connections = connections;
  }

  public void save(MediaService.Metadata m) {
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "INSERT INTO MEDIA_ASSETS(id,owner_id,mime_type,byte_size,sha256,created_at)"
                    + " VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))")) {
      p.setString(1, m.id());
      p.setLong(2, m.ownerId());
      p.setString(3, m.mimeType());
      p.setInt(4, m.byteSize());
      p.setString(5, m.sha256());
      p.executeUpdate();
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  public Optional<MediaService.Metadata> find(String id) {
    try (var c = connections.open();
        var p = c.prepareStatement("SELECT * FROM MEDIA_ASSETS WHERE id=?")) {
      p.setString(1, id);
      try (var r = p.executeQuery()) {
        return r.next()
            ? Optional.of(
                new MediaService.Metadata(
                    r.getString("id"),
                    r.getLong("owner_id"),
                    r.getString("mime_type"),
                    r.getInt("byte_size"),
                    r.getString("sha256")))
            : Optional.empty();
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }
}
