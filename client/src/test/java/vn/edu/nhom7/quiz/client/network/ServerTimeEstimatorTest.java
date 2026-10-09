package vn.edu.nhom7.quiz.client.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ServerTimeEstimatorTest {
  @Test
  void minRttAndClamp() {
    var t = new ServerTimeEstimator();
    t.sample(100, 200, 250);
    assertEquals(100, t.offsetMs());
    t.sample(300, 320, 410);
    assertEquals(100, t.offsetMs());
    assertEquals(0, t.remainingMs(100, 200, 15000));
  }
}
