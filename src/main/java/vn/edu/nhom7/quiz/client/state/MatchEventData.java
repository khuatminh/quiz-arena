package vn.edu.nhom7.quiz.client.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

/** Immutable JSON projections accumulated across match events and snapshots. */
final class MatchEventData {
  private MatchEventData() {}

  /**
   * Rebuilds the stored lifeline result from a snapshot: the kind used on the current question (or
   * none) and the kinds left for the rest of the match.
   */
  static JsonNode lifelines(JsonNode snapshot) {
    var state = snapshot.path("lifelines");
    var node = JsonNodeFactory.instance.objectNode();
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
    var copy = (ObjectNode) result.deepCopy();
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

  static JsonNode answeredStatus(JsonNode p, JsonNode previous) {
    var answered = JsonNodeFactory.instance.objectNode();
    var ids = answered.putArray("answeredUserIds");
    if (previous != null) previous.path("answeredUserIds").forEach(ids::add);
    if (p.path("answered").asBoolean()) ids.add(p.path("userId").asLong());
    answered.setAll((ObjectNode) p.deepCopy());
    answered.set("answeredUserIds", ids);
    return answered;
  }

  static JsonNode lifelineStatus(JsonNode p, JsonNode previous) {
    var status = JsonNodeFactory.instance.objectNode();
    var byUser = status.putObject("byUser");
    if (previous != null)
      previous.path("byUser").fields().forEachRemaining(f -> byUser.set(f.getKey(), f.getValue()));
    byUser.set(p.path("userId").asText(), p.deepCopy());
    status.setAll((ObjectNode) p.deepCopy());
    status.set("byUser", byUser);
    return status;
  }
}
