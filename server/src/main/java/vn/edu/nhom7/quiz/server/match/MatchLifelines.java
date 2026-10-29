package vn.edu.nhom7.quiz.server.match;

import static vn.edu.nhom7.quiz.server.match.MatchPayloads.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.QuestionSnapshot;
import vn.edu.nhom7.quiz.server.session.SessionContext;

/** Validates and spends lifelines. All methods read or update state under the match lock. */
final class MatchLifelines {
  private final MatchManager manager;

  MatchLifelines(MatchManager manager) {
    this.manager = manager;
  }

  /**
   * Lifelines, once each per match and at most one per question, before the caller answers.
   * DOUBLE_POINTS doubles this question's points (a wrong answer still scores 0). REMOVE_ONE hides
   * one wrong option and FIFTY_FIFTY keeps the key and one wrong option; both need a single-choice
   * question with at least three options.
   */
  void lifeline(Match match, SessionContext caller, Envelope command) {
    long received = manager.clock.nanoTime();
    match.requireCurrentRound(command);
    String kind = command.payload().path("kind").asText();
    if (!Payloads.LIFELINES.contains(kind))
      throw new ProtocolException("INVALID_LIFELINE", "Unknown lifeline");
    if (match.phase != MatchPhase.ANSWERING
        || received > match.deadline
        || match.accepted.containsKey(caller.userId()))
      throw new ProtocolException("INVALID_STATE", "Lifelines need an open, unanswered question");
    Set<String> used = match.lifelinesUsed.computeIfAbsent(caller.userId(), k -> new HashSet<>());
    if (used.contains(kind)) throw new ProtocolException("LIFELINE_USED", "Lifeline already used");
    if (match.roundLifeline.containsKey(caller.userId()))
      throw new ProtocolException("LIFELINE_LIMIT", "One lifeline per question");
    QuestionSnapshot question = match.question();
    List<String> removed = List.of();
    if (!kind.equals("DOUBLE_POINTS")) {
      if (question.questionType() != QuestionType.SINGLE_CHOICE || question.options().size() < 3)
        throw new ProtocolException(
            "LIFELINE_UNAVAILABLE", "Needs a single-choice question with three or more options");
      String key = correctAnswer(question).asText();
      List<String> wrong = new ArrayList<>();
      for (var option : question.options()) if (!option.id().equals(key)) wrong.add(option.id());
      Collections.shuffle(wrong, ThreadLocalRandom.current());
      Set<String> hidden =
          new HashSet<>(wrong.subList(0, kind.equals("REMOVE_ONE") ? 1 : wrong.size() - 1));
      removed =
          question.options().stream().map(Payloads.Option::id).filter(hidden::contains).toList();
    }
    used.add(kind);
    match.roundLifeline.put(caller.userId(), new Match.Lifeline(kind, removed));
    manager.emit(
        match,
        "LIFELINE_RESULT",
        command.requestId(),
        caller.connectionId(),
        obj(
            "questionId",
            question.questionId(),
            "kind",
            kind,
            "removedOptionIds",
            removed,
            "remaining",
            remainingLifelines(match, caller.userId())));
    manager.emit(
        match,
        "LIFELINE_STATUS",
        null,
        null,
        obj("userId", caller.userId(), "questionId", question.questionId(), "kind", kind));
  }

  static List<String> remainingLifelines(Match match, long user) {
    Set<String> used = match.lifelinesUsed.getOrDefault(user, Set.of());
    return Payloads.LIFELINES.stream().filter(k -> !used.contains(k)).toList();
  }

  static JsonNode lifelineState(Match match, long user) {
    var current = match.roundLifeline.get(user);
    return obj(
        "remaining",
        remainingLifelines(match, user),
        "active",
        current == null ? List.of() : List.of(current.kind()),
        "removedOptionIds",
        current == null ? List.of() : current.removed());
  }
}
