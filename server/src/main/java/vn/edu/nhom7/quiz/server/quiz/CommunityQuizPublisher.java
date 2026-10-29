package vn.edu.nhom7.quiz.server.quiz;

import static vn.edu.nhom7.quiz.server.quiz.DraftValidator.invalid;
import static vn.edu.nhom7.quiz.server.quiz.QuizSql.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.common.protocol.CommunityPayloads.DraftQuestion;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.match.MatchWireBudget;

/** Validates a complete draft and publishes an immutable version in the caller's transaction. */
final class CommunityQuizPublisher {
  private final JdbcCommunityQuizRepository drafts;
  private final ObjectMapper json = new ObjectMapper();

  CommunityQuizPublisher(JdbcCommunityQuizRepository drafts) {
    this.drafts = drafts;
  }

  void publish(Connection connection, long id, long user) throws Exception {
    var meta = drafts.metadata(connection, id);
    if (meta.path("title").asText().isBlank()) throw invalid("Title is required");
    drafts.category(connection, meta.path("categoryId").asLong());
    int count = drafts.count(connection, id);
    if (count < 1 || count > 50) throw invalid("A quiz requires 1–50 questions");
    var questions = loadValidQuestions(connection, id, user, count);
    validateWireBudget(id, meta, questions);
    long version =
        insert(
            connection,
            "INSERT INTO"
                + " QUIZ_VERSIONS(quiz_id,title,description,category_id,shuffle_questions,created_at)"
                + " VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",
            id,
            meta.path("title").asText(),
            meta.path("description").asText(),
            meta.path("categoryId").asLong(),
            meta.path("shuffleQuestions").asBoolean());
    for (int i = 0; i < count; i++) {
      DraftQuestion question = questions.get(i);
      long questionId =
          insert(
              connection,
              "INSERT INTO"
                  + " QUESTIONS(quiz_id,question_type,content,options_json,answer_key_json,explanation,question_asset_id,explanation_asset_id,is_active,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,?,?,?,TRUE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
              id,
              question.questionType(),
              question.content(),
              json.writeValueAsString(question.options() == null ? List.of() : question.options()),
              json.writeValueAsString(question.answerKey()),
              question.explanation() == null || question.explanation().isBlank()
                  ? null
                  : question.explanation(),
              question.questionAssetId(),
              question.explanationAssetId());
      update(
          connection, "INSERT INTO QUIZ_VERSION_QUESTIONS VALUES(?,?,?)", version, i, questionId);
    }
    update(
        connection,
        "UPDATE QUIZZES SET"
            + " public_version_id=?,title=?,description=?,category_id=?,is_active=TRUE,updated_at=UTC_TIMESTAMP(3)"
            + " WHERE id=?",
        version,
        meta.path("title").asText(),
        meta.path("description").asText(),
        meta.path("categoryId").asLong(),
        id);
  }

  private List<DraftQuestion> loadValidQuestions(
      Connection connection, long id, long user, int count) throws Exception {
    var questions = new ArrayList<DraftQuestion>();
    for (int i = 0; i < count; i++) {
      DraftQuestion question =
          json.treeToValue(drafts.readQuestion(connection, id, i), DraftQuestion.class);
      drafts.validateDraft(connection, user, question);
      try {
        QuestionValidator.validate(snapshot(id, 0, question));
      } catch (IllegalArgumentException e) {
        throw invalid("Invalid question at index " + i);
      }
      questions.add(question);
    }
    return questions;
  }

  private void validateWireBudget(
      long id, com.fasterxml.jackson.databind.JsonNode meta, List<DraftQuestion> questions)
      throws Exception {
    var candidates = new ArrayList<QuestionSnapshot>();
    for (var question : questions) candidates.add(snapshot(id, Long.MAX_VALUE, question));
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
              questions.size(),
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
  }

  private QuestionSnapshot snapshot(long quizId, long questionId, DraftQuestion question)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return new QuestionSnapshot(
        questionId,
        quizId,
        QuestionType.valueOf(question.questionType()),
        question.content(),
        question.options() == null ? List.of() : question.options(),
        json.writeValueAsString(question.answerKey()),
        question.explanation() == null || question.explanation().isBlank()
            ? null
            : question.explanation(),
        question.questionAssetId(),
        question.explanationAssetId());
  }
}
