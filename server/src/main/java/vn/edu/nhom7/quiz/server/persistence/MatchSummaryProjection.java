package vn.edu.nhom7.quiz.server.persistence;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import vn.edu.nhom7.quiz.server.domain.*;

/** Canonical immutable content for idempotent saves at MySQL's millisecond precision. */
final class MatchSummaryProjection {
  private static final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private MatchSummaryProjection() {}

  static String of(MatchSummary summary) {
    try {
      var sorted =
          summary.rounds().stream()
              .sorted(Comparator.comparingInt(RoundSummary::roundIndex))
              .map(
                  round ->
                      new RoundSummary(
                          round.roundId(),
                          round.roundIndex(),
                          round.question(),
                          round.revealed(),
                          round.outcomes().stream()
                              .sorted(Comparator.comparingLong(StoredAnswerOutcome::userId))
                              .toList()))
              .toList();
      var copy =
          new MatchSummary(
              summary.matchId(),
              summary.quizId(),
              summary.quizTitle(),
              summary.player1(),
              summary.player2(),
              summary.startedAt(),
              summary.endedAt(),
              summary.finishReason(),
              summary.reasonCode(),
              summary.outcome(),
              summary.completedRounds(),
              summary.openedRounds(),
              sorted,
              summary.ranked(),
              summary.quizVersionId(),
              summary.totalRounds());
      var tree = json.valueToTree(copy);
      normalize(tree);
      return tree.toString();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid summary", e);
    }
  }

  private static void normalize(JsonNode node) {
    if (node.isObject()) {
      var o = (com.fasterxml.jackson.databind.node.ObjectNode) node;
      for (var name : List.of("startedAt", "endedAt", "receivedAt")) {
        if (o.hasNonNull(name)) {
          var v = o.get(name);
          if (v.isTextual()) o.put(name, Instant.parse(v.asText()).toEpochMilli());
        }
      }
      if (o.has("elapsedNanos") && !o.get("elapsedNanos").isNull())
        o.put("elapsedNanos", o.get("elapsedNanos").asLong() / 1_000_000);
      o.remove("avatarId");
      for (var name : List.of("answerJson", "answerKeyJson")) {
        if (o.hasNonNull(name))
          try {
            o.set(name, json.readTree(o.get(name).asText()));
          } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON");
          }
      }
    }
    for (var child : node) normalize(child);
  }
}
