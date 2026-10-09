package vn.edu.nhom7.quiz.server.network;

import java.net.SocketTimeoutException;
import java.util.function.BiConsumer;
import vn.edu.nhom7.quiz.common.net.FrameDecoder;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ConnectionReader implements Runnable {
  private final Connection c;
  private final ConnectionManager manager;
  private final BiConsumer<Connection, Envelope> router;
  private final BiConsumer<Connection, ProtocolException> errors;

  public ConnectionReader(
      Connection c,
      ConnectionManager manager,
      BiConsumer<Connection, Envelope> router,
      BiConsumer<Connection, ProtocolException> errors) {
    this.c = c;
    this.manager = manager;
    this.router = router;
    this.errors = errors;
  }

  public void run() {
    var decoder = new FrameDecoder();
    var codec = new ProtocolCodec();
    try {
      var in = c.socket.getInputStream();
      byte[] buf = new byte[4096];
      while (!c.closed.get()) {
        try {
          int n = in.read(buf);
          if (n < 0) {
            decoder.endOfInput();
            break;
          }
          for (byte[] frame : decoder.accept(buf, 0, n, System.nanoTime())) {
            try {
              Envelope e = codec.decode(frame);
              router.accept(c, e);
              c.lastValidFrame = System.nanoTime();
            } catch (ProtocolException ex) {
              errors.accept(c, ex);
              if (!c.invalid.allow(System.nanoTime())) return;
            }
          }
        } catch (SocketTimeoutException timeout) {
          decoder.checkTimeout(System.nanoTime());
        }
      }
    } catch (Exception ignored) {
    } finally {
      manager.close(c.id);
    }
  }
}
