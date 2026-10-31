package vn.edu.nhom7.quiz.server.match;

public enum MatchPhase {
  WAITING_READY,
  MATCH_COUNTDOWN,
  QUESTION_PREPARING,
  ROUND_COUNTDOWN,
  ANSWERING,
  REVEAL,
  LEADERBOARD,
  RESULT,
  CLOSED,
  CANCELLED
}
