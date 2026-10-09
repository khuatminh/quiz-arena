package vn.edu.nhom7.quiz.server.network;

import java.util.UUID;
import vn.edu.nhom7.quiz.common.protocol.Envelope;

public interface OutboundTransport {
  boolean tryEnqueue(UUID connectionId, Envelope event);
}
