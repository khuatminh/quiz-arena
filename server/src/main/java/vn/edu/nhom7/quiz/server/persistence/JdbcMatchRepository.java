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

  public JdbcMatchRepository(ConnectionFactory connection) {
    this(connection, point -> {});
  }

  public JdbcMatchRepository(ConnectionFactory connection, FaultInjector f) {
    connections = connection;
    faults = f;
  }

  public SaveResult save(MatchSummary summary) {
    MatchSummaryValidator.validate(summary);
    try (var connection = connections.open()) {
      connection.setAutoCommit(false);
      try {
        var existing = load(connection, summary.matchId());
        if (existing != null) {
          if (!MatchSummaryProjection.of(existing).equals(MatchSummaryProjection.of(summary)))
            throw new ServiceException("INTERNAL_ERROR", "Conflicting immutable match summary");
          connection.rollback();
          return SaveResult.ALREADY_SAVED;
        }
        insert(connection, summary);
        faults.at("before_commit");
        connection.commit();
        faults.at("after_commit");
        return SaveResult.SAVED;
      } catch (SQLException | RuntimeException e) {
        try {
          connection.rollback();
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

  private SaveResult existingResult(MatchSummary summary) {
    try (var connection = connections.open()) {
      var stored = load(connection, summary.matchId());
      if (stored != null
          && MatchSummaryProjection.of(stored).equals(MatchSummaryProjection.of(summary)))
        return SaveResult.ALREADY_SAVED;
      throw new ServiceException("INTERNAL_ERROR", "Conflicting match ID");
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private void insert(Connection connection, MatchSummary summary) throws SQLException {
    insertMatch(connection, summary);
    for (var round : summary.rounds()) {
      insertRound(connection, summary, round);
      faults.at("after_round");
      insertAnswers(connection, round);
      faults.at("after_answers");
    }
    updateRankedProfiles(connection, summary);
  }

  private void insertMatch(Connection connection, MatchSummary summary) throws SQLException {
    String sql =
        "INSERT INTO"
            + " MATCHES(id,quiz_id,quiz_title_snapshot,player1_id,player2_id,player1_name_snapshot,player2_name_snapshot,score1,score2,correct_count1,correct_count2,outcome,finish_reason,reason_code,started_at,ended_at,completed_rounds,opened_rounds,ranked,quiz_version_id,total_rounds)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    try (var statement = connection.prepareStatement(sql)) {
      int i = 1;
      statement.setString(i++, summary.matchId().toString());
      statement.setLong(i++, summary.quizId());
      statement.setString(i++, summary.quizTitle());
      statement.setLong(i++, summary.player1().userId());
      statement.setLong(i++, summary.player2().userId());
      statement.setString(i++, summary.player1().displayName());
      statement.setString(i++, summary.player2().displayName());
      statement.setInt(i++, summary.player1().totalScore());
      statement.setInt(i++, summary.player2().totalScore());
      statement.setInt(i++, summary.player1().correctCount());
      statement.setInt(i++, summary.player2().correctCount());
      statement.setString(i++, summary.outcome().name());
      statement.setString(i++, summary.finishReason().name());
      statement.setString(i++, summary.reasonCode().name());
      statement.setTimestamp(
          i++,
          Timestamp.from(summary.startedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
      statement.setTimestamp(
          i++, Timestamp.from(summary.endedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
      statement.setInt(i++, summary.completedRounds());
      statement.setInt(i++, summary.openedRounds());
      statement.setBoolean(i++, summary.ranked());
      if (summary.quizVersionId() == 0) statement.setNull(i++, Types.BIGINT);
      else statement.setLong(i++, summary.quizVersionId());
      statement.setInt(i++, summary.totalRounds());
      statement.executeUpdate();
    }
  }

  private void insertRound(Connection connection, MatchSummary summary, RoundSummary round)
      throws SQLException {
    try (var statement =
        connection.prepareStatement("INSERT INTO MATCH_QUESTIONS VALUES(?,?,?,?,?,?)")) {
      statement.setString(1, round.roundId().toString());
      statement.setString(2, summary.matchId().toString());
      statement.setInt(3, round.roundIndex());
      statement.setLong(4, round.question().questionId());
      try {
        statement.setString(5, json.writeValueAsString(round.question()));
      } catch (Exception e) {
        throw new SQLException("Invalid snapshot", e);
      }
      statement.setBoolean(6, round.revealed());
      statement.executeUpdate();
    }
  }

  private void insertAnswers(Connection connection, RoundSummary round) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO"
                + " MATCH_ANSWERS(round_id,user_id,answer_json,answer_time_ms,received_at,outcome,is_correct,earned_points,request_id)"
                + " VALUES(?,?,?,?,?,?,?,?,?)")) {
      for (var answer : round.outcomes()) {
        statement.setString(1, round.roundId().toString());
        statement.setLong(2, answer.userId());
        statement.setString(3, answer.answerJson());
        if (answer.elapsedNanos() == null) statement.setNull(4, Types.BIGINT);
        else statement.setLong(4, answer.elapsedNanos() / 1_000_000);
        statement.setTimestamp(
            5,
            answer.receivedAt() == null
                ? null
                : Timestamp.from(
                    answer.receivedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
        statement.setString(6, answer.outcome().name());
        if (answer.correct() == null) statement.setNull(7, Types.BOOLEAN);
        else statement.setBoolean(7, answer.correct());
        statement.setInt(8, answer.earnedPoints());
        statement.setString(9, answer.requestId() == null ? null : answer.requestId().toString());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void updateRankedProfiles(Connection connection, MatchSummary summary)
      throws SQLException {
    if (summary.ranked() && summary.finishReason() != FinishReason.ABORTED) {
      var players = new ArrayList<>(List.of(summary.player1(), summary.player2()));
      players.sort(Comparator.comparingLong(ParticipantSummary::userId));
      for (var player : players) {
        try (var statement =
            connection.prepareStatement("SELECT id FROM USERS WHERE id=? FOR UPDATE")) {
          statement.setLong(1, player.userId());
          try (var round = statement.executeQuery()) {
            if (!round.next()) throw new SQLException("Unknown participant");
          }
        }
        boolean won =
            summary.outcome() == MatchOutcome.PLAYER1_WIN
                    && player.userId() == summary.player1().userId()
                || summary.outcome() == MatchOutcome.PLAYER2_WIN
                    && player.userId() == summary.player2().userId();
        boolean draw = summary.outcome() == MatchOutcome.DRAW;
        try (var statement =
            connection.prepareStatement(
                "UPDATE USERS SET"
                    + " total_score=total_score+?,total_matches=total_matches+1,wins=wins+?,losses=losses+?,draws=draws+?"
                    + " WHERE id=?")) {
          statement.setInt(1, player.totalScore());
          statement.setInt(2, won ? 1 : 0);
          statement.setInt(3, !won && !draw ? 1 : 0);
          statement.setInt(4, draw ? 1 : 0);
          statement.setLong(5, player.userId());
          statement.executeUpdate();
        }
        faults.at("after_user");
      }
    }
  }

  public MatchSummary load(UUID id) {
    try (var connection = connections.open()) {
      return load(connection, id);
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private MatchSummary load(Connection connection, UUID id) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT m.*,u1.avatar_id avatar1,u2.avatar_id avatar2 FROM MATCHES m JOIN USERS u1 ON"
                + " u1.id=m.player1_id JOIN USERS u2 ON u2.id=m.player2_id WHERE m.id=?")) {
      statement.setString(1, id.toString());
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) return null;
        var p1 =
            new ParticipantSummary(
                rows.getLong("player1_id"),
                rows.getString("player1_name_snapshot"),
                rows.getString("avatar1"),
                rows.getInt("score1"),
                rows.getInt("correct_count1"));
        var p2 =
            new ParticipantSummary(
                rows.getLong("player2_id"),
                rows.getString("player2_name_snapshot"),
                rows.getString("avatar2"),
                rows.getInt("score2"),
                rows.getInt("correct_count2"));
        var rounds = rounds(connection, id);
        return new MatchSummary(
            id,
            rows.getLong("quiz_id"),
            rows.getString("quiz_title_snapshot"),
            p1,
            p2,
            rows.getTimestamp("started_at").toInstant(),
            rows.getTimestamp("ended_at").toInstant(),
            FinishReason.valueOf(rows.getString("finish_reason")),
            ReasonCode.valueOf(rows.getString("reason_code")),
            MatchOutcome.valueOf(rows.getString("outcome")),
            rows.getInt("completed_rounds"),
            rows.getInt("opened_rounds"),
            rounds,
            rows.getBoolean("ranked"),
            rows.getLong("quiz_version_id"),
            rows.getInt("total_rounds"));
      }
    }
  }

  private List<RoundSummary> rounds(Connection connection, UUID id) throws SQLException {
    var result = new ArrayList<RoundSummary>();
    var totals = new HashMap<Long, Integer>();
    try (var statement =
        connection.prepareStatement(
            "SELECT * FROM MATCH_QUESTIONS WHERE match_id=? ORDER BY round_index")) {
      statement.setString(1, id.toString());
      try (var rows = statement.executeQuery()) {
        while (rows.next()) {
          UUID roundId = UUID.fromString(rows.getString("round_id"));
          var answers = new ArrayList<StoredAnswerOutcome>();
          try (var answer =
              connection.prepareStatement(
                  "SELECT * FROM MATCH_ANSWERS WHERE round_id=? ORDER BY user_id")) {
            answer.setString(1, roundId.toString());
            try (var answerRows = answer.executeQuery()) {
              while (answerRows.next()) {
                long uid = answerRows.getLong("user_id");
                int before = totals.getOrDefault(uid, 0),
                    points = answerRows.getInt("earned_points");
                totals.put(uid, before + points);
                Long elapsed =
                    answerRows.getObject("answer_time_ms") == null
                        ? null
                        : answerRows.getLong("answer_time_ms") * 1_000_000;
                Timestamp received = answerRows.getTimestamp("received_at");
                String req = answerRows.getString("request_id");
                answers.add(
                    new StoredAnswerOutcome(
                        uid,
                        answerRows.getString("answer_json"),
                        elapsed,
                        received == null ? null : received.toInstant(),
                        AnswerOutcomeType.valueOf(answerRows.getString("outcome")),
                        (Boolean) answerRows.getObject("is_correct"),
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
                    roundId,
                    rows.getInt("round_index"),
                    json.readValue(
                        rows.getString("question_snapshot_json"), QuestionSnapshot.class),
                    rows.getBoolean("revealed"),
                    answers));
          } catch (Exception e) {
            throw new SQLException("Invalid history snapshot", e);
          }
        }
      }
    }
    return List.copyOf(result);
  }
}
