package vn.edu.nhom7.quiz.server.quiz;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.sql.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.CommunityPayloads.*;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;
import vn.edu.nhom7.quiz.common.protocol.QuestionType;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.match.MatchWireBudget;

/** Owner-only mutable drafts and atomic immutable publication. All positions are zero based. */
public final class CommunityQuizService {
  private final ConnectionFactory connections;
  private final ObjectMapper json = new ObjectMapper();

  public CommunityQuizService(ConnectionFactory connections) {
    this.connections = connections;
  }

  public AuthorResult execute(long userId, AuthorRequest request) {
    if (userId <= 0 || request == null || request.action() == null)
      throw invalid("Invalid request");
    String action = request.action();
    if ((action.equals("CREATE") || action.equals("LIST"))
        ? request.quizId() != 0
        : request.quizId() <= 0) throw invalid("Invalid quiz ID");
    JsonNode data = request.data() == null ? json.createObjectNode() : request.data();
    try (var c = connections.open()) {
      c.setAutoCommit(false);
      try {
        long id = request.quizId();
        JsonNode result;
        if (action.equals("LIST")) result = list(c, userId, data);
        else {
          if (action.equals("CREATE")) id = create(c, userId, data);
          owner(c, id, userId);
          switch (action) {
            case "CREATE", "GET" -> {}
            case "SAVE_META" -> saveMeta(c, id, data);
            case "GET_QUESTION" -> {
              int index = index(data, "index");
              result =
                  json.createObjectNode()
                      .put("index", index)
                      .set("question", readQuestion(c, id, index));
              c.commit();
              return new AuthorResult(action, result);
            }
            case "SAVE_QUESTION" -> saveQuestion(c, id, userId, data);
            case "DELETE_QUESTION" -> deleteQuestion(c, id, index(data, "index"));
            case "MOVE_QUESTION" ->
                moveQuestion(c, id, index(data, "index"), index(data, "toIndex"));
            case "PUBLISH" -> publish(c, id, userId);
            case "UNPUBLISH" ->
                update(
                    c,
                    "UPDATE QUIZZES SET"
                        + " public_version_id=NULL,is_active=FALSE,updated_at=UTC_TIMESTAMP(3)"
                        + " WHERE id=?",
                    id);
            default -> throw invalid("Unknown authoring action");
          }
          result = metadata(c, id);
        }
        c.commit();
        return new AuthorResult(action, result);
      } catch (Exception e) {
        c.rollback();
        if (e instanceof ServiceException service) throw service;
        if (e instanceof SQLException) throw ServiceException.database();
        if (e instanceof IllegalArgumentException
            || e instanceof com.fasterxml.jackson.core.JacksonException)
          throw invalid("Invalid question data");
        throw new IllegalStateException(e);
      }
    } catch (SQLException e) {
      throw ServiceException.database();
    }
  }

  private long create(Connection c, long user, JsonNode data) throws SQLException {
    long category;
    try (var s = c.createStatement();
        var r =
            s.executeQuery(
                "SELECT id FROM CATEGORIES WHERE is_active=TRUE ORDER BY display_order,id LIMIT"
                    + " 1")) {
      if (!r.next()) throw invalid("No active category");
      category = r.getLong(1);
    }
    long id =
        insert(
            c,
            "INSERT INTO"
                + " QUIZZES(category_id,title,description,is_active,created_at,updated_at,quiz_source,owner_id)"
                + " VALUES(?,'New quiz','',FALSE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'COMMUNITY',?)",
            category,
            user);
    update(
        c,
        "INSERT INTO QUIZ_DRAFTS(quiz_id,title,description,category_id,shuffle_questions)"
            + " VALUES(?,'New quiz','',?,FALSE)",
        id,
        category);
    if (data.has("title")) saveMeta(c, id, data);
    return id;
  }

  private void owner(Connection c, long id, long user) throws SQLException {
    try (var p =
        c.prepareStatement(
            "SELECT owner_id FROM QUIZZES WHERE id=? AND quiz_source='COMMUNITY' FOR UPDATE")) {
      p.setLong(1, id);
      try (var r = p.executeQuery()) {
        if (!r.next() || r.getLong(1) != user)
          throw new ServiceException("QUIZ_NOT_FOUND", "Quiz not found");
      }
    }
  }

