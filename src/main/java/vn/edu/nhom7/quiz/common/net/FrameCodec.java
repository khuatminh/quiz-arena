package vn.edu.nhom7.quiz.common.net;

import java.nio.ByteBuffer;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;

public final class FrameCodec {
  public static final int PREFIX_BYTES = Integer.BYTES;
  public static final int MAX_FRAME_BYTES = 65_536;

  private FrameCodec() {}

  public static byte[] encode(byte[] jsonUtf8) {
    if (jsonUtf8 == null || jsonUtf8.length < 1 || jsonUtf8.length > MAX_FRAME_BYTES)
      throw new ProtocolException("FRAME_TOO_LARGE", "Frame length outside 1..65536");
    return ByteBuffer.allocate(PREFIX_BYTES + jsonUtf8.length)
        .putInt(jsonUtf8.length)
        .put(jsonUtf8)
        .array();
  }
}
