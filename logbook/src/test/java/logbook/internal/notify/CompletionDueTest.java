package logbook.internal.notify;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionDueTest {

    private static final Duration INTERVAL = Duration.ofSeconds(60);

    @Test
    void remainingOverOneMinuteIsNotDue() {
        assertFalse(CompletionDue.due(Duration.ofMinutes(2), 0L, 1_000L, true, INTERVAL));
    }

    @Test
    void firstTimeWithinOneMinuteIsDue() {
        long now = 1_700_000_000_000L;
        assertTrue(CompletionDue.due(Duration.ofSeconds(30), 0L, now, false, INTERVAL));
    }

    @Test
    void repeatRequiresRemindAndInterval() {
        long now = 120_000L;
        long last = now - 30_000L;
        assertFalse(CompletionDue.due(Duration.ofSeconds(10), last, now, true, INTERVAL));
        assertFalse(CompletionDue.due(Duration.ofSeconds(10), last, now, false, INTERVAL));
        assertTrue(CompletionDue.due(Duration.ofSeconds(10), now - 60_000L, now, true, INTERVAL));
        assertFalse(CompletionDue.due(Duration.ofSeconds(10), now - 60_000L, now, false, INTERVAL));
    }
}
