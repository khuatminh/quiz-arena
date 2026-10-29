package vn.edu.nhom7.quiz.server.quiz;

import static vn.edu.nhom7.quiz.server.quiz.DraftValidator.*;
import static vn.edu.nhom7.quiz.server.quiz.QuizSql.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.sql.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.CommunityPayloads.*;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.domain.*;

/** JDBC operations for mutable drafts, using the transaction supplied by the service. */
final class JdbcCommunityQuizRepository {
  private final ObjectMapper json = new ObjectMapper();

  long create(Connection connection, long user, JsonNode data) throws SQLException {
    long category;
    try (var s = connection.createStatement();
        var rows =
            s.executeQuery(
                "SELECT id FROM CATEGORIES WHERE is_active=TRUE ORDER BY display_order,id LIMIT"
                    + " 1")) {
      if (!rows.next()) throw invalid("No active category");
      category = rows.getLong(1);
    }
    long id =
        insert(
            connection,
            "INSERT INTO"
                + " QUIZZES(category_id,title,description,is_active,created_at,updated_at,quiz_source,owner_id)"
                + " VALUES(?,'New quiz','',FALSE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'COMMUNITY',?)",
            category,
            user);
    update(
        connection,
        "INSERT INTO QUIZ_DRAFTS(quiz_id,title,description,category_id,shuffle_questions)"
            + " VALUES(?,'New quiz','',?,FALSE)",
        id,
        category);
    if (data.has("title")) saveMeta(connection, id, data);
    return id;
  }

