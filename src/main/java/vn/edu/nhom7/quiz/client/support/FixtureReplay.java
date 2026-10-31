package vn.edu.nhom7.quiz.client.support;

import com.fasterxml.jackson.databind.*;
import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class FixtureReplay implements AutoCloseable {
  private final ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var t = new Thread(r, "fixture-replay");
            t.setDaemon(true);
            return t;
          });

  public void play(long participantUserId, Consumer<Envelope> consumer) {
    try {
      var codec = new ProtocolCodec();
      JsonNode fixture =
          codec.mapper().readTree(getClass().getResourceAsStream("/protocol/demo-match-v1.json"));
      consumer.accept(
          codec.envelope(
              MessageType.LOGIN_RESULT,
              null,
              null,
              null,
              null,
              new Payloads.LoginResult(
                  new Payloads.Profile(
                      participantUserId, "demo", "Minh", "avatar-01", 0, 0, 0, 0, 0))));
      var quiz =
          new Payloads.QuizSummary(
              1, "Kiến thức chung", 1, "Tổng hợp", "quiz-cover-general", "AVAILABLE", 10);
      consumer.accept(
          codec.envelope(
              MessageType.QUIZ_LIST,
              null,
              null,
              null,
              null,
              new Payloads.QuizList(
                  1,
                  20,
                  1,
                  java.util.List.of(new Payloads.Category(1, "Tổng hợp", 1)),
                  java.util.List.of(quiz))));
      consumer.accept(
          codec.envelope(
              MessageType.ONLINE_LIST,
              null,
              null,
              null,
              null,
              new Payloads.OnlineList(
                  1,
                  java.util.List.of(
                      new Payloads.OnlineUser(101, "Minh", "avatar-01", 0, "FREE"),
                      new Payloads.OnlineUser(102, "Phúc", "avatar-02", 0, "FREE")))));
      long delay = 1500;
      for (JsonNode entry : fixture.path("events")) {
        if (entry.hasNonNull("participantUserId")
            && entry.path("participantUserId").asLong() != participantUserId) continue;
        Envelope e = codec.decode(codec.mapper().writeValueAsBytes(entry.path("envelope")));
        timer.schedule(() -> consumer.accept(e), delay, TimeUnit.MILLISECONDS);
        delay +=
            switch (e.type()) {
              case QUESTION_OPEN -> 2500;
              case QUESTION_RESULT, ROUND_LEADERBOARD -> 1800;
              case MATCH_COUNTDOWN, ROUND_COUNTDOWN -> 1000;
              case QUESTION, MATCH_START -> 1000;
              default -> 250;
            };
      }
    } catch (Exception e) {
      throw new IllegalStateException("Invalid public demo fixture", e);
    }
  }

  public void close() {
    timer.shutdownNow();
  }
}
