package vn.edu.nhom7.quiz.server.challenge;

import java.util.UUID;
import vn.edu.nhom7.quiz.common.protocol.Payloads;
import vn.edu.nhom7.quiz.server.session.SessionContext;

public final class Challenge {
  public enum State {
    PENDING,
    CREATING_MATCH,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    INVALIDATED
  }

  final UUID id = UUID.randomUUID();
  final SessionContext sender, recipient;
  final Payloads.QuizSummary quiz;
  final long expiresAt, expiresNano;
  State state = State.PENDING;
  UUID newMatchId;

  Challenge(
      SessionContext sender,
      SessionContext recipient,
      Payloads.QuizSummary quiz,
      long expiresAt,
      long expiresNano) {
    this.sender = sender;
    this.recipient = recipient;
    this.quiz = quiz;
    this.expiresAt = expiresAt;
    this.expiresNano = expiresNano;
  }

  public UUID id() {
    return id;
  }

  public State state() {
    return state;
  }
}
