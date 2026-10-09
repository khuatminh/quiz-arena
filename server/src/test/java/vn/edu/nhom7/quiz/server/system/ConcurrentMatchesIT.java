package vn.edu.nhom7.quiz.server.system;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.nhom7.quiz.server.support.SystemTestSupport.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.*;
import vn.edu.nhom7.quiz.server.support.*;

class ConcurrentMatchesIT {
  @Test
  void twoMatchesKeepChatAnswersAndTerminationIsolated() throws Exception {
    var clock = new FakeGameClock();
    var timer = new FakeGameScheduler(clock);
    try (var runtime = new ServerRuntime(TestDatabase.factory(), clock, timer)) {
      runtime.start("127.0.0.1", 0);
      try (var one = login(runtime);
          var two = login(runtime);
          var three = login(runtime);
          var four = login(runtime)) {
        var first = challenge(one, two);
        var second = challenge(three, four);
        assertNotEquals(first, second);
        ready(one, first);
        ready(two, first);
        ready(three, second);
        ready(four, second);
        for (var player : new Player[] {one, two, three, four})
          player.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        timer.advance(Duration.ofSeconds(3));
        var q1 = one.socket().await(MessageType.QUESTION, WAIT);
        two.socket().await(MessageType.QUESTION, WAIT);
        var q2 = three.socket().await(MessageType.QUESTION, WAIT);
        four.socket().await(MessageType.QUESTION, WAIT);
        roundReady(one, q1);
        roundReady(two, q1);
        roundReady(three, q2);
        roundReady(four, q2);
        for (var player : new Player[] {one, two, three, four})
          player.socket().await(MessageType.ROUND_COUNTDOWN, WAIT);
        timer.advance(Duration.ofSeconds(2));
        for (var player : new Player[] {one, two, three, four})
          player.socket().await(MessageType.QUESTION_OPEN, WAIT);
        three.socket().send(command(MessageType.CHAT, first, null, new Payloads.Chat("forbidden")));
        assertEquals(
            "NOT_MATCH_MEMBER",
            three.socket().await(MessageType.ERROR, WAIT).payload().path("code").asText());
        one.socket()
            .send(command(MessageType.CHAT, first, null, new Payloads.Chat("only first match")));
        assertEquals(first, one.socket().await(MessageType.CHAT_MESSAGE, WAIT).matchId());
        assertEquals(first, two.socket().await(MessageType.CHAT_MESSAGE, WAIT).matchId());
        answer(one, q1);
        answer(two, q1);
        one.socket().await(MessageType.QUESTION_RESULT, WAIT);
        two.socket().await(MessageType.QUESTION_RESULT, WAIT);
        assertEquals(
            vn.edu.nhom7.quiz.server.match.MatchPhase.ANSWERING,
            runtime.matches.find(second).orElseThrow().phase());
        three
            .socket()
            .send(command(MessageType.EXIT_MATCH, second, null, new Payloads.ExitMatch()));
        var result = four.socket().await(MessageType.MATCH_RESULT, WAIT);
        assertEquals("FORFEIT", result.payload().path("finishReason").asText());
        assertEquals(four.userId(), result.payload().path("winnerUserId").asLong());
        assertEquals(
            vn.edu.nhom7.quiz.server.match.MatchPhase.REVEAL,
            runtime.matches.find(first).orElseThrow().phase());
      }
    }
  }
}