  void owner(Connection connection, long id, long user) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT owner_id FROM QUIZZES WHERE id=? AND quiz_source='COMMUNITY' FOR UPDATE")) {
      statement.setLong(1, id);
      try (var rows = statement.executeQuery()) {
        if (!rows.next() || rows.getLong(1) != user)
          throw new ServiceException("QUIZ_NOT_FOUND", "Quiz not found");
      }
    }
  }

  ObjectNode metadata(Connection connection, long id) throws SQLException {
    ObjectNode result = json.createObjectNode();
    try (var statement =
        connection.prepareStatement(
            "SELECT d.*,q.public_version_id FROM QUIZ_DRAFTS d JOIN QUIZZES q ON q.id=d.quiz_id"
                + " WHERE d.quiz_id=?")) {
      statement.setLong(1, id);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) throw invalid("Missing draft");
        result
            .put("quizId", id)
            .put("title", rows.getString("title"))
            .put("description", rows.getString("description"))
            .put("categoryId", rows.getLong("category_id"))
            .put("shuffleQuestions", rows.getBoolean("shuffle_questions"))
            .put("publicVersionId", rows.getLong("public_version_id"))
            .put("status", rows.getLong("public_version_id") > 0 ? "PUBLISHED" : "DRAFT");
      }
    }
    ArrayNode questions = result.putArray("questions");
    try (var statement =
        connection.prepareStatement(
            "SELECT"
                + " position,JSON_UNQUOTE(JSON_EXTRACT(question_json,'$.questionType')),LEFT(JSON_UNQUOTE(JSON_EXTRACT(question_json,'$.content')),60)"
                + " FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? ORDER BY position")) {
      statement.setLong(1, id);
      try (var rows = statement.executeQuery()) {
        while (rows.next())
          questions
              .addObject()
              .put("index", rows.getInt(1))
              .put("questionType", rows.getString(2))
              .put("content", rows.getString(3));
      }
    }
    return result.put("questionCount", questions.size());
  }

  JsonNode list(Connection connection, long user, JsonNode data) throws SQLException {
    int page = Math.max(1, data.path("page").asInt(1)),
        size = Math.min(20, Math.max(1, data.path("pageSize").asInt(20)));
    ObjectNode result = json.createObjectNode().put("page", page).put("pageSize", size);
    ArrayNode quizzes = result.putArray("quizzes");
    try (var statement =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM QUIZZES WHERE owner_id=? AND quiz_source='COMMUNITY'")) {
      statement.setLong(1, user);
      try (var rows = statement.executeQuery()) {
        rows.next();
        result.put("total", rows.getLong(1));
      }
    }
    try (var statement =
        connection.prepareStatement(
            "SELECT q.id,d.title,q.public_version_id,(SELECT COUNT(*) FROM QUIZ_DRAFT_QUESTIONS x"
                + " WHERE x.quiz_id=q.id) question_count FROM QUIZZES q JOIN QUIZ_DRAFTS d ON"
                + " d.quiz_id=q.id WHERE q.owner_id=? AND q.quiz_source='COMMUNITY' ORDER BY q.id"
                + " DESC LIMIT ? OFFSET ?")) {
      statement.setLong(1, user);
      statement.setInt(2, size);
      statement.setLong(3, (long) (page - 1) * size);
      try (var rows = statement.executeQuery()) {
        while (rows.next())
          quizzes
              .addObject()
              .put("quizId", rows.getLong(1))
              .put("title", rows.getString(2))
              .put("publicVersionId", rows.getLong(3))
              .put("status", rows.getLong(3) > 0 ? "PUBLISHED" : "DRAFT")
              .put("questionCount", rows.getInt(4));
      }
    }
    return result;
  }

  void saveMeta(Connection connection, long id, JsonNode data) throws SQLException {
    var old = metadata(connection, id);
    String title =
        data.has("title") ? text(data.get("title"), 128, false) : old.path("title").asText();
    String description =
        data.has("description")
            ? text(data.get("description"), 1000, false)
            : old.path("description").asText();
    long category =
        data.has("categoryId") ? data.path("categoryId").asLong() : old.path("categoryId").asLong();
    category(connection, category);
    if (data.has("shuffleQuestions") && !data.get("shuffleQuestions").isBoolean())
      throw invalid("shuffleQuestions must be boolean");
    boolean shuffle =
        data.has("shuffleQuestions")
            ? data.path("shuffleQuestions").asBoolean()
            : old.path("shuffleQuestions").asBoolean();
    update(
        connection,
        "UPDATE QUIZ_DRAFTS SET title=?,description=?,category_id=?,shuffle_questions=? WHERE"
            + " quiz_id=?",
        title,
        description,
        category,
        shuffle,
        id);
  }

  void category(Connection connection, long id) throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT id FROM CATEGORIES WHERE id=? AND is_active=TRUE")) {
      statement.setLong(1, id);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) throw invalid("Invalid category");
      }
    }
  }

  int count(Connection connection, long id) throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT COUNT(*) FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=?")) {
      statement.setLong(1, id);
      try (var rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  JsonNode readQuestion(Connection connection, long id, int index) throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT question_json FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? AND position=?")) {
      statement.setLong(1, id);
      statement.setInt(2, index);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) throw invalid("Question index out of range");
        return json.readTree(rows.getString(1));
      }
    }
  }

  void saveQuestion(Connection connection, long id, long user, JsonNode data) throws Exception {
    int index = index(data, "index"), count = count(connection, id);
    if (index > count || (index == count && count >= 50))
      throw invalid("A quiz supports at most 50 questions");
    JsonNode node = data.get("question");
    if (node == null || !node.isObject()) throw invalid("Question is required");
    DraftQuestion question = json.treeToValue(node, DraftQuestion.class);
    validateDraft(connection, user, question);
    update(
        connection,
        "INSERT INTO QUIZ_DRAFT_QUESTIONS(quiz_id,position,question_json) VALUES(?,?,?) ON"
            + " DUPLICATE KEY UPDATE question_json=VALUES(question_json)",
        id,
        index,
        json.writeValueAsString(question));
  }

  void validateDraft(Connection connection, long userId, DraftQuestion question)
      throws SQLException {
    DraftValidator.question(question);
    asset(connection, userId, question.questionAssetId());
    asset(connection, userId, question.explanationAssetId());
  }

  private void asset(Connection connection, long user, String id) throws SQLException {
    if (id == null) return;
    if (!id.matches("media-[A-Za-z0-9-]{1,58}")) throw invalid("Invalid media ID");
    try (var statement =
        connection.prepareStatement("SELECT id FROM MEDIA_ASSETS WHERE id=? AND owner_id=?")) {
      statement.setString(1, id);
      statement.setLong(2, user);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) throw invalid("Media is not available to this author");
      }
    }
  }

  void deleteQuestion(Connection connection, long id, int index) throws SQLException {
    if (index >= count(connection, id)) throw invalid("Question index out of range");
    update(
        connection, "DELETE FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? AND position=?", id, index);
    update(
        connection,
        "UPDATE QUIZ_DRAFT_QUESTIONS SET position=position-1 WHERE quiz_id=? AND position>? ORDER"
            + " BY position ASC",
        id,
        index);
  }

  void moveQuestion(Connection connection, long id, int from, int to) throws Exception {
    int count = count(connection, id);
    if (from >= count || to >= count) throw invalid("Question index out of range");
    if (from == to) return;
    String question = json.writeValueAsString(readQuestion(connection, id, from));
    update(connection, "DELETE FROM QUIZ_DRAFT_QUESTIONS WHERE quiz_id=? AND position=?", id, from);
    if (from < to)
      update(
          connection,
          "UPDATE QUIZ_DRAFT_QUESTIONS SET position=position-1 WHERE quiz_id=? AND position>? AND"
              + " position<=? ORDER BY position ASC",
          id,
          from,
          to);
    else
      update(
          connection,
          "UPDATE QUIZ_DRAFT_QUESTIONS SET position=position+1 WHERE quiz_id=? AND position>=? AND"
              + " position<? ORDER BY position DESC",
          id,
          to,
          from);
    update(connection, "INSERT INTO QUIZ_DRAFT_QUESTIONS VALUES(?,?,?)", id, to, question);
  }

  void unpublish(Connection connection, long quizId) throws SQLException {
    update(
        connection,
        "UPDATE QUIZZES SET public_version_id=NULL,is_active=FALSE,updated_at=UTC_TIMESTAMP(3)"
            + " WHERE id=?",
        quizId);
  }
}
