package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** Public-safe error only; never store raw credentials or private question payloads. */
public class ProtocolException extends RuntimeException {
  private final String code;
  private final JsonNode details;

  public ProtocolException(String code, String safeMessage) {
    this(code, safeMessage, JsonNodeFactory.instance.objectNode());
  }

  public ProtocolException(String code, String safeMessage, JsonNode details) {
    super(safeMessage);
    this.code = code;
    this.details = details == null ? JsonNodeFactory.instance.objectNode() : details.deepCopy();
  }

  public String code() {
    return code;
  }

  public JsonNode details() {
    return details.deepCopy();
  }
}
