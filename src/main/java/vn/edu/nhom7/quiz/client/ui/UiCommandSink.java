package vn.edu.nhom7.quiz.client.ui;

import java.util.UUID;
import vn.edu.nhom7.quiz.common.protocol.MessageType;

public interface UiCommandSink {
  UUID send(MessageType type, UUID matchId, UUID roundId, Object payload);
}
