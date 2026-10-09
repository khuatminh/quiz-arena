package vn.edu.nhom7.quiz.common.protocol;

import java.util.*;

public final class PayloadRegistry {
  public enum Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
  }

  private static final Map<MessageType, Class<?>> TYPES =
      Map.ofEntries(
          Map.entry(MessageType.HELLO, Payloads.Hello.class),
          Map.entry(MessageType.HELLO_ACK, Payloads.HelloAck.class),
          Map.entry(MessageType.PING, Payloads.Ping.class),
          Map.entry(MessageType.PONG, Payloads.Pong.class),
          Map.entry(MessageType.REGISTER, Payloads.Register.class),
          Map.entry(MessageType.REGISTER_RESULT, Payloads.RegisterResult.class),
          Map.entry(MessageType.LOGIN, Payloads.Login.class),
          Map.entry(MessageType.LOGIN_RESULT, Payloads.LoginResult.class),
          Map.entry(MessageType.LOGOUT, Payloads.Logout.class),
          Map.entry(MessageType.LOGOUT_ACK, Payloads.LogoutAck.class),
          Map.entry(MessageType.ONLINE_LIST_REQUEST, Payloads.OnlineListRequest.class),
          Map.entry(MessageType.ONLINE_LIST, Payloads.OnlineList.class),
          Map.entry(MessageType.QUIZ_LIST_REQUEST, Payloads.QuizListRequest.class),
          Map.entry(MessageType.QUIZ_LIST, Payloads.QuizList.class),
          Map.entry(MessageType.QUIZ_DETAIL_REQUEST, Payloads.QuizDetailRequest.class),
          Map.entry(MessageType.QUIZ_DETAIL, Payloads.QuizDetail.class),
          Map.entry(MessageType.CHALLENGE, Payloads.Challenge.class),
          Map.entry(MessageType.CHALLENGE_ACK, Payloads.ChallengeAck.class),
          Map.entry(MessageType.CHALLENGE_RECEIVED, Payloads.ChallengeReceived.class),
          Map.entry(MessageType.CHALLENGE_ACCEPT, Payloads.ChallengeAccept.class),
          Map.entry(MessageType.CHALLENGE_REJECT, Payloads.ChallengeReject.class),
          Map.entry(MessageType.CHALLENGE_CANCEL, Payloads.ChallengeCancel.class),
          Map.entry(MessageType.CHALLENGE_CLOSED, Payloads.ChallengeClosed.class),
          Map.entry(MessageType.MATCH_START, Payloads.MatchStart.class),
          Map.entry(MessageType.MATCH_READY, Payloads.MatchReady.class),
          Map.entry(MessageType.READY_STATUS, Payloads.ReadyStatus.class),
          Map.entry(MessageType.MATCH_COUNTDOWN, Payloads.MatchCountdown.class),
          Map.entry(MessageType.QUESTION, Payloads.Question.class),
          Map.entry(MessageType.QUESTION_READY, Payloads.QuestionReady.class),
          Map.entry(MessageType.ROUND_COUNTDOWN, Payloads.RoundCountdown.class),
          Map.entry(MessageType.QUESTION_OPEN, Payloads.QuestionOpen.class),
          Map.entry(MessageType.ANSWER, Payloads.Answer.class),
          Map.entry(MessageType.ANSWER_ACK, Payloads.AnswerAck.class),
          Map.entry(MessageType.ANSWER_STATUS, Payloads.AnswerStatus.class),
          Map.entry(MessageType.QUESTION_RESULT, Payloads.QuestionResult.class),
          Map.entry(MessageType.ROUND_LEADERBOARD, Payloads.RoundLeaderboard.class),
          Map.entry(MessageType.CHAT, Payloads.Chat.class),
          Map.entry(MessageType.CHAT_MESSAGE, Payloads.ChatMessage.class),
          Map.entry(MessageType.MATCH_RESULT, Payloads.MatchResult.class),
          Map.entry(MessageType.MATCH_SAVE_STATUS, Payloads.MatchSaveStatus.class),
          Map.entry(MessageType.REMATCH_REQUEST, Payloads.RematchRequest.class),
          Map.entry(MessageType.REMATCH_RESPONSE, Payloads.RematchResponse.class),
          Map.entry(MessageType.REMATCH_STATUS, Payloads.RematchStatus.class),
          Map.entry(MessageType.EXIT_MATCH, Payloads.ExitMatch.class),
          Map.entry(MessageType.EXIT_ACK, Payloads.ExitAck.class),
          Map.entry(MessageType.RESULT_SESSION_CLOSED, Payloads.ResultSessionClosed.class),
          Map.entry(MessageType.MATCH_SNAPSHOT_REQUEST, Payloads.MatchSnapshotRequest.class),
          Map.entry(MessageType.MATCH_SNAPSHOT, Payloads.MatchSnapshot.class),
          Map.entry(MessageType.RANKING_REQUEST, Payloads.RankingRequest.class),
          Map.entry(MessageType.RANKING, Payloads.Ranking.class),
          Map.entry(MessageType.RANKING_INVALIDATED, Payloads.RankingInvalidated.class),
          Map.entry(MessageType.PROFILE_REQUEST, Payloads.ProfileRequest.class),
          Map.entry(MessageType.PROFILE, Payloads.Profile.class),
          Map.entry(MessageType.HISTORY_REQUEST, Payloads.HistoryRequest.class),
          Map.entry(MessageType.HISTORY, Payloads.History.class),
          Map.entry(MessageType.MATCH_DETAIL_REQUEST, Payloads.MatchDetailRequest.class),
          Map.entry(MessageType.MATCH_DETAIL, Payloads.MatchDetail.class),
          Map.entry(MessageType.AUTHOR_REQUEST, CommunityPayloads.AuthorRequest.class),
          Map.entry(MessageType.AUTHOR_RESULT, CommunityPayloads.AuthorResult.class),
          Map.entry(MessageType.MEDIA_UPLOAD_START, MediaPayloads.UploadStart.class),
          Map.entry(MessageType.MEDIA_UPLOAD_STARTED, MediaPayloads.UploadStarted.class),
          Map.entry(MessageType.MEDIA_UPLOAD_CHUNK, MediaPayloads.UploadChunk.class),
          Map.entry(MessageType.MEDIA_UPLOAD_ACK, MediaPayloads.UploadAck.class),
          Map.entry(MessageType.MEDIA_REQUEST, MediaPayloads.MediaRequest.class),
          Map.entry(MessageType.MEDIA_CHUNK, MediaPayloads.MediaChunk.class),
          Map.entry(MessageType.LIVE_REVIEW_REQUEST, Payloads.LiveReviewRequest.class),
          Map.entry(MessageType.LIVE_REVIEW, Payloads.LiveReview.class),
          Map.entry(MessageType.ERROR, Payloads.Error.class));
  private static final Set<MessageType> CLIENT =
      EnumSet.of(
          MessageType.AUTHOR_REQUEST,
          MessageType.MEDIA_UPLOAD_START,
          MessageType.MEDIA_UPLOAD_CHUNK,
          MessageType.MEDIA_REQUEST,
          MessageType.LIVE_REVIEW_REQUEST,
          MessageType.HELLO,
          MessageType.PING,
          MessageType.REGISTER,
          MessageType.LOGIN,
          MessageType.LOGOUT,
          MessageType.ONLINE_LIST_REQUEST,
          MessageType.QUIZ_LIST_REQUEST,
          MessageType.QUIZ_DETAIL_REQUEST,
          MessageType.CHALLENGE,
          MessageType.CHALLENGE_ACCEPT,
          MessageType.CHALLENGE_REJECT,
          MessageType.CHALLENGE_CANCEL,
          MessageType.MATCH_READY,
          MessageType.QUESTION_READY,
          MessageType.ANSWER,
          MessageType.CHAT,
          MessageType.REMATCH_REQUEST,
          MessageType.REMATCH_RESPONSE,
          MessageType.EXIT_MATCH,
          MessageType.MATCH_SNAPSHOT_REQUEST,
          MessageType.RANKING_REQUEST,
          MessageType.PROFILE_REQUEST,
          MessageType.HISTORY_REQUEST,
          MessageType.MATCH_DETAIL_REQUEST);

  public static Class<?> payloadClass(MessageType type) {
    return TYPES.get(type);
  }

  public static Direction direction(MessageType type) {
    return CLIENT.contains(type) ? Direction.CLIENT_TO_SERVER : Direction.SERVER_TO_CLIENT;
  }

  public static Set<MessageType> types() {
    return TYPES.keySet();
  }
}
