package vn.edu.nhom7.quiz.server.quiz;

import static vn.edu.nhom7.quiz.server.quiz.DraftValidator.*;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import vn.edu.nhom7.quiz.common.protocol.CommunityPayloads.*;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.db.ConnectionFactory;

/** Owner-only mutable drafts and atomic immutable publication. All positions are zero based. */
public final class CommunityQuizService {
  private final ConnectionFactory connections;
  private final ObjectMapper json = new ObjectMapper();
  private final JdbcCommunityQuizRepository repository = new JdbcCommunityQuizRepository();

  private final CommunityQuizPublisher publisher = new CommunityQuizPublisher(repository);

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
    try (var connection = connections.open()) {
      connection.setAutoCommit(false);
      try {
        JsonNode result = dispatch(connection, userId, request.quizId(), action, data);
        connection.commit();
        return new AuthorResult(action, result);
      } catch (Exception e) {
        connection.rollback();
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

  private JsonNode dispatch(
      Connection connection, long userId, long quizId, String action, JsonNode data)
      throws Exception {
    long id = quizId;
    JsonNode result;
    if (action.equals("LIST")) result = repository.list(connection, userId, data);
    else {
      if (action.equals("CREATE")) id = repository.create(connection, userId, data);
      repository.owner(connection, id, userId);
      switch (action) {
        case "CREATE", "GET" -> {}
        case "SAVE_META" -> repository.saveMeta(connection, id, data);
        case "GET_QUESTION" -> {
          int index = index(data, "index");
          result =
              json.createObjectNode()
                  .put("index", index)
                  .set("question", repository.readQuestion(connection, id, index));
          return result;
        }
        case "SAVE_QUESTION" -> repository.saveQuestion(connection, id, userId, data);
        case "DELETE_QUESTION" -> repository.deleteQuestion(connection, id, index(data, "index"));
        case "MOVE_QUESTION" ->
            repository.moveQuestion(connection, id, index(data, "index"), index(data, "toIndex"));
        case "PUBLISH" -> publisher.publish(connection, id, userId);
        case "UNPUBLISH" -> repository.unpublish(connection, id);
        default -> throw invalid("Unknown authoring action");
      }
      result = repository.metadata(connection, id);
    }
    return result;
  }
}
