package vn.edu.nhom7.quiz.common.net;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.ProtocolException;

class FrameDecoderTest {
  @Test
  void arbitraryChunks() {
    byte[] frame = FrameCodec.encode("Tiếng Việt 😀".getBytes(StandardCharsets.UTF_8));
    var d = new FrameDecoder();
    var frames = new ArrayList<byte[]>();
    for (int i = 0; i < frame.length; i++) frames.addAll(d.accept(frame, i, 1, i));
    assertEquals(1, frames.size());
    assertEquals("Tiếng Việt 😀", new String(frames.getFirst(), StandardCharsets.UTF_8));
    d.endOfInput();
  }

  @Test
  void rejectsLengthsAndPartialEof() {
    for (int n : new int[] {0, -1, 65537})
      assertThrows(
          ProtocolException.class,
          () ->
              new FrameDecoder()
                  .accept(java.nio.ByteBuffer.allocate(4).putInt(n).array(), 0, 4, 0));
    var d = new FrameDecoder();
    d.accept(new byte[] {0}, 0, 1, 0);
    assertThrows(ProtocolException.class, d::endOfInput);
    assertThrows(ProtocolException.class, () -> d.checkTimeout(10_000_000_000L));
  }

  @Test
  void maximumAndCoalescing() {
    byte[] f = FrameCodec.encode(new byte[65536]);
    var d = new FrameDecoder();
    byte[] both = new byte[f.length * 2];
    System.arraycopy(f, 0, both, 0, f.length);
    System.arraycopy(f, 0, both, f.length, f.length);
    assertEquals(2, d.accept(both, 0, both.length, 0).size());
  }
}
