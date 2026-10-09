package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class Payloads {
  private Payloads() {}

  public record Hello(String clientVersion, String assetPackVersion) {}

  public record HelloAck(
      UUID connectionId,
      int protocolVersion,
      long serverTimeMs,
      long heartbeatIntervalMs,
      String assetPackVersion) {}

  public record Ping(long clientTimeMs) {}

  public record Pong(long clientTimeMs, long serverTimeMs) {}

  public record Register(String username, String password, String displayName, String avatarId) {}

  public record RegisterResult(long userId, String username) {}

  public record Login(String username, String password) {}

  public record LoginResult(Profile profile) {}

  public record Logout() {}

  public record LogoutAck() {}

  public record OnlineListRequest() {}

  public record OnlineList(long revision, List<OnlineUser> users) {}

  public record QuizListRequest(Long categoryId, int page, int pageSize) {}

  public record QuizList(
      int page,
      int pageSize,
      long totalItems,
      List<Category> categories,
      List<QuizSummary> items) {}

  public record QuizDetailRequest(long quizId) {}

  public record QuizDetail(
      QuizSummary quiz, String description, Map<String, Integer> typeCounts, JsonNode rules) {}

  public record Challenge(long targetUserId, long quizId) {}

  public record ChallengeAck(
      UUID challengeId, long targetUserId, QuizSummary quiz, long expiresAtMs) {}

  public record ChallengeReceived(
      UUID challengeId, Profile challengerProfile, QuizSummary quiz, long expiresAtMs) {}

  public record ChallengeAccept(UUID challengeId) {}

  public record ChallengeReject(UUID challengeId) {}

  public record ChallengeCancel(UUID challengeId) {}

  public record ChallengeClosed(UUID challengeId, String status, String reason, UUID matchId) {}

  public record MatchStart(
      QuizSummary quiz,
      List<PlayerMatchSummary> players,
      int totalRounds,
      long readyDeadlineAtMs) {}

  public record MatchReady() {}

  public record ReadyStatus(List<Long> readyUserIds, String phase) {}

  public record MatchCountdown(long durationMs, long phaseEndsAtMs, long serverTimeMs) {}

  public record Question(
      PublicQuestion question, int roundIndex, int totalRounds, long readyDeadlineAtMs) {}

  public record QuestionReady(long questionId) {}

  public record RoundCountdown(long durationMs, long phaseEndsAtMs, long serverTimeMs) {}

  public record QuestionOpen(
      long questionId, long remainingMs, long deadlineAtMs, long serverTimeMs) {}

  public record Answer(long questionId, JsonNode answer) {}

  public record AnswerAck(
      long questionId, boolean accepted, long answerTimeMs, UUID acceptedRequestId) {}

  public record AnswerStatus(long userId, boolean answered) {}

  public record QuestionResult(
      long questionId,
      JsonNode correctAnswer,
      String explanation,
      String explanationAssetId,
      List<AnswerOutcome> outcomes,
      long durationMs,
      long phaseEndsAtMs,
      long serverTimeMs) {}

  public record RoundLeaderboard(
      int roundIndex,
      List<Standing> standings,
      long durationMs,
      long phaseEndsAtMs,
      long serverTimeMs) {}

  public record Chat(String text) {}

  public record ChatMessage(
      UUID chatMessageId, long senderUserId, String displayName, String text, long sentAtMs) {}

  public record MatchResult(
      String finishReason,
      String reasonCode,
      Long winnerUserId,
      List<PlayerMatchSummary> players,
      int completedRounds,
      int openedRounds,
      List<QuestionReview> review,
      String persistenceStatus,
      long resultExpiresAtMs) {}

  public record MatchSaveStatus(String status, boolean retryable, Long savedAtMs) {}

  public record RematchRequest() {}

  public record RematchResponse(boolean accept) {}

  public record RematchStatus(
      String status, long requesterUserId, Long expiresAtMs, UUID newMatchId) {}

  public record ExitMatch() {}

  public record ExitAck(String reason, String lobbyStatus) {}

  public record ResultSessionClosed(String reason) {}

  public record MatchSnapshotRequest() {}

  public record MatchSnapshot(
      String phase,
      long phaseVersion,
      long serverTimeMs,
      long phaseRemainingMs,
      QuizSummary quiz,
      List<PlayerMatchSummary> players,
      PublicQuestion question,
      Integer roundIndex,
      Integer totalRounds,
      JsonNode acceptedAnswer,
      List<Long> answeredUserIds,
      QuestionResult latestResult,
      List<Standing> standings) {}

  public record RankingRequest(int page, int pageSize) {}

  public record Ranking(
      int page, int pageSize, long totalItems, List<RankingEntry> entries, RankingEntry myRank) {}

  public record RankingInvalidated(long revision, String reason) {}

  public record ProfileRequest() {}

  public record Profile(
      long userId,
      String username,
      String displayName,
      String avatarId,
      long totalScore,
      int totalMatches,
      int wins,
      int losses,
      int draws) {}

  public record HistoryRequest(int page, int pageSize) {}

  public record History(int page, int pageSize, long totalItems, List<MatchHistorySummary> items) {}

  public record MatchDetailRequest(UUID historyMatchId) {}

  public record MatchDetail(MatchHistorySummary summary, List<QuestionReview> review) {}

  public record Error(String code, String message, boolean retryable, JsonNode details) {}

  public record Player(
      long userId,
      String displayName,
      String avatarId,
      int totalScore,
      int correctCount,
      int rank) {}

  public record OnlineUser(
      long userId, String displayName, String avatarId, long totalScore, String status) {}

  public record Category(long categoryId, String categoryName, int displayOrder) {}

  public record QuizSummary(
      long quizId,
      String title,
      long categoryId,
      String categoryName,
      String coverAssetId,
      String availability,
      int totalRounds) {}

  public record PublicQuestion(
      long questionId,
      String questionType,
      String content,
      List<Option> options,
      String questionAssetId,
      long timeLimitMs) {}

  public record Option(String id, String text) {}

  public record PlayerMatchSummary(
      long userId,
      String displayName,
      String avatarId,
      int totalScore,
      int correctCount,
      int rank) {}

  public record AnswerOutcome(
      long userId,
      JsonNode answer,
      String outcome,
      Boolean correct,
      Long answerTimeMs,
      int earnedPoints,
      int scoreBefore,
      int totalScore) {}

  public record QuestionReview(
      UUID roundId,
      int roundIndex,
      PublicQuestion question,
      JsonNode correctAnswer,
      String explanation,
      String explanationAssetId,
      List<AnswerOutcome> outcomes,
      boolean revealed) {}

  public record Standing(
      long userId,
      String displayName,
      String avatarId,
      int rank,
      int previousRank,
      int scoreBefore,
      int earnedPoints,
      int totalScore,
      int correctCount,
      String outcome) {}

  public record MatchHistorySummary(
      UUID matchId,
      long quizId,
      String quizTitle,
      long opponentUserId,
      String opponentName,
      int ownScore,
      int opponentScore,
      String outcome,
      String finishReason,
      String reasonCode,
      long startedAtMs,
      long endedAtMs) {}

  public record RankingEntry(
      int rank,
      long userId,
      String displayName,
      String avatarId,
      long totalScore,
      int wins,
      int totalMatches) {}
}
