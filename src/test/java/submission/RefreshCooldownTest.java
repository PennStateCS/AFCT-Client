package submission;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The Refresh cooldown rule, on a hand-cranked clock. */
class RefreshCooldownTest {

    @Test
    void firstRefreshIsAllowedThenTheCooldownHolds() {
        AtomicLong now = new AtomicLong(1_000_000);
        RefreshCooldown cooldown = new RefreshCooldown(now::get);

        assertTrue(cooldown.tryRefresh());
        assertFalse(cooldown.tryRefresh(), "Immediately again must be refused");
        assertTrue(cooldown.coolingDown());

        now.addAndGet(RefreshCooldown.COOLDOWN_MS - 1);
        assertFalse(cooldown.tryRefresh(), "One ms early is still refused");

        now.addAndGet(1);
        assertFalse(cooldown.coolingDown());
        assertTrue(cooldown.tryRefresh(), "At the boundary the refresh is allowed");
    }

    @Test
    void remainingSecondsCountsDownToZero() {
        AtomicLong now = new AtomicLong(0);
        RefreshCooldown cooldown = new RefreshCooldown(now::get);
        cooldown.tryRefresh();

        assertEquals(RefreshCooldown.COOLDOWN_MS / 1000, cooldown.remainingSeconds(),
                "Just after refreshing, the whole window remains");
        now.addAndGet(4_000);
        assertEquals((RefreshCooldown.COOLDOWN_MS - 4_000) / 1000, cooldown.remainingSeconds());
        now.addAndGet(RefreshCooldown.COOLDOWN_MS);
        assertEquals(0, cooldown.remainingSeconds());
    }
}
