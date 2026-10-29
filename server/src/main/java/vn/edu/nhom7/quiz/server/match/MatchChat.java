package vn.edu.nhom7.quiz.server.match;

import static vn.edu.nhom7.quiz.server.match.MatchPayloads.obj;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/** Match chat validation, duplicate requests and per-player rate limits, under the match lock. */
final class MatchChat {
  private final MatchManager manager;

  MatchChat(MatchManager manager) {
    this.manager = manager;
  }

  void chat(Match match, SessionContext caller, Envelope command) {
    if (match.phase == MatchPhase.CLOSED || match.phase == MatchPhase.CANCELLED)
      throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
    if (match.phase == MatchPhase.RESULT && match.attached.size() != 2)
      throw new ProtocolException("INVALID_STATE", "INVALID_STATE");
    JsonNode previous = match.chats.get(command.requestId());
    if (previous != null) {
      manager.emit(match, "CHAT_MESSAGE", command.requestId(), caller.connectionId(), previous);
      return;
    }
    JsonNode raw = command.payload().get("text");
    if (raw == null || !raw.isTextual())
      throw new ProtocolException("INVALID_INPUT", "INVALID_INPUT");
    String text = raw.textValue().strip();
    int count = text.codePointCount(0, text.length());
    if (count < 1 || count > 300) throw new ProtocolException("INVALID_INPUT", "INVALID_INPUT");
    long now = manager.clock.nanoTime();
    Deque<Long> times = match.chatTimes.computeIfAbsent(caller.userId(), id -> new ArrayDeque<>());
    while (!times.isEmpty() && now - times.getFirst() >= 10_000_000_000L) times.removeFirst();
    if (times.size() >= 5)
      throw new ProtocolException(
          "RATE_LIMITED",
          "Too many chat messages",
          obj(
              "retryAfterMs",
              Math.max(1, (10_000_000_000L - (now - times.getFirst()) + 999_999L) / 1_000_000L)));
    times.addLast(now);
    JsonNode message =
        obj(
            "chatMessageId",
            UUID.randomUUID(),
            "senderUserId",
            caller.userId(),
            "displayName",
            match.profiles.get(match.playerIndex(caller.userId())).displayName(),
            "text",
            text,
            "sentAtMs",
            manager.now());
    match.chats.put(command.requestId(), message);
    manager.emit(match, "CHAT_MESSAGE", command.requestId(), null, message);
  }
}
