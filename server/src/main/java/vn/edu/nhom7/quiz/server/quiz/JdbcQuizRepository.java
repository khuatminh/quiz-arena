package vn.edu.nhom7.quiz.server.quiz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.util.*;
import java.util.random.RandomGenerator;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.*;
import vn.edu.nhom7.quiz.server.domain.*;

public final class JdbcQuizRepository implements QuizRepository {
  private final ConnectionFactory connections;
  private final ObjectMapper json = new ObjectMapper();
  private static final Map<QuestionType, Integer> REQUIRED =
      Map.of(
          QuestionType.SINGLE_CHOICE,
          4,
          QuestionType.MULTIPLE_CHOICE,
          2,
          QuestionType.TRUE_FALSE,
          2,
          QuestionType.SHORT_ANSWER,
          2);

  public JdbcQuizRepository(ConnectionFactory c) {
    connections = c;
  }

  private List<QuestionSnapshot> questions(long quizId) {
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "SELECT q.* FROM QUESTIONS q JOIN QUIZZES z ON z.id=q.quiz_id JOIN CATEGORIES t ON"
                    + " t.id=z.category_id WHERE q.quiz_id=? AND q.is_active=TRUE AND"
                    + " z.is_active=TRUE AND t.is_active=TRUE ORDER BY q.id")) {
      p.setLong(1, quizId);
      try (var r = p.executeQuery()) {
        var result = new ArrayList<QuestionSnapshot>();
        while (r.next()) {
          var q =
              new QuestionSnapshot(
                  r.getLong("id"),
                  quizId,
                  QuestionType.valueOf(r.getString("question_type")),
                  r.getString("content"),
                  json.readValue(
                      r.getString("options_json"), new TypeReference<List<Payloads.Option>>() {}),
                  r.getString("answer_key_json"),
                  r.getString("explanation"),
                  r.getString("question_asset_id"),
                  r.getString("explanation_asset_id"));
          try {
            QuestionValidator.validate(q);
            result.add(q);
          } catch (IllegalArgumentException invalid) {
            /* invalid active entries never enter a match */
          }
        }
        return result;
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    } catch (java.io.IOException e) {
      throw new ServiceException("QUIZ_UNAVAILABLE", "Invalid question bank");
    }
  }

  public List<QuestionSnapshot> loadMatchQuestions(long id, RandomGenerator random) {
    var summary = summary(id);
    if ("COMMUNITY".equals(summary.quizSource())) return loadMatchQuestions(summary, random);
    var all = questions(id);
    var result = new ArrayList<QuestionSnapshot>();
    for (var type : QuestionType.values()) {
      var bucket = new ArrayList<>(all.stream().filter(q -> q.questionType() == type).toList());
      int count = REQUIRED.get(type);
      if (bucket.size() < count)
        throw new ServiceException("QUIZ_UNAVAILABLE", "Quiz lacks required question types");
      shuffle(bucket, random);
      result.addAll(bucket.subList(0, count));
    }
    shuffle(result, random);
    return List.copyOf(result);
  }

  @Override
  public Payloads.QuizSummary currentMatchQuiz(Payloads.QuizSummary quiz) {
    return "COMMUNITY".equals(quiz.quizSource()) ? summary(quiz.quizId()) : quiz;
  }

  @Override
  public List<QuestionSnapshot> loadMatchQuestions(
      Payloads.QuizSummary quiz, RandomGenerator random) {
    return withMatchQuestions(quiz, random, (pinned, questions) -> questions);
  }

  @Override
  public <T> T withMatchQuestions(
      Payloads.QuizSummary quiz,
      RandomGenerator random,
      java.util.function.BiFunction<Payloads.QuizSummary, List<QuestionSnapshot>, T> action) {
    if (!"COMMUNITY".equals(quiz.quizSource()))
      return action.apply(quiz, loadMatchQuestions(quiz.quizId(), random));
    try (var c = connections.open()) {
      c.setAutoCommit(false);
      try (var p =
          c.prepareStatement(
              "SELECT z.public_version_id,v.shuffle_questions FROM QUIZZES z JOIN QUIZ_VERSIONS v"
                  + " ON v.id=z.public_version_id JOIN CATEGORIES t ON t.id=v.category_id WHERE"
                  + " z.id=? AND z.is_active=TRUE AND t.is_active=TRUE FOR SHARE")) {
        p.setLong(1, quiz.quizId());
        boolean shuffle;
        try (var r = p.executeQuery()) {
          if (!r.next() || r.getLong(1) != quiz.quizVersionId())
            throw new ServiceException("QUIZ_UNAVAILABLE", "Quiz publication changed");
          shuffle = r.getBoolean(2);
        }
        var result = versionQuestions(c, quiz.quizId(), quiz.quizVersionId());
        if (result.isEmpty() || result.size() > 50 || result.size() != quiz.totalRounds())
          throw new ServiceException("QUIZ_UNAVAILABLE", "Invalid quiz version");
        if (shuffle) shuffle(result, random);
        T value = action.apply(quiz, List.copyOf(result));
        c.commit();
        return value;
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private ArrayList<QuestionSnapshot> versionQuestions(Connection c, long quizId, long versionId)
      throws SQLException {
    var result = new ArrayList<QuestionSnapshot>();
    try (var p =
        c.prepareStatement(
            "SELECT q.* FROM QUIZ_VERSION_QUESTIONS v JOIN QUESTIONS q ON q.id=v.question_id WHERE"
                + " v.version_id=? ORDER BY v.position")) {
      p.setLong(1, versionId);
      try (var r = p.executeQuery()) {
        while (r.next()) {
          try {
            var q =
                new QuestionSnapshot(
                    r.getLong("id"),
                    quizId,
                    QuestionType.valueOf(r.getString("question_type")),
                    r.getString("content"),
                    json.readValue(
                        r.getString("options_json"), new TypeReference<List<Payloads.Option>>() {}),
                    r.getString("answer_key_json"),
                    r.getString("explanation"),
                    r.getString("question_asset_id"),
                    r.getString("explanation_asset_id"));
            QuestionValidator.validate(q);
            result.add(q);
          } catch (java.io.IOException | IllegalArgumentException e) {
            throw new ServiceException("QUIZ_UNAVAILABLE", "Invalid quiz version");
          }
        }
      }
    }
    return result;
  }

  private static <T> void shuffle(List<T> list, RandomGenerator random) {
    for (int i = list.size() - 1; i > 0; i--) Collections.swap(list, i, random.nextInt(i + 1));
  }

  public Payloads.QuizDetail detail(long quizId) {
    var q = summary(quizId);
    var counts = new LinkedHashMap<String, Integer>();
    for (var type : QuestionType.values()) counts.put(type.name(), 0);
    List<QuestionSnapshot> detailQuestions;
    if ("COMMUNITY".equals(q.quizSource())) {
      try (var c = connections.open()) {
        detailQuestions = versionQuestions(c, quizId, q.quizVersionId());
      } catch (SQLException e) {
        throw ServiceException.database();
      }
    } else detailQuestions = questions(quizId);
    for (var question : detailQuestions)
      counts.merge(question.questionType().name(), 1, Integer::sum);
    String description;
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "COMMUNITY".equals(q.quizSource())
                    ? "SELECT description FROM QUIZ_VERSIONS WHERE id=?"
                    : "SELECT description FROM QUIZZES WHERE id=?")) {
      p.setLong(1, "COMMUNITY".equals(q.quizSource()) ? q.quizVersionId() : quizId);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new ServiceException("QUIZ_NOT_FOUND", "Unknown quiz");
        description = r.getString(1);
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
    var rules =
        json.createObjectNode()
            .put("totalRounds", q.totalRounds())
            .put("answerWindowMs", 15000)
            .put("singleChoice", 4)
            .put("multipleChoice", 2)
            .put("trueFalse", 2)
            .put("shortAnswer", 2);
    return new Payloads.QuizDetail(q, description, Map.copyOf(counts), rules);
  }

  public Payloads.QuizSummary summary(long quizId) {
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "SELECT z.*,t.name category_name,u.display_name author_name FROM QUIZZES z LEFT"
                    + " JOIN USERS u ON u.id=z.owner_id JOIN CATEGORIES t ON t.id=z.category_id"
                    + " WHERE z.id=? AND z.is_active=TRUE AND t.is_active=TRUE")) {
      p.setLong(1, quizId);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new ServiceException("QUIZ_NOT_FOUND", "Unknown quiz");
        if ("COMMUNITY".equals(r.getString("quiz_source"))) {
          long version = r.getLong("public_version_id");
          if (version <= 0) throw new ServiceException("QUIZ_NOT_FOUND", "Unknown quiz");
          var all = versionQuestions(c, quizId, version);
          return new Payloads.QuizSummary(
              quizId,
              r.getString("title"),
              r.getLong("category_id"),
              r.getString("category_name"),
              r.getString("cover_asset_id"),
              all.isEmpty() ? "UNAVAILABLE" : "AVAILABLE",
              all.size(),
              "COMMUNITY",
              r.getString("author_name"),
              version);
        }
        var all = questions(quizId);
        boolean available =
            REQUIRED.entrySet().stream()
                .allMatch(
                    e ->
                        all.stream().filter(q -> q.questionType() == e.getKey()).count()
                            >= e.getValue());
        return new Payloads.QuizSummary(
            quizId,
            r.getString("title"),
            r.getLong("category_id"),
            r.getString("category_name"),
            r.getString("cover_asset_id"),
            available ? "AVAILABLE" : "UNAVAILABLE",
            10);
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  public Payloads.QuizList list(Long categoryId, int page, int pageSize) {
    int pg = page < 1 ? 1 : page, sz = pageSize < 1 ? 10 : Math.min(pageSize, 10);
    var ids = new ArrayList<Long>();
    var categories = new ArrayList<Payloads.Category>();
    try (var c = connections.open()) {
      try (var p =
              c.prepareStatement(
                  "SELECT id,name,display_order FROM CATEGORIES WHERE is_active=TRUE ORDER BY"
                      + " display_order,id");
          var r = p.executeQuery()) {
        while (r.next())
          categories.add(new Payloads.Category(r.getLong(1), r.getString(2), r.getInt(3)));
      }
      try (var p =
          c.prepareStatement(
              "SELECT z.id FROM QUIZZES z JOIN CATEGORIES c ON c.id=z.category_id WHERE"
                  + " z.is_active=TRUE AND c.is_active=TRUE AND (z.quiz_source='SYSTEM' OR"
                  + " z.public_version_id IS NOT NULL) AND (? IS NULL OR z.category_id=?) ORDER BY"
                  + " z.id")) {
        if (categoryId == null) {
          p.setNull(1, Types.BIGINT);
          p.setNull(2, Types.BIGINT);
        } else {
          p.setLong(1, categoryId);
          p.setLong(2, categoryId);
        }
        try (var r = p.executeQuery()) {
          while (r.next()) ids.add(r.getLong(1));
        }
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
    long offset = (long) (pg - 1) * sz;
    var items = ids.stream().skip(offset).limit(sz).map(this::summary).toList();
    return new Payloads.QuizList(pg, sz, ids.size(), List.copyOf(categories), items);
  }

  public void verifyBank() {
    int usable = 0;
    try (var c = connections.open();
        var p =
            c.prepareStatement(
                "SELECT z.id,COUNT(q.id) active_count FROM QUIZZES z JOIN CATEGORIES t ON"
                    + " t.id=z.category_id LEFT JOIN QUESTIONS q ON q.quiz_id=z.id AND"
                    + " q.is_active=TRUE WHERE z.is_active=TRUE AND t.is_active=TRUE AND"
                    + " z.quiz_source='SYSTEM' GROUP BY z.id");
        var r = p.executeQuery()) {
      while (r.next()) {
        long id = r.getLong(1);
        var loaded = questions(id);
        if (loaded.size() != r.getLong(2))
          throw new IllegalStateException(
              "Invalid active question bank entry; repair the bank before startup");
        if (REQUIRED.entrySet().stream()
            .allMatch(
                e ->
                    loaded.stream().filter(q -> q.questionType() == e.getKey()).count()
                        >= e.getValue())) usable++;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Question bank unavailable; apply database/002_seed.sql");
    }
    if (usable == 0) throw new IllegalStateException("No usable quiz; apply database/002_seed.sql");
  }
}
