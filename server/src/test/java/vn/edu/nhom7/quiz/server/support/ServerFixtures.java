package vn.edu.nhom7.quiz.server.support;

import java.time.*;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.domain.*;
import vn.edu.nhom7.quiz.server.session.SessionContext;

public final class ServerFixtures {
  private ServerFixtures() {}

  public static final UUID CONNECTION1 = UUID.fromString("00000000-0000-0000-0000-000000000101"),
      CONNECTION2 = UUID.fromString("00000000-0000-0000-0000-000000000102");

  public static List<SessionContext> players() {
    return List.of(new SessionContext(CONNECTION1, 101), new SessionContext(CONNECTION2, 102));
  }

  public static Payloads.QuizSummary quiz() {
    return new Payloads.QuizSummary(1, "Kiến thức tổng hợp", 1, "Tổng hợp", null, "AVAILABLE", 10);
  }

  public static List<QuestionSnapshot> questions() {
    List<QuestionSnapshot> list = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      QuestionType type =
          i <= 4
              ? QuestionType.SINGLE_CHOICE
              : i <= 6
                  ? QuestionType.MULTIPLE_CHOICE
                  : i <= 8 ? QuestionType.TRUE_FALSE : QuestionType.SHORT_ANSWER;
      list.add(
          new QuestionSnapshot(
              i,
              1,
              type,
              "Câu " + i,
              type == QuestionType.SINGLE_CHOICE || type == QuestionType.MULTIPLE_CHOICE
                  ? List.of(
                      new Payloads.Option("A", "Một"),
                      new Payloads.Option("B", "Hai"),
                      new Payloads.Option("C", "Ba"))
                  : List.of(),
              switch (type) {
                case SINGLE_CHOICE -> "\"A\"";
                case MULTIPLE_CHOICE -> "[\"A\",\"B\"]";
                case TRUE_FALSE -> "true";
                case SHORT_ANSWER -> "[\"Hà Nội\",\"Thủ đô Hà Nội\"]";
              },
              "Giải thích " + i,
              null,
              null));
    }
    return List.copyOf(list);
  }

  public static MatchSummary completedSummary() {
    Instant start = Instant.parse("2026-10-05T00:00:00Z");
    List<RoundSummary> rounds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      QuestionSnapshot q = questions().get(i);
      String answer =
          q.questionType() == QuestionType.SHORT_ANSWER ? "\"Hà Nội\"" : q.answerKeyJson();
      rounds.add(
          new RoundSummary(
              new UUID(0, i + 1),
              i + 1,
              q,
              true,
              List.of(
                  new StoredAnswerOutcome(
                      101,
                      answer,
                      1_000_000_000L,
                      start.plusSeconds(i * 25L + 1),
                      AnswerOutcomeType.ANSWERED,
                      true,
                      3,
                      i * 3,
                      (i + 1) * 3,
                      new UUID(1, i)),
                  new StoredAnswerOutcome(
                      102, null, null, null, AnswerOutcomeType.TIMEOUT, false, 0, 0, 0, null))));
    }
    return new MatchSummary(
        new UUID(7, 1),
        1,
        "Kiến thức tổng hợp",
        new ParticipantSummary(101, "An", "avatar-1", 30, 10),
        new ParticipantSummary(102, "Bình", "avatar-2", 0, 0),
        start,
        start.plusSeconds(250),
        FinishReason.COMPLETED,
        ReasonCode.NORMAL,
        MatchOutcome.PLAYER1_WIN,
        10,
        10,
        rounds);
  }
}
