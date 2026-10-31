package vn.edu.nhom7.quiz.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;

class ProtocolCatalogueTest {
  private final ProtocolCodec codec = new ProtocolCodec();

  @TestFactory
  Stream<DynamicTest> everyMessageValidAndInvalid() throws Exception {
    var entries =
        codec.mapper().readTree(getClass().getResourceAsStream("/protocol/catalogue-v1.json"));
    var tests = new ArrayList<DynamicTest>();
    for (JsonNode entry : entries) {
      String type = entry.path("type").asText();
      tests.add(
          DynamicTest.dynamicTest(
              type + " roundtrip",
              () -> {
                var e = codec.decode(codec.mapper().writeValueAsBytes(entry.path("valid")));
                assertEquals(e, codec.decode(codec.encode(e)));
              }));
      tests.add(
          DynamicTest.dynamicTest(
              type + " unknown field rejected",
              () ->
                  assertThrows(
                      ProtocolException.class,
                      () ->
                          codec.decode(codec.mapper().writeValueAsBytes(entry.path("invalid"))))));
    }
    return tests.stream();
  }

  @Test
  void demoAllTypesAndNoEarlyPrivateKey() throws Exception {
    var fixture =
        codec.mapper().readTree(getClass().getResourceAsStream("/protocol/demo-match-v1.json"));
    var counts = new HashMap<String, Integer>();
    int privateAcks = 0;
    for (JsonNode item : fixture.path("events")) {
      var e = codec.decode(codec.mapper().writeValueAsBytes(item.path("envelope")));
      if (e.type() == MessageType.QUESTION) {
        var q = e.payload().path("question");
        counts.merge(q.path("questionType").asText(), 1, Integer::sum);
        assertFalse(q.has("correctAnswer"));
        assertFalse(q.has("explanation"));
      }
      if (e.type() == MessageType.ANSWER_ACK) {
        privateAcks++;
        assertTrue(item.path("participantUserId").asLong() >= 101);
        assertFalse(e.payload().has("correct"));
        assertFalse(e.payload().has("earnedPoints"));
      }
    }
    assertEquals(
        Map.of("SINGLE_CHOICE", 4, "MULTIPLE_CHOICE", 2, "TRUE_FALSE", 2, "SHORT_ANSWER", 2),
        counts);
    assertEquals(20, privateAcks);
  }

  @Test
  void malformedUtf8DuplicateAndTrailingAreRejected() {
    for (byte[] bytes :
        List.of(
            new byte[] {(byte) 0xC3, 0x28},
            "{\"protocolVersion\":1,\"protocolVersion\":1}".getBytes(),
            "{} {}".getBytes()))
      assertEquals(
          "BAD_JSON", assertThrows(ProtocolException.class, () -> codec.decode(bytes)).code());
  }
}
