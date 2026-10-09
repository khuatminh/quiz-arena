package vn.edu.nhom7.quiz.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProtocolContractTest {
  private final ProtocolCodec codec = new ProtocolCodec();

  @Test
  void rejectsMalformed() {
    for (String json :
        new String[] {
          "{}",
          "{\"protocolVersion\":1,\"type\":\"NOPE\",\"payload\":{}}",
          "{\"protocolVersion\":1,\"type\":\"HELLO\",\"requestId\":\"bad\",\"payload\":{}}",
          "{\"protocolVersion\":1,\"type\":\"HELLO\",\"payload\":[]}"
        })
      assertThrows(
          ProtocolException.class, () -> codec.decode(json.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void rejectsScalarCoercion() {
    String s =
        "{\"protocolVersion\":1,\"type\":\"REMATCH_RESPONSE\",\"requestId\":\"00000000-0000-0000-0000-000000000001\",\"matchId\":\"00000000-0000-0000-0000-000000000002\",\"payload\":{\"accept\":\"true\"}}";
    assertThrows(ProtocolException.class, () -> codec.decode(s.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void catalogueEveryType() throws Exception {
    var mapper = codec.mapper();
    var entries = mapper.readTree(getClass().getResourceAsStream("/protocol/catalogue-v1.json"));
    assertEquals(MessageType.values().length, entries.size());
    for (var entry : entries) {
      Envelope e = codec.decode(mapper.writeValueAsBytes(entry.get("valid")));
      assertEquals(e, codec.decode(codec.encode(e)));
      assertThrows(
          ProtocolException.class,
          () -> codec.decode(mapper.writeValueAsBytes(entry.get("invalid"))));
    }
  }
}
