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
                    t == MessageType.RESULT_SESSION_CLOSED
                        || t == MessageType.REMATCH_STATUS
                        || t == MessageType.MATCH_RESULT
                        || t == MessageType.QUESTION_RESULT
                        || t == MessageType.ROUND_LEADERBOARD
                        || t == MessageType.CHAT_MESSAGE);
      }
      case MATCH_COUNTDOWN -> phase = "MATCH_COUNTDOWN";
      case QUESTION -> {
        phase = "QUESTION_PREPARE";
        round = e.roundId();
        question = p.path("question");
        pending = false;
        accepted = false;
      }
      case ROUND_COUNTDOWN -> phase = "ROUND_COUNTDOWN";
      case QUESTION_OPEN -> phase = "OPEN";
      case ANSWER_ACK -> {
        pending = false;
        accepted = true;
      }
      case QUESTION_RESULT -> {
        phase = "REVEAL";
        pending = false;
        accepted = true;
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
        if (p.hasNonNull("latestResult"))
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
}
