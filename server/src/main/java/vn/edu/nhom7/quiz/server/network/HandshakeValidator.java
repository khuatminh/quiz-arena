package vn.edu.nhom7.quiz.server.network;

import vn.edu.nhom7.quiz.common.protocol.*;

public final class HandshakeValidator {
  public void validateFirst(Envelope e, long connected, long now) {
    if (now - connected >= 5_000_000_000L)
      throw new ProtocolException("HANDSHAKE_TIMEOUT", "HELLO must arrive within 5 seconds");
    if (e.type() != MessageType.HELLO)
      throw new ProtocolException("INVALID_STATE", "HELLO must be first");
    if (e.protocolVersion() != 1)
      throw new ProtocolException("UNSUPPORTED_PROTOCOL", "Protocol version mismatch");
    if (!e.payload().path("assetPackVersion").asText().equals("1"))
      throw new ProtocolException("ASSET_VERSION_MISMATCH", "Asset pack version mismatch");
    if (!e.payload().path("clientVersion").asText().equals("2.0"))
      throw new ProtocolException(
          "UNSUPPORTED_PROTOCOL", "Quiz Arena requires client version 2.0 (community quizzes).");
  }
}
