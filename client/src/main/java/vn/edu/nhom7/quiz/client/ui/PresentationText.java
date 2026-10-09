package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Human-readable labels only; scoring and answer normalization stay on the server. */
public final class PresentationText {
  private PresentationText() {}

  public static String answer(JsonNode question, JsonNode answer) {
    if (answer == null || answer.isNull()) return "Không trả lời";
    if (answer.isArray()) {
      var values = new ArrayList<String>();
      for (var item : answer) values.add(answer(question, item));
      return String.join(", ", values);
    }
    if (answer.isBoolean()) return answer.booleanValue() ? "Đúng" : "Sai";
    String text = answer.asText();
    if (question != null)
      for (var option : question.path("options"))
        if (option.path("id").asText().equals(text)) return option.path("text").asText();
    return text;
  }

  public static String outcome(String code) {
    return switch (code) {
      case "ANSWERED" -> "Đã trả lời";
      case "TIMEOUT" -> "Không trả lời";
      case "ABANDONED" -> "Câu chưa được chấm";
      case "WIN", "WON" -> "Thắng";
      case "LOSS", "LOST" -> "Thua";
      case "DRAW" -> "Hòa";
      case "COMPLETED" -> "Hoàn thành";
      case "FORFEIT" -> "Kết thúc do rời trận";
      case "ABORTED" -> "Trận bị hủy";
      default -> code;
    };
  }

  public static String reason(String code) {
    return switch (code) {
      case "NORMAL" -> "Đã hoàn thành đủ câu hỏi";
      case "USER_EXIT" -> "Người chơi rời trận";
      case "LOGOUT" -> "Người chơi đăng xuất";
      case "DISCONNECT" -> "Người chơi mất kết nối";
      case "BOTH_DISCONNECTED" -> "Cả hai người chơi mất kết nối";
      case "CLIENT_NOT_READY" -> "Người chơi chưa sẵn sàng";
      case "INTERNAL_ERROR" -> "Lỗi hệ thống";
      case "SERVER_SHUTDOWN" -> "Server dừng hoạt động";
      default -> code;
    };
  }

  public static String playerName(JsonNode players, long userId, long self) {
    if (players != null)
      for (var p : players)
        if (p.path("userId").asLong() == userId)
          return p.path("displayName").asText() + (userId == self ? " · Bạn" : "");
    return userId == self ? "Bạn" : "Đối thủ";
  }
}
