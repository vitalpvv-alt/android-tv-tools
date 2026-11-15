package ru.vital.tv.sunset;

import java.util.ArrayDeque;

/** Stop instead of fighting firmware which repeatedly resets a requested backlight value. */
final class RateGuard {
    private final ArrayDeque<Long> attempts=new ArrayDeque<>();
    boolean permit(long monotonicMillis) {
        while(!attempts.isEmpty()&&monotonicMillis-attempts.peekFirst()>=30_000)attempts.removeFirst();
        if(attempts.size()>=3)return false;
        attempts.addLast(monotonicMillis);return true;
    }
}
