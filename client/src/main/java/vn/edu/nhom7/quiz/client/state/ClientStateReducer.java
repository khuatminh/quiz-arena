package vn.edu.nhom7.quiz.client.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ClientStateReducer {
  private static final Set<MessageType> MATCH_LOCAL_EVENTS =
      Set.of(
          MessageType.READY_STATUS,
          MessageType.RESULT_SESSION_CLOSED,
          MessageType.REMATCH_STATUS,
          MessageType.LIVE_REVIEW,
          MessageType.MATCH_RESULT,
          MessageType.QUESTION_RESULT,
          MessageType.ROUND_LEADERBOARD,
          MessageType.ANSWER_STATUS,
          MessageType.LIFELINE_RESULT,
          MessageType.LIFELINE_STATUS,
          MessageType.CHAT_MESSAGE);

  public ClientState apply(ClientState state, Envelope event, long receivedNanos) {
    var sequences = new HashMap<>(state.lastSeqByMatch());
    if (event.matchId() != null && event.eventSeq() != null) {
      Long last = sequences.get(event.matchId());
      if (last != null && event.eventSeq() <= last) return state;
      sequences.put(event.matchId(), event.eventSeq());
    }
    var data = new EnumMap<MessageType, JsonNode>(MessageType.class);
    data.putAll(state.data());
    var persistence = new HashMap<>(state.persistenceByMatch());
    var chatMessages = new LinkedHashMap<>(state.chatById());
    var payload = event.payload();
    String phase = state.phase(), error = "";
    JsonNode profile = state.profile(), question = state.question();
    UUID activeMatchId = state.activeMatchId(),
        resultMatchId = state.resultMatchId(),
        roundId = state.roundId();
    long onlineRevision = state.onlineRevision(), presentationVersion = state.presentationVersion();
    boolean pending = state.ownPending(), accepted = state.accepted();
    if (event.type() == MessageType.ONLINE_LIST) {
      long r = payload.path("revision").asLong();
      if (r <= onlineRevision) return state;
      onlineRevision = r;
    }
    if (event.type() == MessageType.MATCH_SAVE_STATUS) {
      persistence.put(event.matchId(), payload.path("status").asText());
    } else if (event.matchId() != null
        && activeMatchId != null
        && !event.matchId().equals(activeMatchId)
        && event.type() != MessageType.MATCH_START) return state;
    switch (event.type()) {
      case LOGIN_RESULT -> {
        profile = payload.path("profile");
        phase = "LOBBY";
        activeMatchId = null;
        roundId = null;
        question = null;
        pending = false;
        accepted = false;
        chatMessages.clear();
      }
      case PROFILE -> profile = payload;
      case LOGOUT_ACK -> {
        profile = null;
        phase = "AUTH";
        activeMatchId = null;
      }
      case MATCH_START -> {
        activeMatchId = event.matchId();
        resultMatchId = null;
        roundId = null;
        phase = "WAITING_READY";
        question = null;
        pending = false;
        accepted = false;
        chatMessages.clear();
        data.keySet().removeAll(MATCH_LOCAL_EVENTS);
      }
      case MATCH_COUNTDOWN -> phase = "MATCH_COUNTDOWN";
      case QUESTION -> {
        phase = "QUESTION_PREPARE";
        roundId = event.roundId();
        question = payload.path("question");
        pending = false;
        accepted = false;
        data.remove(MessageType.ANSWER_STATUS);
      }
      case ROUND_COUNTDOWN -> phase = "ROUND_COUNTDOWN";
      case QUESTION_OPEN -> phase = "OPEN";
      case ANSWER_STATUS ->
          payload = MatchEventData.answeredStatus(payload, data.get(MessageType.ANSWER_STATUS));
      case LIFELINE_STATUS ->
          payload = MatchEventData.lifelineStatus(payload, data.get(MessageType.LIFELINE_STATUS));
      case ANSWER_ACK -> {
        pending = false;
        accepted = true;
      }
      case QUESTION_RESULT -> {
        phase = "REVEAL";
        pending = false;
        accepted = true;
        payload = MatchEventData.withStreaks(payload, data.get(MessageType.QUESTION_RESULT));
      }
      case ROUND_LEADERBOARD -> phase = "LEADERBOARD";
      case MATCH_RESULT -> {
        phase = "RESULT";
        resultMatchId = event.matchId();
        pending = false;
        persistence.put(event.matchId(), payload.path("persistenceStatus").asText());
      }
      case CHAT_MESSAGE -> {
        UUID id = UUID.fromString(payload.path("chatMessageId").asText());
        chatMessages.putIfAbsent(id, payload);
      }
      case MATCH_SNAPSHOT -> {
        phase =
            switch (payload.path("phase").asText()) {
              case "ANSWERING" -> "OPEN";
              case "QUESTION_PREPARING" -> "QUESTION_PREPARE";
              default -> payload.path("phase").asText();
            };
        question = payload.get("question");
        JsonNode previousResult = data.get(MessageType.QUESTION_RESULT);
        // Keep our own copy of the same result, since it carries the streak counts.
        if (payload.hasNonNull("latestResult")
            && (previousResult == null
                || previousResult.path("questionId").asLong()
                    != payload.path("latestResult").path("questionId").asLong()))
          data.put(MessageType.QUESTION_RESULT, payload.get("latestResult"));
        if (payload.hasNonNull("standings")) {
          var leaderboard =
              com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
          leaderboard.set("standings", payload.get("standings"));
          leaderboard.put("roundIndex", payload.path("roundIndex").asInt());
          data.put(MessageType.ROUND_LEADERBOARD, leaderboard);
        }
        accepted = payload.hasNonNull("acceptedAnswer");
        pending = false;
        roundId = event.roundId();
        if (payload.has("lifelines"))
          data.put(MessageType.LIFELINE_RESULT, MatchEventData.lifelines(payload));
      }
      case RESULT_SESSION_CLOSED -> {
        activeMatchId = null;
        phase = "RESULT_DETACHED";
        pending = false;
      }
      case EXIT_ACK -> {
        phase = "LOBBY";
        activeMatchId = null;
        roundId = null;
      }
      case ERROR -> {
        error = payload.path("code").asText() + ": " + payload.path("message").asText();
        if (payload.path("code").asText().equals("INVALID_ANSWER") && phase.equals("OPEN"))
          pending = false;
      }
      default -> {}
    }
    if (!phase.equals(state.phase())
        || event.type() == MessageType.MATCH_START
        || event.type() == MessageType.MATCH_SNAPSHOT) presentationVersion++;
    if (event.matchId() != null
        && event.type() != MessageType.MATCH_SNAPSHOT
        && event.type() != MessageType.MATCH_SAVE_STATUS) data.remove(MessageType.MATCH_SNAPSHOT);
    data.put(event.type(), payload);
    return new ClientState(
        state.connectionEpoch(),
        profile,
        onlineRevision,
        activeMatchId,
        resultMatchId,
        roundId,
        sequences,
        phase,
        presentationVersion,
        question,
        pending,
        accepted,
        data,
        persistence,
        chatMessages,
        error,
        receivedNanos);
  }
}
