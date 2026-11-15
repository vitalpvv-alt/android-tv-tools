package dev.mert.tvassistant;

/** No sleeps, queued commands, retries or payment fallback after a free rate limit. */
final class FreeRateGate {
  private long next;
  synchronized void reserve(long now, int intervalSeconds) {
    long seconds = remaining(now);
    if (seconds > 0) throw new IllegalStateException("Сервис ИИ будет доступен через " + seconds + " сек. Обычные команды работают сразу.");
    next = now + Math.max(0, intervalSeconds) * 1000L;
  }
  synchronized long remaining(long now) { return Math.max(0, (next - now + 999) / 1000); }
  synchronized void backoff(long now, int seconds) { next = Math.max(next, now + Math.max(0, Math.min(seconds, 3600)) * 1000L); }
}
