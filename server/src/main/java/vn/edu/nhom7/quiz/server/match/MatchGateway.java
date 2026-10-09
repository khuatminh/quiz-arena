package vn.edu.nhom7.quiz.server.match;

public interface MatchGateway {
  void handle(
      vn.edu.nhom7.quiz.server.session.SessionContext caller,
      vn.edu.nhom7.quiz.common.protocol.Envelope command);

  void onDisconnected(vn.edu.nhom7.quiz.server.session.SessionContext caller);
}
