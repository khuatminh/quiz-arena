package vn.edu.nhom7.quiz.server.query;

import java.sql.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;

public final class RankingService {
  private final ConnectionFactory connections;

  public RankingService(ConnectionFactory c) {
    connections = c;
  }

  public Payloads.Ranking ranking(long requester, int page, int pageSize) {
    int pg = Math.max(1, page), sz = pageSize < 1 ? 20 : Math.min(50, pageSize);
    var rows = new ArrayList<Payloads.RankingEntry>();
    Payloads.RankingEntry mine = null;
    long total = 0, offset = (long) (pg - 1) * sz;
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "WITH ranked AS (SELECT"
                    + " id,display_name,avatar_id,total_score,wins,total_matches,RANK() OVER(ORDER"
                    + " BY total_score DESC,wins DESC) rank_value,ROW_NUMBER() OVER(ORDER BY"
                    + " total_score DESC,wins DESC,id ASC) row_value,COUNT(*) OVER() total FROM"
                    + " USERS) SELECT * FROM ranked WHERE (row_value>? AND row_value<=?) OR id=?"
                    + " ORDER BY row_value")) {
      p.setLong(1, offset);
      p.setLong(2, offset + sz);
      p.setLong(3, requester);
      try (var r = p.executeQuery()) {
        while (r.next()) {
          total = r.getLong("total");
          var entry =
              new Payloads.RankingEntry(
                  r.getInt("rank_value"),
                  r.getLong("id"),
                  r.getString("display_name"),
                  r.getString("avatar_id"),
                  r.getLong("total_score"),
                  r.getInt("wins"),
                  r.getInt("total_matches"));
          if (entry.userId() == requester) mine = entry;
          long row = r.getLong("row_value");
          if (row > offset && row <= offset + sz) rows.add(entry);
        }
      }
      return new Payloads.Ranking(pg, sz, total, List.copyOf(rows), mine);
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }
}
