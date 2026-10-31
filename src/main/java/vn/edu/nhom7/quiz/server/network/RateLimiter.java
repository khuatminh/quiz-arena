package vn.edu.nhom7.quiz.server.network;

public final class RateLimiter {
  private final int limit;
  private final long window;
  private long started = Long.MIN_VALUE;
  private int count;

  public RateLimiter(int limit, long windowNanos) {
    this.limit = limit;
    this.window = windowNanos;
  }

  public synchronized boolean available(long now) {
    return started == Long.MIN_VALUE || now - started >= window || count < limit;
  }

  public synchronized boolean allow(long now) {
    if (started == Long.MIN_VALUE || now - started >= window) {
      started = now;
      count = 0;
    }
    if (count >= limit) return false;
    count++;
    return true;
  }
}
