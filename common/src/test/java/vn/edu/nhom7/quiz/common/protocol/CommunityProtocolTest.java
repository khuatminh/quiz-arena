package vn.edu.nhom7.quiz.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CommunityProtocolTest {
  final ProtocolCodec codec = new ProtocolCodec();

  @Test
  void createAndListCanUseZeroIdButEditsCannot() {
    for (String action : List.of("CREATE", "LIST"))
      assertDoesNotThrow(
          () ->
              codec.envelope(
                  MessageType.AUTHOR_REQUEST,
                  UUID.randomUUID(),
                  null,
                  null,
                  null,
                  new CommunityPayloads.AuthorRequest(
                      action, 0, codec.mapper().createObjectNode())));
    assertThrows(
        ProtocolException.class,
        () ->
            codec.envelope(
                MessageType.AUTHOR_REQUEST,
                UUID.randomUUID(),
                null,
                null,
                null,
                new CommunityPayloads.AuthorRequest(
                    "PUBLISH", 0, codec.mapper().createObjectNode())));
  }

  @Test
  void partialAckHasNullableMediaId() {
    assertDoesNotThrow(
        () ->
            codec.envelope(
                MessageType.MEDIA_UPLOAD_ACK,
                UUID.randomUUID(),
                null,
                null,
                null,
                new MediaPayloads.UploadAck(UUID.randomUUID(), 24576, null)));
  }

  @Test
  void transferFitsExistingFrame() {
    var e =
        codec.envelope(
            MessageType.MEDIA_UPLOAD_CHUNK,
            UUID.randomUUID(),
            null,
            null,
            null,
            new MediaPayloads.UploadChunk(
                UUID.randomUUID(),
                0,
                Base64.getEncoder().encodeToString(new byte[MediaPayloads.CHUNK_BYTES])));
    assertTrue(codec.encode(e).length < 65536);
  }
}
