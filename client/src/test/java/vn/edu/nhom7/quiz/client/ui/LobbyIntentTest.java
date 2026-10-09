package vn.edu.nhom7.quiz.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class LobbyIntentTest {
  @Test
  void onlyAvailableOtherFreePlayers() {
    var f = JsonNodeFactory.instance;
    var q = f.objectNode().put("availability", "AVAILABLE");
    var u = f.objectNode().put("userId", 2).put("status", "FREE");
    assertTrue(LobbyView.canChallenge(q, u, 1, false));
    assertFalse(LobbyView.canChallenge(q, u, 2, false));
    assertFalse(LobbyView.canChallenge(q, u, 1, true));
    u.put("status", "BUSY");
    assertFalse(LobbyView.canChallenge(q, u, 1, false));
  }
}
