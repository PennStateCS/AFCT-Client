package submission;

import java.util.function.LongSupplier;

/**
 * The Refresh button's cooldown decision, with an injectable clock so the rule
 * is testable. The button's countdown animation stays in the window; this class
 * only answers "may they refresh yet, and if not, for how much longer".
 */
class RefreshCooldown {

    static final int COOLDOWN_MS = 10_000;

    private final LongSupplier clock;
    private long lastRefreshMs = 0;

    RefreshCooldown(LongSupplier clock) {
        this.clock = clock;
    }

    static RefreshCooldown systemClock() {
        return new RefreshCooldown(System::currentTimeMillis);
    }

    /** True when a refresh is allowed now; a permitted call starts the cooldown. */
    boolean tryRefresh() {
        long now = clock.getAsLong();
        if (now - lastRefreshMs < COOLDOWN_MS) {
            return false;
        }
        lastRefreshMs = now;
        return true;
    }

    /** Whole seconds until the next refresh is allowed; 0 when allowed now. */
    long remainingSeconds() {
        long elapsed = clock.getAsLong() - lastRefreshMs;
        return Math.max(0, (COOLDOWN_MS - elapsed) / 1000);
    }

    /** True while the cooldown is running (drives the button's disabled state). */
    boolean coolingDown() {
        return clock.getAsLong() - lastRefreshMs < COOLDOWN_MS;
    }
}
