package vn.edu.nhom7.quiz.client.state;

import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ClientStore implements AutoCloseable {
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "client-state");
            t.setDaemon(true);
            return t;
          });
  private final ClientStateReducer reducer = new ClientStateReducer();
  private volatile ClientState state = ClientState.initial();
  private volatile Consumer<ClientState> listener = s -> {};

  public ClientState state() {
    return state;
  }

  public void subscribe(Consumer<ClientState> subscriber) {
    listener = subscriber;
  }

  public void accept(Envelope event) {
    long receivedNanos = System.nanoTime();
    update(current -> reducer.apply(current, event, receivedNanos));
  }

  public void leaveResult() {
    update(ClientState::lobby);
  }

  public void resetConnection() {
    update(ClientState::reconnected);
  }

  public void cancelAnswerPending() {
    update(ClientState::clearPending);
  }

  public void markAnswerPending() {
    update(ClientState::pending);
  }

  /** Serializes every transition and notifies only after the new immutable state is installed. */
  private void update(UnaryOperator<ClientState> transition) {
    executor.execute(
        () -> {
          state = transition.apply(state);
          listener.accept(state);
        });
  }

  public void close() {
    executor.shutdownNow();
  }
}
