package vn.edu.nhom7.quiz.client.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public record ClientState(
    long connectionEpoch,
    JsonNode profile,
    long onlineRevision,
    UUID activeMatchId,
    UUID resultMatchId,
    UUID roundId,
    Map<UUID, Long> lastSeqByMatch,
    String phase,
    long presentationVersion,
    JsonNode question,
    boolean ownPending,
    boolean accepted,
    Map<MessageType, JsonNode> data,
    Map<UUID, String> persistenceByMatch,
    Map<UUID, JsonNode> chatById,
    String error,
    long receivedNanos) {
  public ClientState {
    lastSeqByMatch = Map.copyOf(lastSeqByMatch);
    data = Map.copyOf(data);
    persistenceByMatch = Map.copyOf(persistenceByMatch);
    chatById = Collections.unmodifiableMap(new LinkedHashMap<>(chatById));
  }

  public static ClientState initial() {
    return new ClientState(
        0, null, -1, null, null, null, Map.of(), "AUTH", 0, null, false, false, Map.of(), Map.of(),
        Map.of(), "", 0);
  }

  public long selfUserId() {
    return profile == null ? 0 : profile.path("userId").asLong();
  }

  public JsonNode payload(MessageType type) {
    return data.get(type);
  }

  public boolean canAnswer() {
    return "OPEN".equals(phase) && !ownPending && !accepted;
  }

  public boolean resultAttached() {
    return activeMatchId != null && !data.containsKey(MessageType.RESULT_SESSION_CLOSED);
  }

  public ClientState lobby() {
    return new ClientState(
        connectionEpoch,
        profile,
        onlineRevision,
        null,
        resultMatchId,
        null,
        lastSeqByMatch,
        "LOBBY",
        presentationVersion + 1,
        null,
        false,
        false,
        data,
        persistenceByMatch,
        chatById,
        error,
        receivedNanos);
  }

  public ClientState clearPending() {
    return new ClientState(
        connectionEpoch,
        profile,
        onlineRevision,
        activeMatchId,
        resultMatchId,
        roundId,
        lastSeqByMatch,
        phase,
        presentationVersion,
        question,
        false,
        accepted,
        data,
        persistenceByMatch,
        chatById,
        error,
        receivedNanos);
  }

  public ClientState pending() {
    return new ClientState(
        connectionEpoch,
        profile,
        onlineRevision,
        activeMatchId,
        resultMatchId,
        roundId,
        lastSeqByMatch,
        phase,
        presentationVersion,
        question,
        true,
        false,
        data,
        persistenceByMatch,
        chatById,
        error,
        receivedNanos);
  }
}
