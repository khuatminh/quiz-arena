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

  public void subscribe(Consumer<ClientState> l) {
    listener = l;
  }

  public void accept(Envelope e) {
    long received = System.nanoTime();
    executor.execute(
        () -> {
          state = reducer.apply(state, e, received);
          listener.accept(state);
        });
  }

  public void leaveResult() {
    executor.execute(
        () -> {
          state = state.lobby();
          listener.accept(state);
        });
  }

  public void resetConnection() {
    executor.execute(
        () -> {
          var old = state;
          var fresh = ClientState.initial();
          state =
              new ClientState(
                  old.connectionEpoch() + 1,
                  null,
                  -1,
                  null,
                  null,
                  null,
                  java.util.Map.of(),
                  "AUTH",
                  old.presentationVersion() + 1,
                  null,
                  false,
                  false,
                  java.util.Map.of(),
                  old.persistenceByMatch(),
                  java.util.Map.of(),
                  "",
                  0);
          listener.accept(state);
        });
  }

  public void cancelAnswerPending() {
    executor.execute(
        () -> {
          state = state.clearPending();
          listener.accept(state);
        });
  }

  public void markAnswerPending() {
    executor.execute(
        () -> {
          state = state.pending();
          listener.accept(state);
        });
  }

  public void close() {
    executor.shutdownNow();
  }
}
