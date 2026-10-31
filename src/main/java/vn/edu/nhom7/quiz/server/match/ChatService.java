package vn.edu.nhom7.quiz.server.match;

import vn.edu.nhom7.quiz.common.protocol.Envelope;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/** Entry point using the aggregate's membership, deduplication and independent chat limiter. */
public final class ChatService {
  private final MatchGateway gateway;

  public ChatService(MatchGateway gateway) {
    this.gateway = gateway;
  }

  public void handle(SessionContext caller, Envelope chat) {
    gateway.handle(caller, chat);
  }
}