  private ObjectNode metadata(Connection c, long id) throws SQLException {
    ObjectNode result = json.createObjectNode();
    try (var p =
        c.prepareStatement(
            "SELECT d.*,q.public_version_id FROM QUIZ_DRAFTS d JOIN QUIZZES q ON q.id=d.quiz_id"
                + " WHERE d.quiz_id=?")) {
      p.setLong(1, id);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw invalid("Missing draft");
        result
            .put("quizId", id)
            .put("title", r.getString("title"))
            .put("description", r.getString("description"))
            .put("categoryId", r.getLong("category_id"))
            .put("shuffleQuestions", r.getBoolean("shuffle_questions"))
            .put("publicVersionId", r.getLong("public_version_id"))
            .put("status", r.getLong("public_version_id") > 0 ? "PUBLISHED" : "DRAFT");
      }
    }
    ArrayNode questions = result.putArray("questions");
    try (var p =
        c.prepareStatement(
            "SELECT"
                + " position,JSON_UNQUOTE(JSON_EXTRACT(question_json,'$.questionType')),LEFT(JSON_UNQUOTE(JSON_EXTRACT(question_json,'$.content')),60)"
                + " FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? ORDER BY position")) {
      p.setLong(1, id);
      try (var r = p.executeQuery()) {
        while (r.next())
          questions
              .addObject()
              .put("index", r.getInt(1))
              .put("questionType", r.getString(2))
              .put("content", r.getString(3));
      }
    }
    return result.put("questionCount", questions.size());
  }

  private JsonNode list(Connection c, long user, JsonNode data) throws SQLException {
    int page = Math.max(1, data.path("page").asInt(1)),
        size = Math.min(20, Math.max(1, data.path("pageSize").asInt(20)));
    ObjectNode result = json.createObjectNode().put("page", page).put("pageSize", size);
    ArrayNode quizzes = result.putArray("quizzes");
    try (var p =
        c.prepareStatement(
            "SELECT COUNT(*) FROM QUIZZES WHERE owner_id=? AND quiz_source='COMMUNITY'")) {
      p.setLong(1, user);
      try (var r = p.executeQuery()) {
        r.next();
        result.put("total", r.getLong(1));
      }
    }
    try (var p =
        c.prepareStatement(
            "SELECT q.id,d.title,q.public_version_id,(SELECT COUNT(*) FROM QUIZ_DRAFT_QUESTIONS x"
                + " WHERE x.quiz_id=q.id) question_count FROM QUIZZES q JOIN QUIZ_DRAFTS d ON"
                + " d.quiz_id=q.id WHERE q.owner_id=? AND q.quiz_source='COMMUNITY' ORDER BY q.id"
                + " DESC LIMIT ? OFFSET ?")) {
      p.setLong(1, user);
      p.setInt(2, size);
      p.setLong(3, (long) (page - 1) * size);
      try (var r = p.executeQuery()) {
        while (r.next())
          quizzes
              .addObject()
              .put("quizId", r.getLong(1))
              .put("title", r.getString(2))
              .put("publicVersionId", r.getLong(3))
              .put("status", r.getLong(3) > 0 ? "PUBLISHED" : "DRAFT")
              .put("questionCount", r.getInt(4));
      }
    }
    return result;
  }

  private void saveMeta(Connection c, long id, JsonNode data) throws SQLException {
    var old = metadata(c, id);
    String title =
        data.has("title") ? text(data.get("title"), 128, false) : old.path("title").asText();
    String description =
        data.has("description")
            ? text(data.get("description"), 1000, false)
            : old.path("description").asText();
    long category =
        data.has("categoryId") ? data.path("categoryId").asLong() : old.path("categoryId").asLong();
    category(c, category);
    if (data.has("shuffleQuestions") && !data.get("shuffleQuestions").isBoolean())
      throw invalid("shuffleQuestions must be boolean");
    boolean shuffle =
        data.has("shuffleQuestions")
            ? data.path("shuffleQuestions").asBoolean()
            : old.path("shuffleQuestions").asBoolean();
    update(
        c,
        "UPDATE QUIZ_DRAFTS SET title=?,description=?,category_id=?,shuffle_questions=? WHERE"
            + " quiz_id=?",
        title,
        description,
        category,
        shuffle,
        id);
  }

  private void category(Connection c, long id) throws SQLException {
    try (var p = c.prepareStatement("SELECT id FROM CATEGORIES WHERE id=? AND is_active=TRUE")) {
      p.setLong(1, id);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw invalid("Invalid category");
      }
    }
  }

  private int count(Connection c, long id) throws SQLException {
    try (var p = c.prepareStatement("SELECT COUNT(*) FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=?")) {
      p.setLong(1, id);
      try (var r = p.executeQuery()) {
        r.next();
        return r.getInt(1);
      }
    }
  }

  private JsonNode readQuestion(Connection c, long id, int index) throws Exception {
    try (var p =
        c.prepareStatement(
            "SELECT question_json FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? AND position=?")) {
      p.setLong(1, id);
      p.setInt(2, index);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw invalid("Question index out of range");
        return json.readTree(r.getString(1));
      }
    }
  }

  private void saveQuestion(Connection c, long id, long user, JsonNode data) throws Exception {
    int index = index(data, "index"), count = count(c, id);
    if (index > count || (index == count && count >= 50))
      throw invalid("A quiz supports at most 50 questions");
    JsonNode node = data.get("question");
    if (node == null || !node.isObject()) throw invalid("Question is required");
    DraftQuestion q = json.treeToValue(node, DraftQuestion.class);
    validateDraft(c, user, q);
    update(
        c,
        "INSERT INTO QUIZ_DRAFT_QUESTIONS(quiz_id,position,question_json) VALUES(?,?,?) ON"
            + " DUPLICATE KEY UPDATE question_json=VALUES(question_json)",
        id,
        index,
        json.writeValueAsString(q));
  }

  private void validateDraft(Connection c, long user, DraftQuestion q) throws SQLException {
    if (q.questionType() == null) throw invalid("Question type is required");
    try {
      QuestionType.valueOf(q.questionType());
    } catch (IllegalArgumentException e) {
      throw invalid("Invalid question type");
    }
    bound(q.content(), 500);
    bound(q.explanation(), 500);
    if (q.options() != null) {
      if (q.options().size() > 6) throw invalid("Too many options");
      var ids = new HashSet<String>();
      for (var o : q.options()) {
        if (o == null) throw invalid("Invalid option");
        bound(o.id(), 64);
        bound(o.text(), 120);
        if (o.id() != null && !ids.add(o.id())) throw invalid("Duplicate option ID");
      }
    }
    if (q.answerKey() != null && q.answerKey().toString().length() > 8192)
      throw invalid("Answer key too large");
    if ("SHORT_ANSWER".equals(q.questionType()) && q.answerKey() != null) {
      JsonNode accepted =
          q.answerKey().has("acceptedAnswers")
              ? q.answerKey().get("acceptedAnswers")
              : q.answerKey();
      if (accepted.isArray() && accepted.size() > 20)
        throw invalid("At most 20 accepted answers are allowed");
    }
    try {
      // Reserve room for the author-result envelope and index around the stored question.
      if (json.writeValueAsBytes(q).length > 60 * 1024)
        throw invalid("Nội dung câu hỏi quá lớn để tải lại. Hãy rút gọn nội dung hoặc đáp án.");
    } catch (java.io.IOException e) {
      throw invalid("Invalid question data");
    }
    asset(c, user, q.questionAssetId());
    asset(c, user, q.explanationAssetId());
  }

  private void asset(Connection c, long user, String id) throws SQLException {
    if (id == null) return;
    if (!id.matches("media-[A-Za-z0-9-]{1,58}")) throw invalid("Invalid media ID");
    try (var p = c.prepareStatement("SELECT id FROM MEDIA_ASSETS WHERE id=? AND owner_id=?")) {
      p.setString(1, id);
      p.setLong(2, user);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw invalid("Media is not available to this author");
      }
    }
  }

  private void deleteQuestion(Connection c, long id, int index) throws SQLException {
    if (index >= count(c, id)) throw invalid("Question index out of range");
    update(c, "DELETE FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? AND position=?", id, index);
    update(
        c,
        "UPDATE QUIZ_DRAFT_QUESTIONS SET position=position-1 WHERE quiz_id=? AND position>? ORDER"
            + " BY position ASC",
        id,
        index);
  }

  private void moveQuestion(Connection c, long id, int from, int to) throws Exception {
    int count = count(c, id);
    if (from >= count || to >= count) throw invalid("Question index out of range");
    if (from == to) return;
    String question = json.writeValueAsString(readQuestion(c, id, from));
    update(c, "DELETE FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? AND position=?", id, from);
    if (from < to)
      update(
          c,
          "UPDATE QUIZ_DRAFT_QUESTIONS SET position=position-1 WHERE quiz_id=? AND position>? AND"
              + " position<=? ORDER BY position ASC",
          id,
          from,
          to);
    else
      update(
          c,
          "UPDATE QUIZ_DRAFT_QUESTIONS SET position=position+1 WHERE quiz_id=? AND position>=? AND"
              + " position<? ORDER BY position DESC",
          id,
          to,
          from);
    update(c, "INSERT INTO QUIZ_DRAFT_QUESTIONS VALUES(?,?,?)", id, to, question);
  }

  private void publish(Connection c, long id, long user) throws Exception {
    var meta = metadata(c, id);
    if (meta.path("title").asText().isBlank()) throw invalid("Title is required");
    category(c, meta.path("categoryId").asLong());
    int count = count(c, id);
    if (count < 1 || count > 50) throw invalid("A quiz requires 1–50 questions");
    var questions = new ArrayList<DraftQuestion>();
    for (int i = 0; i < count; i++) {
      DraftQuestion q = json.treeToValue(readQuestion(c, id, i), DraftQuestion.class);
      validateDraft(c, user, q);
      try {
        QuestionValidator.validate(
            new QuestionSnapshot(
                0,
                id,
                QuestionType.valueOf(q.questionType()),
                q.content(),
                q.options() == null ? List.of() : q.options(),
                json.writeValueAsString(q.answerKey()),
                q.explanation() == null || q.explanation().isBlank() ? null : q.explanation(),
                q.questionAssetId(),
                q.explanationAssetId()));
      } catch (IllegalArgumentException e) {
        throw invalid("Invalid question at index " + i);
      }
      questions.add(q);
    }
    var candidates = new ArrayList<QuestionSnapshot>();
    for (var q : questions)
      candidates.add(
          new QuestionSnapshot(
              Long.MAX_VALUE,
              id,
              QuestionType.valueOf(q.questionType()),
              q.content(),
              q.options() == null ? List.of() : q.options(),
              json.writeValueAsString(q.answerKey()),
              q.explanation() == null || q.explanation().isBlank() ? null : q.explanation(),
              q.questionAssetId(),
              q.explanationAssetId()));
    String largestName = "😀".repeat(128);
    try {
      MatchWireBudget.validate(
          new Payloads.QuizSummary(
              id,
              meta.path("title").asText(),
              meta.path("categoryId").asLong(),
              "",
              null,
              "AVAILABLE",
              count,
              "COMMUNITY",
              largestName,
              0),
          candidates,
          List.of(
              new ParticipantSummary(Long.MAX_VALUE, largestName, "avatar-1", 0, 0),
              new ParticipantSummary(Long.MAX_VALUE - 1, largestName, "avatar-1", 0, 0)));
    } catch (ProtocolException e) {
      throw invalid(
          "Nội dung câu hỏi hoặc đáp án quá lớn để công bố kết quả. Hãy rút gọn rồi xuất bản lại.");
    }
    long version =
        insert(
            c,
            "INSERT INTO"
                + " QUIZ_VERSIONS(quiz_id,title,description,category_id,shuffle_questions,created_at)"
                + " VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",
            id,
            meta.path("title").asText(),
            meta.path("description").asText(),
            meta.path("categoryId").asLong(),
            meta.path("shuffleQuestions").asBoolean());
    for (int i = 0; i < count; i++) {
      DraftQuestion q = questions.get(i);
      long question =
          insert(
              c,
              "INSERT INTO"
                  + " QUESTIONS(quiz_id,question_type,content,options_json,answer_key_json,explanation,question_asset_id,explanation_asset_id,is_active,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,?,?,?,TRUE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
              id,
              q.questionType(),
              q.content(),
              json.writeValueAsString(q.options() == null ? List.of() : q.options()),
              json.writeValueAsString(q.answerKey()),
              q.explanation() == null || q.explanation().isBlank() ? null : q.explanation(),
              q.questionAssetId(),
              q.explanationAssetId());
      update(c, "INSERT INTO QUIZ_VERSION_QUESTIONS VALUES(?,?,?)", version, i, question);
    }
    update(
        c,
        "UPDATE QUIZZES SET"
            + " public_version_id=?,title=?,description=?,category_id=?,is_active=TRUE,updated_at=UTC_TIMESTAMP(3)"
            + " WHERE id=?",
        version,
        meta.path("title").asText(),
        meta.path("description").asText(),
        meta.path("categoryId").asLong(),
        id);
  }

  private static int index(JsonNode data, String field) {
    var n = data.get(field);
    if (n == null
        || !n.isIntegralNumber()
        || !n.canConvertToInt()
        || n.intValue() < 0
        || n.intValue() > 49) throw invalid("Invalid " + field);
    return n.intValue();
  }

  private static String text(JsonNode n, int max, boolean required) {
    if (n == null || !n.isTextual()) throw invalid("Expected text");
    String s = n.asText();
    bound(s, max);
    if (required && s.isBlank()) throw invalid("Text is required");
    return s;
  }

  private static void bound(String s, int max) {
    if (s != null && s.codePointCount(0, s.length()) > max)
      throw invalid("Text exceeds " + max + " characters");
  }

  private static ServiceException invalid(String message) {
    return new ServiceException("VALIDATION_ERROR", message);
  }

  private static void bind(PreparedStatement p, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
  }

  private static int update(Connection c, String sql, Object... args) throws SQLException {
    try (var p = c.prepareStatement(sql)) {
      bind(p, args);
      return p.executeUpdate();
    }
  }

  private static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      bind(p, args);
      p.executeUpdate();
      try (var r = p.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("No generated ID");
        return r.getLong(1);
      }
    }
  }
}
