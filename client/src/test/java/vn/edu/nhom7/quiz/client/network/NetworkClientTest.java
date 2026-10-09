package vn.edu.nhom7.quiz.client.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.net.*;
import vn.edu.nhom7.quiz.common.protocol.*;

class NetworkClientTest {
  @Test
  void helloFirstAndFragmentedAck() throws Exception {
    var codec = new ProtocolCodec();
    var received = new LinkedBlockingQueue<Envelope>();
    try (var server = new ServerSocket(0);
        var client = new NetworkClient(received::add, r -> {})) {
      var peer =
          CompletableFuture.runAsync(
              () -> {
                try (var socket = server.accept()) {
                  var in = new DataInputStream(socket.getInputStream());
                  int len = in.readInt();
                  Envelope hello = codec.decode(in.readNBytes(len));
                  assertEquals(MessageType.HELLO, hello.type());
                  var ack =
                      codec.envelope(
                          MessageType.HELLO_ACK,
                          hello.requestId(),
                          null,
                          null,
                          null,
                          new Payloads.HelloAck(UUID.randomUUID(), 1, 0, 10000, "1"));
                  byte[] bytes = FrameCodec.encode(codec.encode(ack));
                  for (byte b : bytes) {
                    socket.getOutputStream().write(b);
                    socket.getOutputStream().flush();
                  }
                  Thread.sleep(200);
                } catch (Exception e) {
                  throw new CompletionException(e);
                }
              });
      client.connect("localhost", server.getLocalPort()).get(2, TimeUnit.SECONDS);
      Envelope e = received.poll(2, TimeUnit.SECONDS);
      assertNotNull(e);
      assertEquals(MessageType.HELLO_ACK, e.type());
      peer.get(3, TimeUnit.SECONDS);
    }
  }
}
