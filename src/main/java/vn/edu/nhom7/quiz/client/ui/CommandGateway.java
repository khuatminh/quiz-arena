package vn.edu.nhom7.quiz.client.ui;

import java.util.*;
import vn.edu.nhom7.quiz.client.network.*;
import vn.edu.nhom7.quiz.client.state.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class CommandGateway implements UiCommandSink {
  private final NetworkClient network;
  private final ClientStore store;
  private final ProtocolCodec codec = new ProtocolCodec();
  private final RequestTracker tracker;
  private UUID pendingRound;
  private long epoch = -1;

  public CommandGateway(NetworkClient network, ClientStore store, RequestTracker tracker) {
    this.network = network;
    this.store = store;
    this.tracker = tracker;
  }

  public synchronized UUID send(MessageType type, UUID matchId, UUID roundId, Object payload) {
    if (epoch != store.state().connectionEpoch()) {
      pendingRound = null;
      epoch = store.state().connectionEpoch();
    }
    UUID id = UUID.randomUUID();
    if (type == MessageType.ANSWER) {
      if (!store.state().canAnswer() || java.util.Objects.equals(pendingRound, roundId))
        throw new IllegalStateException("Answer already pending or round closed");
      pendingRound = roundId;
      store.markAnswerPending();
    }
    Envelope e = codec.envelope(type, id, matchId, roundId, null, payload);
    if (!network.send(e)) {
      if (type == MessageType.ANSWER) {
        pendingRound = null;
        store.cancelAnswerPending();
      }
      throw new IllegalStateException("Connection closed or outbound queue full");
    }
    tracker.register(id, type, System.nanoTime());
    return id;
  }

  public synchronized void onEvent(Envelope e) {
    if (e.type() == MessageType.QUESTION
        || e.type() == MessageType.MATCH_START
        || (e.type() == MessageType.ERROR
            && e.payload().path("code").asText().equals("INVALID_ANSWER"))) pendingRound = null;
  }
}
