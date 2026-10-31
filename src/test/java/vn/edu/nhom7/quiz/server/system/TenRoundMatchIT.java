package vn.edu.nhom7.quiz.server.system;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.nhom7.quiz.server.support.SystemTestSupport.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.*;
import vn.edu.nhom7.quiz.server.support.*;

class TenRoundMatchIT {
  @Test
  void tenRoundsThroughTcpPersistTwentyOutcomesAndRematchFreshState() throws Exception {
    var clock = new FakeGameClock();
    var timer = new FakeGameScheduler(clock);
    try (var runtime = new ServerRuntime(TestDatabase.factory(), clock, timer)) {
      runtime.start("127.0.0.1", 0);
      try (var one = login(runtime);
          var two = login(runtime)) {
        UUID match = challenge(one, two);
        ready(one, match);
        ready(two, match);
        one.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        two.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        timer.advance(Duration.ofSeconds(3));
        Set<Long> questions = new HashSet<>();
        Map<String, Integer> types = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
          Envelope q = one.socket().await(MessageType.QUESTION, WAIT);
          two.socket().await(MessageType.QUESTION, WAIT);
          assertEquals(i, q.payload().path("roundIndex").asInt());
          questions.add(q.payload().path("question").path("questionId").asLong());
          types.merge(q.payload().path("question").path("questionType").asText(), 1, Integer::sum);
          roundReady(one, q);
          roundReady(two, q);
          one.socket().await(MessageType.ROUND_COUNTDOWN, WAIT);
          two.socket().await(MessageType.ROUND_COUNTDOWN, WAIT);
          timer.advance(Duration.ofSeconds(2));
          one.socket().await(MessageType.QUESTION_OPEN, WAIT);
          two.socket().await(MessageType.QUESTION_OPEN, WAIT);
          answer(one, q);
          one.socket().await(MessageType.ANSWER_ACK, WAIT);
          answer(two, q);
          two.socket().await(MessageType.ANSWER_ACK, WAIT);
          one.socket().await(MessageType.QUESTION_RESULT, WAIT);
          two.socket().await(MessageType.QUESTION_RESULT, WAIT);
          timer.advance(Duration.ofSeconds(2));
          var board = one.socket().await(MessageType.ROUND_LEADERBOARD, WAIT);
          two.socket().await(MessageType.ROUND_LEADERBOARD, WAIT);
          assertEquals(2, board.payload().path("standings").size());
          for (var row : board.payload().path("standings"))
            assertEquals(
                row.path("totalScore").asInt(),
                row.path("scoreBefore").asInt() + row.path("earnedPoints").asInt());
          timer.advance(Duration.ofSeconds(3));
        }
        Envelope result = one.socket().await(MessageType.MATCH_RESULT, WAIT);
        two.socket().await(MessageType.MATCH_RESULT, WAIT);
        assertEquals(10, result.payload().path("completedRounds").asInt());
        assertEquals(10, questions.size());
        assertEquals(
            Map.of("SINGLE_CHOICE", 4, "MULTIPLE_CHOICE", 2, "TRUE_FALSE", 2, "SHORT_ANSWER", 2),
            types);
        assertEquals(Payloads.REVIEW_PAGE_SIZE, result.payload().path("review").size());
        assertEquals(10, result.payload().path("reviewTotalItems").asInt());
        var reviewedRounds = new HashSet<Integer>();
        for (var review : result.payload().path("review"))
          assertTrue(reviewedRounds.add(review.path("roundIndex").asInt()));
        for (int page = 2;
            page <= (10 + Payloads.REVIEW_PAGE_SIZE - 1) / Payloads.REVIEW_PAGE_SIZE;
            page++) {
          one.socket()
              .send(
                  command(
                      MessageType.LIVE_REVIEW_REQUEST,
                      match,
                      null,
                      new Payloads.LiveReviewRequest(page)));
          var reviews = one.socket().await(MessageType.LIVE_REVIEW, WAIT).payload();
          assertEquals(page, reviews.path("page").asInt());
          assertEquals(10, reviews.path("totalItems").asInt());
          for (var review : reviews.path("review")) {
            assertTrue(reviewedRounds.add(review.path("roundIndex").asInt()));
            assertTrue(review.path("revealed").asBoolean());
            assertEquals(2, review.path("outcomes").size());
          }
        }
        assertEquals(
            java.util.stream.IntStream.rangeClosed(1, 10)
                .boxed()
                .collect(java.util.stream.Collectors.toSet()),
            reviewedRounds);
        one.socket().await(MessageType.MATCH_SAVE_STATUS, WAIT);
        assertEquals(0, runtime.persistence.awaitPending(Duration.ofSeconds(8)));
        try (var c = TestDatabase.factory().open();
            var s =
                c.prepareStatement(
                    "SELECT COUNT(*) FROM MATCH_ANSWERS a JOIN MATCH_QUESTIONS r ON"
                        + " r.round_id=a.round_id WHERE r.match_id=?")) {
          s.setString(1, match.toString());
          try (var rs = s.executeQuery()) {
            rs.next();
            assertEquals(20, rs.getInt(1));
          }
        }
        one.socket()
            .send(command(MessageType.REMATCH_REQUEST, match, null, new Payloads.RematchRequest()));
        one.socket().await(MessageType.REMATCH_STATUS, WAIT);
        two.socket()
            .send(command(MessageType.REMATCH_REQUEST, match, null, new Payloads.RematchRequest()));
        Envelope next = one.socket().await(MessageType.MATCH_START, WAIT);
        two.socket().await(MessageType.MATCH_START, WAIT);
        assertNotEquals(match, next.matchId());
        assertEquals(0, next.payload().path("players").get(0).path("totalScore").asInt());
      }
    }
  }
}
