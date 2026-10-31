package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Fixed v1 schema: only catalogue record classes are ever instantiated. */
public final class ProtocolCodec {
  private final ObjectMapper mapper;

  public ProtocolCodec() {
    var factory =
        JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(100).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    mapper =
        JsonMapper.builder(factory)
            .addModule(new JavaTimeModule())
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();
  }

  /** Return a copy so callers cannot weaken protocol validation. */
  public ObjectMapper mapper() {
    return mapper.copy();
  }

  public Envelope decode(byte[] jsonUtf8) {
    try {
      String json =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(jsonUtf8))
              .toString();
      JsonNode root = mapper.readTree(json);
      if (root == null || !root.isObject()) fail("INVALID_MESSAGE", "Envelope must be an object");
      Set<String> fields =
          Set.of(
              "protocolVersion", "type", "requestId", "matchId", "roundId", "eventSeq", "payload");
      root.fieldNames()
          .forEachRemaining(
              k -> {
                if (!fields.contains(k)) fail("INVALID_MESSAGE", "Unknown envelope field");
              });
      if (!root.path("protocolVersion").isIntegralNumber())
        fail("INVALID_MESSAGE", "Protocol version must be integer");
      if (root.path("protocolVersion").intValue() != 1)
        fail("UNSUPPORTED_PROTOCOL", "Protocol version mismatch");
      if (!root.path("type").isTextual()) fail("INVALID_MESSAGE", "Type must be a string");
      MessageType type;
      try {
        type = MessageType.valueOf(root.path("type").textValue());
      } catch (IllegalArgumentException ex) {
        throw new ProtocolException("UNKNOWN_TYPE", "Unknown message type");
      }
      for (String id : List.of("requestId", "matchId", "roundId"))
        if (root.hasNonNull(id)) PayloadValidator.uuid(root.get(id));
      if (root.hasNonNull("eventSeq")
          && (!root.get("eventSeq").isIntegralNumber()
              || !root.get("eventSeq").canConvertToLong()
              || root.get("eventSeq").longValue() < 1))
        fail("INVALID_MESSAGE", "Invalid event sequence");
      if (PayloadRegistry.direction(type) == PayloadRegistry.Direction.CLIENT_TO_SERVER) {
        if (!root.hasNonNull("requestId")) fail("INVALID_MESSAGE", "Client request ID required");
        if (root.hasNonNull("eventSeq")) fail("INVALID_MESSAGE", "Client event sequence forbidden");
      }
      JsonNode payload = root.get("payload");
      if (payload == null || !payload.isObject())
        fail("INVALID_MESSAGE", "Payload must be an object");
      PayloadValidator.validate(payload, PayloadRegistry.payloadClass(type), "payload");
      Envelope envelope = mapper.treeToValue(root, Envelope.class);
      validateLimits(envelope);
      return envelope;
    } catch (ProtocolException e) {
      throw e;
    } catch (com.fasterxml.jackson.core.JsonProcessingException
        | java.nio.charset.CharacterCodingException e) {
      throw new ProtocolException("BAD_JSON", "Malformed JSON or UTF-8");
    } catch (Exception e) {
      throw new ProtocolException("INVALID_MESSAGE", "Malformed message or payload");
    }
  }

  public byte[] encode(Envelope envelope) {
    try {
      byte[] bytes = mapper.writeValueAsBytes(envelope);
      decode(bytes);
      return bytes;
    } catch (ProtocolException e) {
      throw e;
    } catch (Exception e) {
      throw new ProtocolException("INVALID_MESSAGE", "Cannot encode message");
    }
  }

  public <T> T payload(Envelope envelope, Class<T> type) {
    if (PayloadRegistry.payloadClass(envelope.type()) != type)
      fail("INVALID_MESSAGE", "Payload type mismatch");
    try {
      return mapper.treeToValue(envelope.payload(), type);
    } catch (Exception e) {
      throw new ProtocolException("INVALID_MESSAGE", "Invalid payload");
    }
  }

  public Envelope envelope(
      MessageType type, UUID requestId, UUID matchId, UUID roundId, Long eventSeq, Object payload) {
    Envelope e =
        new Envelope(1, type, requestId, matchId, roundId, eventSeq, mapper.valueToTree(payload));
    encode(e); // Encoding already validates the generated envelope.
    return e;
  }

  private void validateLimits(Envelope e) {
    JsonNode p = e.payload();
    if (p.has("page") && p.get("page").intValue() < 1)
      fail("INVALID_MESSAGE", "Page must be positive");
    if (p.has("pageSize")
        && (p.get("pageSize").intValue() < 1 || p.get("pageSize").intValue() > 50))
      fail("INVALID_MESSAGE", "Page size outside 1..50");
    for (String id : List.of("userId", "quizId", "questionId", "targetUserId", "senderUserId"))
      if (p.has(id)
          && p.get(id).longValue() <= 0
          && !(e.type() == MessageType.AUTHOR_REQUEST
              && id.equals("quizId")
              && Set.of("CREATE", "LIST").contains(p.path("action").asText())
              && p.get(id).longValue() == 0)) fail("INVALID_MESSAGE", "ID must be positive");
  }

  private static void fail(String code, String message) {
    throw new ProtocolException(code, message);
  }
}
