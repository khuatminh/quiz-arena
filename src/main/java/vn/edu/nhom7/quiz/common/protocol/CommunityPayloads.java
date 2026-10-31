package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class CommunityPayloads {
  private CommunityPayloads() {}

  public record AuthorRequest(String action, long quizId, JsonNode data) {}

  public record AuthorResult(String action, JsonNode data) {}

  public record DraftQuestion(
      String questionType,
      String content,
      List<Payloads.Option> options,
      JsonNode answerKey,
      String explanation,
      String questionAssetId,
      String explanationAssetId) {}
}
