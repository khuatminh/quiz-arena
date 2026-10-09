package vn.edu.nhom7.quiz.common.net;

import java.nio.ByteBuffer;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;

public final class FrameDecoder {
  private final byte[] prefix = new byte[4];
  private int prefixRead, bodyRead;
  private byte[] body;
  private Long firstByteAt;

  public List<byte[]> accept(byte[] bytes, int offset, int length, long nowNanos) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    checkTimeout(nowNanos);
    var frames = new ArrayList<byte[]>();
    for (int i = offset; i < offset + length; i++) {
      if (firstByteAt == null) firstByteAt = nowNanos;
      if (body == null) {
        prefix[prefixRead++] = bytes[i];
        if (prefixRead == 4) {
          int n = ByteBuffer.wrap(prefix).getInt();
          if (n < 1 || n > 65536)
            throw new ProtocolException("FRAME_TOO_LARGE", "Frame length outside 1..65536");
          body = new byte[n];
        }
      } else {
        body[bodyRead++] = bytes[i];
        if (bodyRead == body.length) {
          frames.add(body);
          body = null;
          bodyRead = 0;
          prefixRead = 0;
          firstByteAt = null;
        }
      }
    }
    return frames;
  }

  public void checkTimeout(long nowNanos) {
    if (firstByteAt != null && nowNanos - firstByteAt >= 10_000_000_000L)
      throw new ProtocolException("INCOMPLETE_FRAME", "Frame incomplete for 10 seconds");
  }

  public void endOfInput() {
    if (firstByteAt != null) throw new ProtocolException("INCOMPLETE_FRAME", "EOF inside a frame");
  }
}
