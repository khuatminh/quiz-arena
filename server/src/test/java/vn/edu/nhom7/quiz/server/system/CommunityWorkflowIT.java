package vn.edu.nhom7.quiz.server.system;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.nhom7.quiz.server.support.SystemTestSupport.*;

import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.ServerRuntime;
import vn.edu.nhom7.quiz.server.media.MediaService;
import vn.edu.nhom7.quiz.server.support.*;

/** Two independent TCP clients author, upload, publish, duel and inspect committed history. */
class CommunityWorkflowIT {
  @TempDir Path media;
  final ObjectMapper json = new ObjectMapper();

  JsonNode author(Player p, String action, long id, JsonNode data) throws Exception {
    var command =
        command(
            MessageType.AUTHOR_REQUEST,
            null,
            null,
            new CommunityPayloads.AuthorRequest(action, id, data));
    p.socket().send(command);
    return p.socket()
        .await(MessageType.AUTHOR_RESULT, command.requestId(), WAIT)
        .payload()
        .path("data");
  }

  String upload(Player p, byte[] bytes) throws Exception {
    var start =
        command(
            MessageType.MEDIA_UPLOAD_START,
            null,
            null,
            new MediaPayloads.UploadStart("image/png", bytes.length, MediaService.sha256(bytes)));
    p.socket().send(start);
    UUID transfer =
        UUID.fromString(
            p.socket()
                .await(MessageType.MEDIA_UPLOAD_STARTED, start.requestId(), WAIT)
                .payload()
                .path("transferId")
                .asText());
    var chunk =
        command(
            MessageType.MEDIA_UPLOAD_CHUNK,
            null,
            null,
            new MediaPayloads.UploadChunk(transfer, 0, Base64.getEncoder().encodeToString(bytes)));
    p.socket().send(chunk);
    return p.socket()
        .await(MessageType.MEDIA_UPLOAD_ACK, chunk.requestId(), WAIT)
        .payload()
        .path("mediaId")
        .asText();
  }

  Envelope media(Player p, String id, MessageType response) throws Exception {
    var get = command(MessageType.MEDIA_REQUEST, null, null, new MediaPayloads.MediaRequest(id, 0));
    p.socket().send(get);
    return p.socket().await(response, get.requestId(), WAIT);
  }

