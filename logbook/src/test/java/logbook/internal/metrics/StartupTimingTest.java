package logbook.internal.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link StartupTiming} のテスト。
 */
class StartupTimingTest {

    @BeforeEach
    @AfterEach
    void reset() {
        StartupTiming.resetForTest();
    }

    @Test
    void markBeforeBeginLauncherIsNoOp() {
        StartupTiming.mark("plugin");
        StartupTiming.completeWindowShown();
        StartupTiming.completeUiReady();
        assertEquals(0L, StartupTiming.jvmToLauncherMillis());
        assertEquals(0L, StartupTiming.toWindowShownMillis());
        assertEquals(0L, StartupTiming.toUiReadyMillis());
        assertEquals(0L, StartupTiming.jvmToWindowShownMillis());
        assertEquals(0L, StartupTiming.jvmToUiReadyMillis());
    }

    @Test
    void beginLauncherIsIdempotent() {
        StartupTiming.beginLauncher();
        long first = StartupTiming.jvmToLauncherMillis();
        StartupTiming.beginLauncher();
        assertEquals(first, StartupTiming.jvmToLauncherMillis());
        assertTrue(first >= 0L);
    }

    @Test
    void milestonesCompleteOnceAndElapsedIsMonotonic() throws Exception {
        StartupTiming.beginLauncher();
        Thread.sleep(30);
        StartupTiming.mark("plugin");
        Thread.sleep(30);
        StartupTiming.completeWindowShown();
        long windowShown = StartupTiming.toWindowShownMillis();
        long jvmWindowShown = StartupTiming.jvmToWindowShownMillis();
        Thread.sleep(30);
        StartupTiming.completeUiReady();
        long uiReady = StartupTiming.toUiReadyMillis();
        long jvmUiReady = StartupTiming.jvmToUiReadyMillis();

        assertTrue(windowShown >= 15L, "windowShown=" + windowShown);
        assertTrue(uiReady >= windowShown, "uiReady=" + uiReady + " windowShown=" + windowShown);
        assertTrue(jvmWindowShown >= StartupTiming.jvmToLauncherMillis());
        assertTrue(jvmUiReady >= jvmWindowShown);

        StartupTiming.completeWindowShown();
        StartupTiming.completeUiReady();
        assertEquals(windowShown, StartupTiming.toWindowShownMillis());
        assertEquals(uiReady, StartupTiming.toUiReadyMillis());
        assertEquals(jvmWindowShown, StartupTiming.jvmToWindowShownMillis());
        assertEquals(jvmUiReady, StartupTiming.jvmToUiReadyMillis());
    }

    @Test
    void metricsMxBeanDelegatesStartupValues() throws Exception {
        StartupTiming.beginLauncher();
        Thread.sleep(30);
        StartupTiming.completeWindowShown();
        Thread.sleep(30);
        StartupTiming.completeUiReady();

        LogbookMetrics metrics = new LogbookMetrics();
        assertEquals(StartupTiming.jvmToLauncherMillis(), metrics.getStartupJvmToLauncherMillis());
        assertEquals(StartupTiming.toWindowShownMillis(), metrics.getStartupToWindowShownMillis());
        assertEquals(StartupTiming.toUiReadyMillis(), metrics.getStartupToUiReadyMillis());
        assertEquals(StartupTiming.jvmToWindowShownMillis(), metrics.getStartupJvmToWindowShownMillis());
        assertEquals(StartupTiming.jvmToUiReadyMillis(), metrics.getStartupJvmToUiReadyMillis());
        assertTrue(metrics.getStartupToUiReadyMillis() >= 15L);
    }
}
