package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.Set;

/** Shared numeric contracts and an explicit allowlist for public range settings. */
public final class InteractiveQuestions {
  private InteractiveQuestions() {}

  public static JsonNode publicConfig(QuestionType type, JsonNode key) {
    if (type != QuestionType.RANGE) return NullNode.instance;
    ObjectNode config = JsonNodeFactory.instance.objectNode();
    for (String field : new String[] {"min", "max", "step", "unit"})
      config.set(field, key.path(field).deepCopy());
    return config;
  }

  public static BigDecimal number(JsonNode node) {
    if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue()))
      throw new IllegalArgumentException("Expected a finite number");
    BigDecimal value = node.decimalValue();
    if (value.abs().compareTo(new BigDecimal("1000000000000")) > 0
        || value.scale() > 9
        || value.stripTrailingZeros().precision() > 15)
      throw new IllegalArgumentException("Number outside supported precision");
    return value;
  }

  public static void validateRange(JsonNode key) {
    fields(key, Set.of("min", "max", "step", "unit", "acceptedMin", "acceptedMax"));
    BigDecimal min = number(key.get("min")), max = number(key.get("max"));
    BigDecimal step = number(key.get("step"));
    BigDecimal lo = number(key.get("acceptedMin")), hi = number(key.get("acceptedMax"));
    // MySQL JSON stores fractional numbers as doubles. Keep every slider value within
    // fifteen decimal digits so a draft/publication/history round trip is stable.
    int integerDigits = Math.max(min.precision() - min.scale(), max.precision() - max.scale());
    int scale = Math.max(0, Math.max(step.scale(), Math.max(min.scale(), max.scale())));
    if (min.compareTo(max) >= 0
        || integerDigits + scale > 15
        || step.signum() <= 0
        || max.subtract(min).remainder(step).signum() != 0
        || max.subtract(min).divide(step).compareTo(new BigDecimal("1000000")) > 0
        || lo.compareTo(hi) > 0
        || lo.compareTo(min) < 0
        || hi.compareTo(max) > 0
        || !key.path("unit").isTextual()
        || key.path("unit").asText().codePointCount(0, key.path("unit").asText().length()) > 32)
      throw new IllegalArgumentException("Invalid range settings");
    // At least one value reachable on the slider must be accepted.
    BigDecimal first =
        min.add(lo.subtract(min).divide(step, 0, java.math.RoundingMode.CEILING).multiply(step));
    if (first.compareTo(hi) > 0) throw new IllegalArgumentException("No accepted slider value");
  }

  public static void validateRegion(JsonNode key) {
    fields(key, Set.of("x", "y", "width", "height"));
    BigDecimal x = number(key.get("x")), y = number(key.get("y"));
    BigDecimal w = number(key.get("width")), h = number(key.get("height"));
    if (x.signum() < 0
        || y.signum() < 0
        || w.signum() <= 0
        || h.signum() <= 0
        || x.add(w).compareTo(BigDecimal.ONE) > 0
        || y.add(h).compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Region must be inside the image");
  }

  public static void fields(JsonNode node, Set<String> fields) {
    if (node == null || !node.isObject() || node.size() != fields.size())
      throw new IllegalArgumentException("Invalid fields");
    node.fieldNames()
        .forEachRemaining(
            name -> {
              if (!fields.contains(name)) throw new IllegalArgumentException("Unknown field");
            });
  }
}
