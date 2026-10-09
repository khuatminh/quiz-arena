package vn.edu.nhom7.quiz.server.domain;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public record QuestionSnapshot(
    long questionId,
    long quizId,
    QuestionType questionType,
    String content,
    List<Payloads.Option> options,
    String answerKeyJson,
    String explanation,
    String questionAssetId,
    String explanationAssetId) {
  public QuestionSnapshot {
    options = List.copyOf(options);
  }
}
