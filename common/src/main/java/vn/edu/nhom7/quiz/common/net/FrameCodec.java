package vn.edu.nhom7.quiz.common.net;

import java.nio.ByteBuffer;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;

public final class FrameCodec {
  private FrameCodec() {}

  public static byte[] encode(byte[] jsonUtf8) {
    if (jsonUtf8 == null || jsonUtf8.length < 1 || jsonUtf8.length > 65536)
      throw new ProtocolException("FRAME_TOO_LARGE", "Frame length outside 1..65536");
    return ByteBuffer.allocate(4 + jsonUtf8.length).putInt(jsonUtf8.length).put(jsonUtf8).array();
  }
}
