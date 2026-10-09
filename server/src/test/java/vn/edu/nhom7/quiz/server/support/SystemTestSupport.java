package vn.edu.nhom7.quiz.server.support;

import java.io.*;
import java.time.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.ServerRuntime;

public final class SystemTestSupport {
  public static final Duration WAIT = Duration.ofSeconds(8);
  private static final ProtocolCodec CODEC = new ProtocolCodec();

  public record Player(SocketHarness socket, long userId) implements AutoCloseable {
    public void close() throws IOException {
      socket.close();
    }
  }

  public static Envelope command(MessageType type, UUID match, UUID round, Object payload) {
    return CODEC.envelope(type, UUID.randomUUID(), match, round, null, payload);
  }

  public static Player login(ServerRuntime runtime) throws IOException {
    SocketHarness socket = SocketHarness.connect("127.0.0.1", runtime.port());
    socket.sendFragmented(
        command(MessageType.HELLO, null, null, new Payloads.Hello("1.0", "1")), 1);
    socket.await(MessageType.HELLO_ACK, WAIT);
    String username = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    socket.send(
        command(
            MessageType.REGISTER,
            null,
            null,
            new Payloads.Register(username, "ValidPass123!", "Integration", "avatar-01")));
    long id = socket.await(MessageType.REGISTER_RESULT, WAIT).payload().path("userId").asLong();
    socket.send(
        command(MessageType.LOGIN, null, null, new Payloads.Login(username, "ValidPass123!")));
    socket.await(MessageType.LOGIN_RESULT, WAIT);
    return new Player(socket, id);
  }

  public static UUID challenge(Player one, Player two) throws IOException {
    one.socket.send(
        command(MessageType.CHALLENGE, null, null, new Payloads.Challenge(two.userId, 1)));
    Envelope invitation = two.socket.await(MessageType.CHALLENGE_RECEIVED, WAIT);
    one.socket.await(MessageType.CHALLENGE_ACK, WAIT);
    UUID challenge = UUID.fromString(invitation.payload().path("challengeId").asText());
    two.socket.send(
        command(MessageType.CHALLENGE_ACCEPT, null, null, new Payloads.ChallengeAccept(challenge)));
    UUID match = one.socket.await(MessageType.MATCH_START, WAIT).matchId();
    if (!match.equals(two.socket.await(MessageType.MATCH_START, WAIT).matchId()))
      throw new AssertionError("Different match IDs");
    return match;
  }

  public static void ready(Player player, UUID match) throws IOException {
    player.socket.send(command(MessageType.MATCH_READY, match, null, new Payloads.MatchReady()));
  }

  public static void roundReady(Player player, Envelope question) throws IOException {
    player.socket.send(
        command(
            MessageType.QUESTION_READY,
            question.matchId(),
            question.roundId(),
            new Payloads.QuestionReady(
                question.payload().path("question").path("questionId").asLong())));
  }

  public static void answer(Player player, Envelope question) throws IOException {
    var q = question.payload().path("question");
    String type = q.path("questionType").asText();
    var mapper = CODEC.mapper();
    com.fasterxml.jackson.databind.JsonNode answer =
        switch (type) {
          case "SINGLE_CHOICE" -> mapper.valueToTree("A");
          case "MULTIPLE_CHOICE" -> mapper.valueToTree(List.of("A", "C"));
          case "TRUE_FALSE" -> mapper.valueToTree(true);
          case "SHORT_ANSWER" -> mapper.valueToTree("Hà Nội");
          default -> throw new AssertionError(type);
        };
    player.socket.send(
        command(
            MessageType.ANSWER,
            question.matchId(),
            question.roundId(),
            new Payloads.Answer(q.path("questionId").asLong(), answer)));
  }
}
