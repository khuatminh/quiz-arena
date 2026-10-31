package vn.edu.nhom7.quiz.server.match;

public record PhaseToken(
    java.util.UUID matchId, java.util.UUID roundId, MatchPhase phase, long version) {}
