package vn.edu.nhom7.quiz.server.match;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.QuestionSnapshot;
import vn.edu.nhom7.quiz.server.quiz.QuestionValidator;

class InteractiveQuestionsTest {
  static final ObjectMapper JSON = new ObjectMapper();
  static final String RANGE =
      "{\"min\":0,\"max\":10,\"step\":0.1,\"unit\":\"ms\",\"acceptedMin\":4.9,\"acceptedMax\":5.1}";
  static final String REGION = "{\"x\":0.2,\"y\":0.3,\"width\":0.4,\"height\":0.2}";
  final AnswerEvaluator evaluator = new AnswerEvaluator();

  static QuestionSnapshot question(QuestionType type, String key) {
    return new QuestionSnapshot(
        900 + type.ordinal(),
        1,
        type,
        "Câu tương tác",
        type == QuestionType.REORDER
            ? List.of(
                new Payloads.Option("b", "SYN-ACK"),
                new Payloads.Option("c", "ACK"),
                new Payloads.Option("a", "SYN"))
            : List.of(),
        key,
        "Giải thích",
        type == QuestionType.PINPOINT ? "question-1" : null,
        null);
  }

  @Test
  void reorderRequiresEveryItemOnceAndExactOrder() throws Exception {
    var q = question(QuestionType.REORDER, "[\"a\",\"b\",\"c\"]");
    QuestionValidator.validate(q);
    assertTrue(evaluator.evaluate(q, JSON.readTree(q.answerKeyJson())));
    assertFalse(evaluator.evaluate(q, JSON.readTree("[\"b\",\"a\",\"c\"]")));
    for (String bad :
        List.of("[]", "[\"a\",\"b\"]", "[\"a\",\"a\",\"c\"]", "[\"a\",\"b\",\"x\"]", "\"a,b,c\""))
      assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, JSON.readTree(bad)));
    assertThrows(
        IllegalArgumentException.class,
        () -> QuestionValidator.validate(question(QuestionType.REORDER, "[\"a\",\"b\"]")));
  }

  @Test
  void rangeUsesExactDecimalStepAndInclusiveAcceptedBounds() throws Exception {
    var q = question(QuestionType.RANGE, RANGE);
    QuestionValidator.validate(q);
    for (String good : List.of("4.9", "5", "5.0", "5.1"))
      assertTrue(evaluator.evaluate(q, JSON.readTree(good)));
    for (String wrong : List.of("0", "4.8", "5.2", "10"))
      assertFalse(evaluator.evaluate(q, JSON.readTree(wrong)));
    for (String bad :
        List.of("\"5\"", "null", "{}", "-0.1", "10.1", "5.01", "1e100", "0.0000000001"))
      assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, JSON.readTree(bad)));
  }

  @Test
  void rangeCannotPublishImpossibleOrMalformedSettings() {
    for (String key :
        List.of(
            RANGE.replace("0.1", "0"),
            RANGE.replace("4.9", "5.2"),
            RANGE.replace("0.1", "0.3"),
            RANGE.replace("4.9", "4.91").replace("5.1", "4.99"),
            RANGE.replace("\"ms\"", "false"),
            RANGE.replace("\"step\":0.1", "\"step\":\"0.1\"")))
      assertThrows(
          IllegalArgumentException.class,
          () -> QuestionValidator.validate(question(QuestionType.RANGE, key)));
  }

  @Test
  void pinpointChecksNormalizedCoordinatesAndRectangleEdges() throws Exception {
    var q = question(QuestionType.PINPOINT, REGION);
    QuestionValidator.validate(q);
    for (String point :
        List.of("{\"x\":0.2,\"y\":0.3}", "{\"x\":0.6,\"y\":0.5}", "{\"x\":0.4,\"y\":0.4}"))
      assertTrue(evaluator.evaluate(q, JSON.readTree(point)));
    assertFalse(evaluator.evaluate(q, JSON.readTree("{\"x\":0.600001,\"y\":0.4}")));
    for (String bad :
        List.of(
            "{}",
            "null",
            "[0.4,0.4]",
            "{\"x\":\"0.4\",\"y\":0.4}",
            "{\"x\":1.1,\"y\":0.4}",
            "{\"x\":0.4,\"y\":0.4,\"correct\":true}"))
      assertThrows(ProtocolException.class, () -> evaluator.evaluate(q, JSON.readTree(bad)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuestionValidator.validate(
                question(QuestionType.PINPOINT, REGION.replace("0.4", "0.9"))));
    var noImage =
        new QuestionSnapshot(
            1, 1, QuestionType.PINPOINT, "Question", List.of(), REGION, null, null, null);
    assertThrows(IllegalArgumentException.class, () -> QuestionValidator.validate(noImage));
  }

  @Test
  void publicQuestionsAndSnapshotsHaveOnlyAllowedRangeSettings() throws Exception {
    var codec = new ProtocolCodec();
    for (var q :
        List.of(
            question(QuestionType.RANGE, RANGE),
            question(QuestionType.PINPOINT, REGION),
            question(QuestionType.REORDER, "[\"a\",\"b\",\"c\"]"))) {
      JsonNode pub = MatchPayloads.publicQuestion(q);
      assertEquals(30000, pub.path("timeLimitMs").asInt());
      assertFalse(pub.toString().contains("acceptedMin"));
      assertFalse(pub.toString().contains("acceptedMax"));
      assertFalse(pub.toString().contains("answerKey"));
      if (q.questionType() == QuestionType.RANGE) assertEquals(4, pub.path("config").size());
      else assertTrue(pub.path("config").isNull());
      var envelope =
          codec.envelope(
              MessageType.QUESTION,
              null,
              UUID.randomUUID(),
              UUID.randomUUID(),
              1L,
              MatchPayloads.obj(
                  "question", pub, "roundIndex", 1, "totalRounds", 3, "readyDeadlineAtMs", 123));
      assertEquals(
          pub.toString(),
          codec.decode(codec.encode(envelope)).payload().path("question").toString());
    }
  }

  @Test
  void decimalDigitsSurviveWireDecodingAndKeyParsing() throws Exception {
    String key =
        "{\"min\":12345,\"max\":12346,\"step\":0.000001,\"unit\":\"ms\",\"acceptedMin\":12345.000001,\"acceptedMax\":12345.000001}";
    var q = question(QuestionType.RANGE, key);
    QuestionValidator.validate(q);
    var codec = new ProtocolCodec();
    var envelope =
        codec.envelope(
            MessageType.ANSWER,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new Payloads.Answer(
                q.questionId(),
                com.fasterxml.jackson.databind.node.DecimalNode.valueOf(
                    new java.math.BigDecimal("12345.000001"))));
    var decoded = codec.decode(codec.encode(envelope)).payload().path("answer");
    assertEquals(new java.math.BigDecimal("12345.000001"), decoded.decimalValue());
    assertTrue(evaluator.evaluate(q, decoded));
    assertFalse(
        evaluator.evaluate(
            q,
            com.fasterxml.jackson.databind.node.DecimalNode.valueOf(
                new java.math.BigDecimal("12345.000002"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuestionValidator.validate(
                question(
                    QuestionType.RANGE,
                    "{\"min\":999999999999,\"max\":1000000000000,\"step\":0.000001,\"unit\":\"ms\",\"acceptedMin\":999999999999,\"acceptedMax\":1000000000000}")));
  }

  @Test
  void thirtySecondScoringHasProportionalBoundaries() {
    long window = 30_000_000_000L;
    assertEquals(3, ScoreCalculator.calculate(true, 10_000_000_000L, window));
    assertEquals(2, ScoreCalculator.calculate(true, 10_000_000_001L, window));
    assertEquals(2, ScoreCalculator.calculate(true, 20_000_000_000L, window));
    assertEquals(1, ScoreCalculator.calculate(true, window, window));
    assertEquals(0, ScoreCalculator.calculate(false, window, window));
    assertThrows(
        IllegalArgumentException.class, () -> ScoreCalculator.calculate(true, window + 1, window));
  }
}
