package vn.edu.nhom7.quiz.server.query;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.persistence.JdbcMatchRepository;

public final class HistoryService {
  private final ConnectionFactory connections;
  private final JdbcMatchRepository matches;
  private final ObjectMapper json = new ObjectMapper();

  public HistoryService(ConnectionFactory c) {
    connections = c;
    matches = new JdbcMatchRepository(c);
  }

  public Payloads.History history(long requester, int page, int pageSize) {
    int pg = Math.max(page, 1), sz = pageSize < 1 ? 20 : Math.min(pageSize, 50);
    var ids = new ArrayList<UUID>();
    long total;
    try (var c = connections.open()) {
      c.setAutoCommit(false);
      c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      try (var p =
          c.prepareStatement("SELECT COUNT(*) FROM MATCHES WHERE player1_id=? OR player2_id=?")) {
        p.setLong(1, requester);
        p.setLong(2, requester);
        try (var r = p.executeQuery()) {
          r.next();
          total = r.getLong(1);
        }
      }
      try (var p =
          c.prepareStatement(
              "SELECT id FROM MATCHES WHERE player1_id=? OR player2_id=? ORDER BY ended_at DESC,id"
                  + " ASC LIMIT ? OFFSET ?")) {
        p.setLong(1, requester);
        p.setLong(2, requester);
        p.setInt(3, sz);
        p.setLong(4, (long) (pg - 1) * sz);
        try (var r = p.executeQuery()) {
          while (r.next()) ids.add(UUID.fromString(r.getString(1)));
        }
      }
      c.commit();
    } catch (SQLException e) {
      throw ServiceException.database();
    }
    return new Payloads.History(
        pg, sz, total, ids.stream().map(matches::load).map(s -> summary(s, requester)).toList());
  }

  public Payloads.MatchDetail detail(long requester, UUID matchId) {
    try (var c = connections.open();
        var p = c.prepareStatement("SELECT player1_id,player2_id FROM MATCHES WHERE id=?")) {
      p.setString(1, matchId.toString());
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new ServiceException("MATCH_NOT_FOUND", "Committed match not found");
        if (r.getLong(1) != requester && r.getLong(2) != requester)
          throw new ServiceException(
              "NOT_MATCH_MEMBER", "Match detail is available to its participants");
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
    var s = matches.load(matchId);
    var reviews = s.rounds().stream().map(this::review).toList();
    return new Payloads.MatchDetail(summary(s, requester), reviews);
  }

  private Payloads.MatchHistorySummary summary(MatchSummary s, long requester) {
    boolean first = s.player1().userId() == requester;
    var own = first ? s.player1() : s.player2();
    var opponent = first ? s.player2() : s.player1();
    String outcome =
        s.outcome() == MatchOutcome.NONE
            ? "NONE"
            : s.outcome() == MatchOutcome.DRAW
                ? "DRAW"
                : (s.outcome() == MatchOutcome.PLAYER1_WIN) == first ? "WIN" : "LOSS";
    return new Payloads.MatchHistorySummary(
        s.matchId(),
        s.quizId(),
        s.quizTitle(),
        opponent.userId(),
        opponent.displayName(),
        own.totalScore(),
        opponent.totalScore(),
        outcome,
        s.finishReason().name(),
        s.reasonCode().name(),
        s.startedAt().toEpochMilli(),
        s.endedAt().toEpochMilli());
  }

  private Payloads.QuestionReview review(RoundSummary r) {
    var q = r.question();
    return new Payloads.QuestionReview(
        r.roundId(),
        r.roundIndex(),
        new Payloads.PublicQuestion(
            q.questionId(),
            q.questionType().name(),
            q.content(),
            q.options(),
            q.questionAssetId(),
            15000),
        correctAnswer(q),
        q.explanation(),
        q.explanationAssetId(),
        r.outcomes().stream()
            .map(
                a ->
                    new Payloads.AnswerOutcome(
                        a.userId(),
                        parse(a.answerJson()),
                        a.outcome().name(),
                        a.correct(),
                        a.elapsedNanos() == null ? null : a.elapsedNanos() / 1_000_000,
                        a.earnedPoints(),
                        a.scoreBefore(),
                        a.totalScore()))
            .toList(),
        r.revealed());
  }

  private JsonNode correctAnswer(QuestionSnapshot q) {
    var key = parse(q.answerKeyJson());
    return q.questionType().name().equals("SHORT_ANSWER") ? key.get(0) : key;
  }

  private JsonNode parse(String text) {
    if (text == null) return null;
    try {
      var node = json.readTree(text);
      if (node.has("value")) return node.get("value");
      if (node.has("acceptedAnswers")) return node.get("acceptedAnswers");
      return node;
    } catch (Exception e) {
      throw new ServiceException("INTERNAL_ERROR", "Stored match data invalid");
    }
  }
}
