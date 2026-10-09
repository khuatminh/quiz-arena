package vn.edu.nhom7.quiz.server.persistence;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.*;
import vn.edu.nhom7.quiz.server.domain.*;

public final class JdbcMatchRepository {
  private final ConnectionFactory connections;
  private final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final FaultInjector faults;

  @FunctionalInterface
  public interface FaultInjector {
    void at(String point) throws SQLException;
  }

  public JdbcMatchRepository(ConnectionFactory c) {
    this(c, point -> {});
  }

  public JdbcMatchRepository(ConnectionFactory c, FaultInjector f) {
    connections = c;
    faults = f;
  }

  public SaveResult save(MatchSummary summary) {
    validate(summary);
    try (var c = connections.open()) {
      c.setAutoCommit(false);
      try {
        var existing = load(c, summary.matchId());
        if (existing != null) {
          if (!projection(existing).equals(projection(summary)))
            throw new ServiceException("INTERNAL_ERROR", "Conflicting immutable match summary");
          c.rollback();
          return SaveResult.ALREADY_SAVED;
        }
        insert(c, summary);
        faults.at("before_commit");
        c.commit();
        faults.at("after_commit");
        return SaveResult.SAVED;
      } catch (SQLException | RuntimeException e) {
        try {
          c.rollback();
        } catch (SQLException ignored) {
        }
        if (e instanceof SQLException sql && sql.getErrorCode() == 1062)
          return existingResult(summary);
        throw e;
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private SaveResult existingResult(MatchSummary s) {
    try (var c = connections.open()) {
      var stored = load(c, s.matchId());
      if (stored != null && projection(stored).equals(projection(s)))
        return SaveResult.ALREADY_SAVED;
      throw new ServiceException("INTERNAL_ERROR", "Conflicting match ID");
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private String projection(MatchSummary s) {
    try {
      var sorted =
          s.rounds().stream()
              .sorted(Comparator.comparingInt(RoundSummary::roundIndex))
              .map(
                  r ->
                      new RoundSummary(
                          r.roundId(),
                          r.roundIndex(),
                          r.question(),
                          r.revealed(),
                          r.outcomes().stream()
                              .sorted(Comparator.comparingLong(StoredAnswerOutcome::userId))
                              .toList()))
              .toList();
      var copy =
          new MatchSummary(
              s.matchId(),
              s.quizId(),
              s.quizTitle(),
              s.player1(),
              s.player2(),
              s.startedAt(),
              s.endedAt(),
              s.finishReason(),
              s.reasonCode(),
              s.outcome(),
              s.completedRounds(),
              s.openedRounds(),
              sorted,
              s.ranked(),
              s.quizVersionId(),
              s.totalRounds());
      var tree = json.valueToTree(copy);
      normalize(tree);
      return tree.toString();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid summary", e);
    }
  }

  private void normalize(JsonNode node) {
    if (node.isObject()) {
      var o = (com.fasterxml.jackson.databind.node.ObjectNode) node;
      for (var name : List.of("startedAt", "endedAt", "receivedAt")) {
        if (o.hasNonNull(name)) {
          var v = o.get(name);
          if (v.isTextual()) o.put(name, Instant.parse(v.asText()).toEpochMilli());
        }
      }
      if (o.has("elapsedNanos") && !o.get("elapsedNanos").isNull())
        o.put("elapsedNanos", o.get("elapsedNanos").asLong() / 1_000_000);
      o.remove("avatarId");
      for (var name : List.of("answerJson", "answerKeyJson")) {
        if (o.hasNonNull(name))
          try {
            o.set(name, json.readTree(o.get(name).asText()));
          } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON");
          }
      }
    }
    for (var child : node) normalize(child);
  }

  private void insert(Connection c, MatchSummary s) throws SQLException {
    String sql =
        "INSERT INTO"
            + " MATCHES(id,quiz_id,quiz_title_snapshot,player1_id,player2_id,player1_name_snapshot,player2_name_snapshot,score1,score2,correct_count1,correct_count2,outcome,finish_reason,reason_code,started_at,ended_at,completed_rounds,opened_rounds,ranked,quiz_version_id,total_rounds)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    try (var p = c.prepareStatement(sql)) {
      int i = 1;
      p.setString(i++, s.matchId().toString());
      p.setLong(i++, s.quizId());
      p.setString(i++, s.quizTitle());
      p.setLong(i++, s.player1().userId());
      p.setLong(i++, s.player2().userId());
      p.setString(i++, s.player1().displayName());
      p.setString(i++, s.player2().displayName());
      p.setInt(i++, s.player1().totalScore());
      p.setInt(i++, s.player2().totalScore());
      p.setInt(i++, s.player1().correctCount());
      p.setInt(i++, s.player2().correctCount());
      p.setString(i++, s.outcome().name());
      p.setString(i++, s.finishReason().name());
      p.setString(i++, s.reasonCode().name());
      p.setTimestamp(
          i++, Timestamp.from(s.startedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
      p.setTimestamp(
          i++, Timestamp.from(s.endedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
      p.setInt(i++, s.completedRounds());
      p.setInt(i++, s.openedRounds());
      p.setBoolean(i++, s.ranked());
      if (s.quizVersionId() == 0) p.setNull(i++, Types.BIGINT);
      else p.setLong(i++, s.quizVersionId());
      p.setInt(i++, s.totalRounds());
      p.executeUpdate();
    }
    for (var round : s.rounds()) {
      try (var p = c.prepareStatement("INSERT INTO MATCH_QUESTIONS VALUES(?,?,?,?,?,?)")) {
        p.setString(1, round.roundId().toString());
        p.setString(2, s.matchId().toString());
        p.setInt(3, round.roundIndex());
        p.setLong(4, round.question().questionId());
        try {
          p.setString(5, json.writeValueAsString(round.question()));
        } catch (Exception e) {
          throw new SQLException("Invalid snapshot", e);
        }
        p.setBoolean(6, round.revealed());
        p.executeUpdate();
      }
      faults.at("after_round");
      try (var p =
          c.prepareStatement(
              "INSERT INTO"
                  + " MATCH_ANSWERS(round_id,user_id,answer_json,answer_time_ms,received_at,outcome,is_correct,earned_points,request_id)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)")) {
        for (var a : round.outcomes()) {
          p.setString(1, round.roundId().toString());
          p.setLong(2, a.userId());
          p.setString(3, a.answerJson());
          if (a.elapsedNanos() == null) p.setNull(4, Types.BIGINT);
          else p.setLong(4, a.elapsedNanos() / 1_000_000);
          p.setTimestamp(
              5,
              a.receivedAt() == null
                  ? null
                  : Timestamp.from(
                      a.receivedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
          p.setString(6, a.outcome().name());
          if (a.correct() == null) p.setNull(7, Types.BOOLEAN);
          else p.setBoolean(7, a.correct());
          p.setInt(8, a.earnedPoints());
          p.setString(9, a.requestId() == null ? null : a.requestId().toString());
          p.addBatch();
        }
        p.executeBatch();
      }
      faults.at("after_answers");
    }
    if (s.ranked() && s.finishReason() != FinishReason.ABORTED) {
      var players = new ArrayList<>(List.of(s.player1(), s.player2()));
      players.sort(Comparator.comparingLong(ParticipantSummary::userId));
      for (var player : players) {
        try (var p = c.prepareStatement("SELECT id FROM USERS WHERE id=? FOR UPDATE")) {
          p.setLong(1, player.userId());
          try (var r = p.executeQuery()) {
            if (!r.next()) throw new SQLException("Unknown participant");
          }
        }
        boolean won =
            s.outcome() == MatchOutcome.PLAYER1_WIN && player.userId() == s.player1().userId()
                || s.outcome() == MatchOutcome.PLAYER2_WIN
                    && player.userId() == s.player2().userId();
        boolean draw = s.outcome() == MatchOutcome.DRAW;
        try (var p =
            c.prepareStatement(
                "UPDATE USERS SET"
                    + " total_score=total_score+?,total_matches=total_matches+1,wins=wins+?,losses=losses+?,draws=draws+?"
                    + " WHERE id=?")) {
          p.setInt(1, player.totalScore());
          p.setInt(2, won ? 1 : 0);
          p.setInt(3, !won && !draw ? 1 : 0);
          p.setInt(4, draw ? 1 : 0);
          p.setLong(5, player.userId());
          p.executeUpdate();
        }
        faults.at("after_user");
      }
    }
  }

  public MatchSummary load(UUID id) {
    try (var c = connections.open()) {
      return load(c, id);
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private MatchSummary load(Connection c, UUID id) throws SQLException {
    try (var p =
        c.prepareStatement(
            "SELECT m.*,u1.avatar_id avatar1,u2.avatar_id avatar2 FROM MATCHES m JOIN USERS u1 ON"
                + " u1.id=m.player1_id JOIN USERS u2 ON u2.id=m.player2_id WHERE m.id=?")) {
      p.setString(1, id.toString());
      try (var r = p.executeQuery()) {
        if (!r.next()) return null;
        var p1 =
            new ParticipantSummary(
                r.getLong("player1_id"),
                r.getString("player1_name_snapshot"),
                r.getString("avatar1"),
                r.getInt("score1"),
                r.getInt("correct_count1"));
        var p2 =
            new ParticipantSummary(
                r.getLong("player2_id"),
                r.getString("player2_name_snapshot"),
                r.getString("avatar2"),
                r.getInt("score2"),
                r.getInt("correct_count2"));
        var rounds = rounds(c, id);
        return new MatchSummary(
            id,
            r.getLong("quiz_id"),
            r.getString("quiz_title_snapshot"),
            p1,
            p2,
            r.getTimestamp("started_at").toInstant(),
            r.getTimestamp("ended_at").toInstant(),
            FinishReason.valueOf(r.getString("finish_reason")),
            ReasonCode.valueOf(r.getString("reason_code")),
            MatchOutcome.valueOf(r.getString("outcome")),
            r.getInt("completed_rounds"),
            r.getInt("opened_rounds"),
            rounds,
            r.getBoolean("ranked"),
            r.getLong("quiz_version_id"),
            r.getInt("total_rounds"));
      }
    }
  }

  private List<RoundSummary> rounds(Connection c, UUID id) throws SQLException {
    var result = new ArrayList<RoundSummary>();
    var totals = new HashMap<Long, Integer>();
    try (var p =
        c.prepareStatement("SELECT * FROM MATCH_QUESTIONS WHERE match_id=? ORDER BY round_index")) {
      p.setString(1, id.toString());
      try (var r = p.executeQuery()) {
        while (r.next()) {
          UUID round = UUID.fromString(r.getString("round_id"));
          var answers = new ArrayList<StoredAnswerOutcome>();
          try (var a =
              c.prepareStatement("SELECT * FROM MATCH_ANSWERS WHERE round_id=? ORDER BY user_id")) {
            a.setString(1, round.toString());
            try (var ar = a.executeQuery()) {
              while (ar.next()) {
                long uid = ar.getLong("user_id");
                int before = totals.getOrDefault(uid, 0), points = ar.getInt("earned_points");
                totals.put(uid, before + points);
                Long elapsed =
                    ar.getObject("answer_time_ms") == null
                        ? null
                        : ar.getLong("answer_time_ms") * 1_000_000;
                Timestamp received = ar.getTimestamp("received_at");
                String req = ar.getString("request_id");
                answers.add(
                    new StoredAnswerOutcome(
                        uid,
                        ar.getString("answer_json"),
                        elapsed,
                        received == null ? null : received.toInstant(),
                        AnswerOutcomeType.valueOf(ar.getString("outcome")),
                        (Boolean) ar.getObject("is_correct"),
                        points,
                        before,
                        before + points,
                        req == null ? null : UUID.fromString(req)));
              }
            }
          }
          try {
            result.add(
                new RoundSummary(
                    round,
                    r.getInt("round_index"),
                    json.readValue(r.getString("question_snapshot_json"), QuestionSnapshot.class),
                    r.getBoolean("revealed"),
                    answers));
          } catch (Exception e) {
            throw new SQLException("Invalid history snapshot", e);
          }
        }
      }
    }
    return List.copyOf(result);
  }

  private void validate(MatchSummary s) {
    if (s.player1().userId() == s.player2().userId()
        || s.totalRounds() < 1
        || s.totalRounds() > 50
        || s.ranked() && s.totalRounds() != 10
        || s.rounds().size() > s.totalRounds()
        || s.openedRounds() != s.rounds().size()
        || s.completedRounds() != s.rounds().stream().filter(RoundSummary::revealed).count()
        || s.startedAt() == null
        || s.endedAt() == null) throw new IllegalArgumentException("Invalid match summary");
    if (s.finishReason() == FinishReason.COMPLETED
        && (s.rounds().size() != s.totalRounds() || s.completedRounds() != s.totalRounds()))
      throw new IllegalArgumentException("Completed match requires all rounds");
    var totals = new HashMap<Long, Integer>();
    var counts = new HashMap<Long, Integer>();
    var indices = new HashSet<Integer>();
    for (var r : s.rounds()) {
      if (r.roundIndex() < 1
          || r.roundIndex() > s.totalRounds()
          || !indices.add(r.roundIndex())
          || r.outcomes().size() != 2) throw new IllegalArgumentException("Invalid rounds");
      var users = new HashSet<Long>();
      for (var a : r.outcomes()) {
        if (!users.add(a.userId())
            || (a.userId() != s.player1().userId() && a.userId() != s.player2().userId()))
          throw new IllegalArgumentException("Invalid participants");
        int before = totals.getOrDefault(a.userId(), 0);
        if (before != a.scoreBefore()
            || a.totalScore() != before + a.earnedPoints()
            || a.earnedPoints() < 0
            || a.earnedPoints() > 3)
          throw new IllegalArgumentException("Inconsistent score projection");
        totals.put(a.userId(), a.totalScore());
        if (Boolean.TRUE.equals(a.correct())) counts.merge(a.userId(), 1, Integer::sum);
      }
    }
    for (var player : List.of(s.player1(), s.player2()))
      if (player.totalScore() != totals.getOrDefault(player.userId(), 0)
          || player.correctCount() != counts.getOrDefault(player.userId(), 0))
        throw new IllegalArgumentException("Final score mismatch");
  }
}
