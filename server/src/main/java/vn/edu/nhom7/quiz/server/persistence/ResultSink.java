package vn.edu.nhom7.quiz.server.persistence;

import java.util.UUID;
import vn.edu.nhom7.quiz.server.domain.MatchSummary;

public interface ResultSink {
  void submit(MatchSummary summary);

  boolean canCreateMatch();

  boolean reserveMatch(UUID matchId);

  void cancelReservation(UUID matchId);
}
