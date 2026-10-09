package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record Envelope(
    int protocolVersion,
    MessageType type,
    UUID requestId,
    UUID matchId,
    UUID roundId,
    Long eventSeq,
    JsonNode payload) {}