  @Test
  void authorUploadPublishDuelRevealAndUnrankedHistory() throws Exception {
    String prior = System.getProperty("quiz.mediaDir");
    System.setProperty("quiz.mediaDir", media.toString());
    var clock = new FakeGameClock();
    var timer = new FakeGameScheduler(clock);
    try (var runtime = new ServerRuntime(TestDatabase.factory(), clock, timer)) {
      runtime.start("127.0.0.1", 0);
      try (var owner = login(runtime);
          var opponent = login(runtime);
          var outsider = login(runtime)) {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        String questionImage = upload(owner, bytes.toByteArray()),
            revealImage = upload(owner, bytes.toByteArray());
        long quiz = author(owner, "CREATE", 0, json.createObjectNode()).path("quizId").asLong();
        author(
            owner,
            "SAVE_META",
            quiz,
            json.createObjectNode().put("title", "Ảnh và đúng/sai").put("categoryId", 1));
        var q =
            json.createObjectNode()
                .put("questionType", "TRUE_FALSE")
                .put("content", "Đây là một ảnh PNG?")
                .put("answerKey", true)
                .put("explanation", "PNG được chọn từ máy.")
                .put("questionAssetId", questionImage)
                .put("explanationAssetId", revealImage);
        q.set("options", json.createArrayNode());
        author(
            owner,
            "SAVE_QUESTION",
            quiz,
            json.createObjectNode().put("index", 0).set("question", q));
        author(owner, "PUBLISH", quiz, json.createObjectNode());
        boolean listed = false;
        for (int page = 1; page <= 100 && !listed; page++) {
          var list =
              command(
                  MessageType.QUIZ_LIST_REQUEST,
                  null,
                  null,
                  new Payloads.QuizListRequest(null, page, 10));
          opponent.socket().send(list);
          var payload =
              opponent.socket().await(MessageType.QUIZ_LIST, list.requestId(), WAIT).payload();
          var items = payload.path("items");
          listed =
              java.util.stream.StreamSupport.stream(items.spliterator(), false)
                  .anyMatch(
                      z ->
                          z.path("quizId").asLong() == quiz
                              && z.path("quizSource").asText().equals("COMMUNITY")
                              && z.path("totalRounds").asInt() == 1);
          if (items.isEmpty()) break;
        }
        assertTrue(listed);
        assertEquals(
            "FORBIDDEN",
            media(opponent, revealImage, MessageType.ERROR).payload().path("code").asText());
        owner
            .socket()
            .send(
                command(
                    MessageType.CHALLENGE,
                    null,
                    null,
                    new Payloads.Challenge(opponent.userId(), quiz)));
        var invite = opponent.socket().await(MessageType.CHALLENGE_RECEIVED, WAIT);
        owner.socket().await(MessageType.CHALLENGE_ACK, WAIT);
        opponent
            .socket()
            .send(
                command(
                    MessageType.CHALLENGE_ACCEPT,
                    null,
                    null,
                    new Payloads.ChallengeAccept(
                        UUID.fromString(invite.payload().path("challengeId").asText()))));
        var start = owner.socket().await(MessageType.MATCH_START, WAIT);
        opponent.socket().await(MessageType.MATCH_START, WAIT);
        assertEquals(1, start.payload().path("totalRounds").asInt());
        UUID match = start.matchId();
        ready(owner, match);
        ready(opponent, match);
        owner.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        opponent.socket().await(MessageType.MATCH_COUNTDOWN, WAIT);
        timer.advance(Duration.ofSeconds(3));
        var question = owner.socket().await(MessageType.QUESTION, WAIT);
        opponent.socket().await(MessageType.QUESTION, WAIT);
        assertArrayEquals(
            bytes.toByteArray(),
            Base64.getDecoder()
                .decode(
                    media(opponent, questionImage, MessageType.MEDIA_CHUNK)
                        .payload()
                        .path("data")
                        .asText()));
        assertEquals(
            "FORBIDDEN",
            media(opponent, revealImage, MessageType.ERROR).payload().path("code").asText());
        roundReady(owner, question);
        roundReady(opponent, question);
        owner.socket().await(MessageType.ROUND_COUNTDOWN, WAIT);
        opponent.socket().await(MessageType.ROUND_COUNTDOWN, WAIT);
        timer.advance(Duration.ofSeconds(2));
        owner.socket().await(MessageType.QUESTION_OPEN, WAIT);
        opponent.socket().await(MessageType.QUESTION_OPEN, WAIT);
        answer(owner, question);
        owner.socket().await(MessageType.ANSWER_ACK, WAIT);
        assertEquals(
            "FORBIDDEN",
            media(opponent, revealImage, MessageType.ERROR).payload().path("code").asText());
        answer(opponent, question);
        opponent.socket().await(MessageType.ANSWER_ACK, WAIT);
        owner.socket().await(MessageType.QUESTION_RESULT, WAIT);
        opponent.socket().await(MessageType.QUESTION_RESULT, WAIT);
        assertEquals(
            revealImage,
            media(opponent, revealImage, MessageType.MEDIA_CHUNK)
                .payload()
                .path("mediaId")
                .asText());
        assertEquals(
            "FORBIDDEN",
            media(outsider, revealImage, MessageType.ERROR).payload().path("code").asText());
        author(owner, "UNPUBLISH", quiz, json.createObjectNode());
        timer.advance(Duration.ofSeconds(2));
        owner.socket().await(MessageType.ROUND_LEADERBOARD, WAIT);
        opponent.socket().await(MessageType.ROUND_LEADERBOARD, WAIT);
        timer.advance(Duration.ofSeconds(3));
        var result = opponent.socket().await(MessageType.MATCH_RESULT, WAIT);
        assertFalse(result.payload().path("ranked").asBoolean(true));
        assertEquals(1, result.payload().path("completedRounds").asInt());
        assertEquals(0, runtime.persistence.awaitPending(Duration.ofSeconds(8)));
        var profile =
            command(MessageType.PROFILE_REQUEST, null, null, new Payloads.ProfileRequest());
        opponent.socket().send(profile);
        var stats =
            opponent.socket().await(MessageType.PROFILE, profile.requestId(), WAIT).payload();
        assertEquals(0, stats.path("totalScore").asInt());
        assertEquals(0, stats.path("totalMatches").asInt());
        var history =
            command(
                MessageType.MATCH_DETAIL_REQUEST,
                null,
                null,
                new Payloads.MatchDetailRequest(match, 1));
        opponent.socket().send(history);
        var detail =
            opponent.socket().await(MessageType.MATCH_DETAIL, history.requestId(), WAIT).payload();
        assertFalse(detail.path("summary").path("ranked").asBoolean(true));
        assertEquals(1, detail.path("review").size());
        assertEquals(
            revealImage,
            media(opponent, revealImage, MessageType.MEDIA_CHUNK)
                .payload()
                .path("mediaId")
                .asText());
      }
    } finally {
      if (prior == null) System.clearProperty("quiz.mediaDir");
      else System.setProperty("quiz.mediaDir", prior);
    }
  }
}
