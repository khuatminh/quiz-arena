package vn.edu.nhom7.quiz.server.support;

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.net.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class SocketHarness implements AutoCloseable {
  private final Socket socket;
  private final ProtocolCodec codec = new ProtocolCodec();
  private final FrameDecoder decoder = new FrameDecoder();
  private final List<Envelope> inbox = new ArrayList<>();

  private SocketHarness(Socket s) throws IOException {
    socket = s;
    socket.setSoTimeout(100);
  }

  public static SocketHarness connect(String host, int port) throws IOException {
    return new SocketHarness(new Socket(host, port));
  }

  public void send(Envelope e) throws IOException {
    sendFragmented(e, 65540);
  }

  public synchronized void sendFragmented(Envelope e, int chunk) throws IOException {
    byte[] frame = FrameCodec.encode(codec.encode(e));
    var out = socket.getOutputStream();
    for (int at = 0; at < frame.length; at += chunk) {
      out.write(frame, at, Math.min(chunk, frame.length - at));
      out.flush();
    }
  }

  public synchronized void sendRaw(byte[] bytes) throws IOException {
    socket.getOutputStream().write(bytes);
    socket.getOutputStream().flush();
  }

  public Envelope await(MessageType type, Duration timeout) throws IOException {
    return await(type, null, timeout);
  }

  public Envelope await(MessageType type, UUID requestId, Duration timeout) throws IOException {
    long end = System.nanoTime() + timeout.toNanos();
    byte[] buf = new byte[4096];
    while (System.nanoTime() < end) {
      for (var it = inbox.iterator(); it.hasNext(); ) {
        Envelope e = it.next();
        if (e.type() == type && (requestId == null || requestId.equals(e.requestId()))) {
          it.remove();
          return e;
        }
      }
      try {
        int n = socket.getInputStream().read(buf);
        if (n < 0) throw new EOFException("Connection closed awaiting " + type);
        for (var frame : decoder.accept(buf, 0, n, System.nanoTime()))
          inbox.add(codec.decode(frame));
      } catch (SocketTimeoutException ignored) {
        decoder.checkTimeout(System.nanoTime());
      }
    }
    throw new IOException(
        "Timed out awaiting " + type + "; buffered=" + inbox.stream().map(Envelope::type).toList());
  }

  public void close() throws IOException {
    socket.close();
  }
}
