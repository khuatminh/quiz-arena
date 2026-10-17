package vn.edu.nhom7.quiz.client.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public final class ClientStateReducer {
  public ClientState apply(ClientState s, Envelope e, long now) {
    var seq = new HashMap<>(s.lastSeqByMatch());
    if (e.matchId() != null && e.eventSeq() != null) {
      Long last = seq.get(e.matchId());
      if (last != null && e.eventSeq() <= last) return s;
      seq.put(e.matchId(), e.eventSeq());
    }
    var data = new EnumMap<MessageType, JsonNode>(MessageType.class);
    data.putAll(s.data());
    var saves = new HashMap<>(s.persistenceByMatch());
    var chats = new LinkedHashMap<>(s.chatById());
    var p = e.payload();
    String phase = s.phase(), error = "";
    JsonNode profile = s.profile(), question = s.question();
    UUID match = s.activeMatchId(), resultMatch = s.resultMatchId(), round = s.roundId();
    long revision = s.onlineRevision(), pv = s.presentationVersion();
    boolean pending = s.ownPending(), accepted = s.accepted();
    if (e.type() == MessageType.ONLINE_LIST) {
      long r = p.path("revision").asLong();
      if (r <= revision) return s;
      revision = r;
    }
    if (e.type() == MessageType.MATCH_SAVE_STATUS) {
      saves.put(e.matchId(), p.path("status").asText());
    } else if (e.matchId() != null
        && match != null
        && !e.matchId().equals(match)
        && e.type() != MessageType.MATCH_START) return s;
    switch (e.type()) {
      case LOGIN_RESULT -> {
        profile = p.path("profile");
        phase = "LOBBY";
        match = null;
        round = null;
        question = null;
        pending = false;
        accepted = false;
        chats.clear();
      }
      case PROFILE -> profile = p;
      case LOGOUT_ACK -> {
        profile = null;
        phase = "AUTH";
        match = null;
      }
      case MATCH_START -> {
        match = e.matchId();
        resultMatch = null;
        round = null;
        phase = "WAITING_READY";
        question = null;
        pending = false;
        accepted = false;
        chats.clear();
        data.keySet()
            .removeIf(
                t ->
                    t == MessageType.READY_STATUS
                        || t == MessageType.RESULT_SESSION_CLOSED
                        || t == MessageType.REMATCH_STATUS
                        || t == MessageType.LIVE_REVIEW
                        || t == MessageType.MATCH_RESULT
                        || t == MessageType.QUESTION_RESULT
                        || t == MessageType.ROUND_LEADERBOARD
                        || t == MessageType.ANSWER_STATUS
                        || t == MessageType.LIFELINE_RESULT
                        || t == MessageType.LIFELINE_STATUS
                        || t == MessageType.CHAT_MESSAGE);
      }
      case MATCH_COUNTDOWN -> phase = "MATCH_COUNTDOWN";
      case QUESTION -> {
        phase = "QUESTION_PREPARE";
        round = e.roundId();
        question = p.path("question");
        pending = false;
        accepted = false;
        data.remove(MessageType.ANSWER_STATUS);
      }
      case ROUND_COUNTDOWN -> phase = "ROUND_COUNTDOWN";
      case QUESTION_OPEN -> phase = "OPEN";
      case ANSWER_STATUS -> {
        // Accumulate who has answered this round, so the opponent's status survives our own.
        var answered = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        var ids = answered.putArray("answeredUserIds");
        JsonNode previous = data.get(MessageType.ANSWER_STATUS);
        if (previous != null) previous.path("answeredUserIds").forEach(ids::add);
        if (p.path("answered").asBoolean()) ids.add(p.path("userId").asLong());
        answered.setAll((com.fasterxml.jackson.databind.node.ObjectNode) p.deepCopy());
        answered.set("answeredUserIds", ids);
        p = answered;
      }
      case LIFELINE_STATUS -> {
        // Keep each player's latest lifeline, so our own use doesn't hide the opponent's.
        var status = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        var byUser = status.putObject("byUser");
        JsonNode previous = data.get(MessageType.LIFELINE_STATUS);
        if (previous != null)
          previous
              .path("byUser")
              .fields()
              .forEachRemaining(f -> byUser.set(f.getKey(), f.getValue()));
        byUser.set(p.path("userId").asText(), p.deepCopy());
        status.setAll((com.fasterxml.jackson.databind.node.ObjectNode) p.deepCopy());
        status.set("byUser", byUser);
        p = status;
      }
      case ANSWER_ACK -> {
        pending = false;
        accepted = true;
      }
      case QUESTION_RESULT -> {
        phase = "REVEAL";
        pending = false;
        accepted = true;
        p = withStreaks(p, data.get(MessageType.QUESTION_RESULT));
      }
      case ROUND_LEADERBOARD -> phase = "LEADERBOARD";
      case MATCH_RESULT -> {
        phase = "RESULT";
        resultMatch = e.matchId();
        pending = false;
        saves.put(e.matchId(), p.path("persistenceStatus").asText());
      }
      case CHAT_MESSAGE -> {
        UUID id = UUID.fromString(p.path("chatMessageId").asText());
        chats.putIfAbsent(id, p);
      }
      case MATCH_SNAPSHOT -> {
        phase =
            switch (p.path("phase").asText()) {
              case "ANSWERING" -> "OPEN";
              case "QUESTION_PREPARING" -> "QUESTION_PREPARE";
              default -> p.path("phase").asText();
            };
        question = p.get("question");
        JsonNode previousResult = data.get(MessageType.QUESTION_RESULT);
        // Keep our own copy of the same result, since it carries the streak counts.
        if (p.hasNonNull("latestResult")
            && (previousResult == null
                || previousResult.path("questionId").asLong()
                    != p.path("latestResult").path("questionId").asLong()))
          data.put(MessageType.QUESTION_RESULT, p.get("latestResult"));
        if (p.hasNonNull("standings")) {
          var leaderboard =
              com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
          leaderboard.set("standings", p.get("standings"));
          leaderboard.put("roundIndex", p.path("roundIndex").asInt());
          data.put(MessageType.ROUND_LEADERBOARD, leaderboard);
        }
        accepted = p.hasNonNull("acceptedAnswer");
        pending = false;
        round = e.roundId();
        if (p.has("lifelines")) data.put(MessageType.LIFELINE_RESULT, lifelines(p));
      }
      case RESULT_SESSION_CLOSED -> {
        match = null;
        phase = "RESULT_DETACHED";
        pending = false;
      }
      case EXIT_ACK -> {
        phase = "LOBBY";
        match = null;
        round = null;
      }
      case ERROR -> {
        error = p.path("code").asText() + ": " + p.path("message").asText();
        if (p.path("code").asText().equals("INVALID_ANSWER") && phase.equals("OPEN"))
          pending = false;
      }
      default -> {}
    }
    if (!phase.equals(s.phase())
        || e.type() == MessageType.MATCH_START
        || e.type() == MessageType.MATCH_SNAPSHOT) pv++;
    if (e.matchId() != null
        && e.type() != MessageType.MATCH_SNAPSHOT
        && e.type() != MessageType.MATCH_SAVE_STATUS) data.remove(MessageType.MATCH_SNAPSHOT);
    data.put(e.type(), p);
    return new ClientState(
        s.connectionEpoch(),
        profile,
        revision,
        match,
        resultMatch,
        round,
        seq,
        phase,
        pv,
        question,
        pending,
        accepted,
        data,
        saves,
        chats,
        error,
        now);
  }

  /**
   * Rebuilds the stored lifeline result from a snapshot: the kind used on the current question (or
   * none) and the kinds left for the rest of the match.
   */
  static JsonNode lifelines(JsonNode snapshot) {
    var state = snapshot.path("lifelines");
    var node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    node.put("questionId", snapshot.path("question").path("questionId").asLong(-1));
    node.put("kind", state.path("active").path(0).asText(""));
    node.set("removedOptionIds", state.path("removedOptionIds").deepCopy());
    node.set("remaining", state.path("remaining").deepCopy());
    return node;
  }

  /**
   * Adds {@code streaks}: each player's run of correct answers, counted from the previous result of
   * this match (results are cleared at MATCH_START). A repeated result keeps the old counts.
   */
  static JsonNode withStreaks(JsonNode result, JsonNode previous) {
    if (!result.isObject()) return result;
    var copy = (com.fasterxml.jackson.databind.node.ObjectNode) result.deepCopy();
    var streaks = copy.putObject("streaks");
    JsonNode before = previous == null ? null : previous.path("streaks");
    if (previous != null
        && previous.path("questionId").asLong() == result.path("questionId").asLong()) {
      before.fields().forEachRemaining(f -> streaks.put(f.getKey(), f.getValue().asInt()));
      return copy;
    }
    for (var outcome : result.path("outcomes")) {
      String id = outcome.path("userId").asText();
      int run = before == null ? 0 : before.path(id).asInt(0);
      streaks.put(id, outcome.path("correct").asBoolean(false) ? run + 1 : 0);
    }
    return copy;
  }
}
