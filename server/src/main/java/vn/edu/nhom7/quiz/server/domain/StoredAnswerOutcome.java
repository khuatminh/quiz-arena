package vn.edu.nhom7.quiz.server.domain;

import java.time.Instant;
import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public record StoredAnswerOutcome(
    long userId,
    String answerJson,
    Long elapsedNanos,
    Instant receivedAt,
    AnswerOutcomeType outcome,
    Boolean correct,
    int earnedPoints,
    int scoreBefore,
    int totalScore,
    UUID requestId) {}
