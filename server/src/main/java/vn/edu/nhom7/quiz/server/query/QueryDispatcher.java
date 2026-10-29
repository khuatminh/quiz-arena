package vn.edu.nhom7.quiz.server.query;

import java.util.UUID;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.auth.AuthService;
import vn.edu.nhom7.quiz.server.media.MediaService;
import vn.edu.nhom7.quiz.server.network.MessageRouter;
import vn.edu.nhom7.quiz.server.quiz.*;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/**
 * Authenticated service queries. The router owns execution pools, rate limits and reply delivery.
 */
public final class QueryDispatcher implements MessageRouter.Queries {
  private final ProtocolCodec codec = new ProtocolCodec();
  private final JdbcQuizRepository quizzes;
  private final AuthService auth;
  private final RankingService ranking;
  private final HistoryService history;
  private final CommunityQuizService authoring;
  private final MediaService media;

  public QueryDispatcher(
      JdbcQuizRepository quizzes,
      AuthService auth,
      RankingService ranking,
      HistoryService history,
      CommunityQuizService authoring,
      MediaService media) {
    this.quizzes = quizzes;
    this.auth = auth;
    this.ranking = ranking;
    this.history = history;
    this.authoring = authoring;
    this.media = media;
  }

  @Override
  public Object execute(SessionContext caller, Envelope request) {
    var payload = request.payload();
    int page = payload.path("page").asInt(1), pageSize = payload.path("pageSize").asInt(20);
    return switch (request.type()) {
      case AUTHOR_REQUEST ->
          authoring.execute(
              caller.userId(), codec.payload(request, CommunityPayloads.AuthorRequest.class));
      case MEDIA_UPLOAD_START ->
          media.start(caller.userId(), codec.payload(request, MediaPayloads.UploadStart.class));
      case MEDIA_UPLOAD_CHUNK ->
          media.chunk(caller.userId(), codec.payload(request, MediaPayloads.UploadChunk.class));
      case MEDIA_REQUEST ->
          media.read(caller.userId(), codec.payload(request, MediaPayloads.MediaRequest.class));
      case QUIZ_LIST_REQUEST ->
          quizzes.list(
              payload.hasNonNull("categoryId") ? payload.get("categoryId").asLong() : null,
              page,
              pageSize);
      case QUIZ_DETAIL_REQUEST -> quizzes.detail(payload.path("quizId").asLong());
      case PROFILE_REQUEST -> auth.profile(caller.userId());
      case RANKING_REQUEST -> ranking.ranking(caller.userId(), page, pageSize);
      case HISTORY_REQUEST -> history.history(caller.userId(), page, pageSize);
      case MATCH_DETAIL_REQUEST ->
          history.detail(
              caller.userId(), UUID.fromString(payload.path("historyMatchId").asText()), page);
      default ->
          throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
              "INVALID_MESSAGE", "Unsupported query");
    };
  }
}
