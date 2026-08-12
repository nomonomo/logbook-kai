package logbook.internal.metrics;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 起動フェーズの所要時間を記録します。
 * <p>
 * ログは DEBUG のみです。配布既定の logback では出力されません。
 * 調査時は {@code logbook.internal.metrics.StartupTiming} を DEBUG にし、
 * app アペンダの ThresholdFilter を DEBUG まで下げてください。
 * </p>
 */
@Slf4j
public final class StartupTiming {

    private static final Object LOCK = new Object();

    private static final long JVM_START_MILLIS = ManagementFactory.getRuntimeMXBean().getStartTime();

    private static long launcherStartNanos;

    private static long lastMarkNanos;

    private static long jvmToLauncherMillis;

    private static long toWindowShownMillis;

    private static long toUiReadyMillis;

    private static long jvmToWindowShownMillis;

    private static long jvmToUiReadyMillis;

    private static boolean windowShownCompleted;

    private static boolean uiReadyCompleted;

    private StartupTiming() {
    }

    /**
     * {@code Launcher.main} 先頭で呼び出します。
     * JVM 起動からここまでの時間を記録します。
     */
    public static void beginLauncher() {
        synchronized (LOCK) {
            if (launcherStartNanos != 0L) {
                return;
            }
            long nowMillis = System.currentTimeMillis();
            launcherStartNanos = System.nanoTime();
            lastMarkNanos = launcherStartNanos;
            jvmToLauncherMillis = Math.max(0L, nowMillis - JVM_START_MILLIS);
            logPhase("jvmToLauncher", jvmToLauncherMillis, jvmToLauncherMillis);
        }
    }

    /**
     * 直前のマークからの経過をフェーズとして記録します。
     *
     * @param phase フェーズ名
     */
    public static void mark(String phase) {
        synchronized (LOCK) {
            if (launcherStartNanos == 0L) {
                return;
            }
            long nowNanos = System.nanoTime();
            long elapsedMs = nanosToMillis(nowNanos - lastMarkNanos);
            lastMarkNanos = nowNanos;
            logPhase(phase, elapsedMs, nanosToMillis(nowNanos - launcherStartNanos));
        }
    }

    /**
     * 指定起点からの経過をフェーズとして記録します（直前マークは更新しません）。
     *
     * @param phase フェーズ名
     * @param startNanos {@link System#nanoTime()} の起点
     */
    public static void markFrom(String phase, long startNanos) {
        synchronized (LOCK) {
            if (launcherStartNanos == 0L) {
                return;
            }
            long nowNanos = System.nanoTime();
            long elapsedMs = nanosToMillis(nowNanos - startNanos);
            logPhase(phase, elapsedMs, nanosToMillis(nowNanos - launcherStartNanos));
        }
    }

    /**
     * メインウィンドウ表示（{@code stage.show()}）を確定します。1 回のみ有効です。
     */
    public static void completeWindowShown() {
        synchronized (LOCK) {
            if (launcherStartNanos == 0L || windowShownCompleted) {
                return;
            }
            windowShownCompleted = true;
            long nowNanos = System.nanoTime();
            long nowMillis = System.currentTimeMillis();
            long elapsedMs = nanosToMillis(nowNanos - lastMarkNanos);
            lastMarkNanos = nowNanos;
            toWindowShownMillis = nanosToMillis(nowNanos - launcherStartNanos);
            jvmToWindowShownMillis = Math.max(0L, nowMillis - JVM_START_MILLIS);
            logPhase("windowShown", elapsedMs, toWindowShownMillis);
            logMilestone("windowShown", jvmToWindowShownMillis, toWindowShownMillis);
        }
    }

    /**
     * 初回 UI 更新完了を確定します。1 回のみ有効です。
     */
    public static void completeUiReady() {
        synchronized (LOCK) {
            if (launcherStartNanos == 0L || uiReadyCompleted) {
                return;
            }
            uiReadyCompleted = true;
            long nowNanos = System.nanoTime();
            long nowMillis = System.currentTimeMillis();
            toUiReadyMillis = nanosToMillis(nowNanos - launcherStartNanos);
            jvmToUiReadyMillis = Math.max(0L, nowMillis - JVM_START_MILLIS);
            logMilestone("uiReady", jvmToUiReadyMillis, toUiReadyMillis);
        }
    }

    /**
     * JVM 起動から {@code Launcher.main} 先頭までのミリ秒。未計測は 0。
     *
     * @return ミリ秒
     */
    public static long jvmToLauncherMillis() {
        synchronized (LOCK) {
            return jvmToLauncherMillis;
        }
    }

    /**
     * Launcher 先頭からウィンドウ表示までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    public static long toWindowShownMillis() {
        synchronized (LOCK) {
            return toWindowShownMillis;
        }
    }

    /**
     * Launcher 先頭から UI 操作可能までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    public static long toUiReadyMillis() {
        synchronized (LOCK) {
            return toUiReadyMillis;
        }
    }

    /**
     * JVM 起動からウィンドウ表示までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    public static long jvmToWindowShownMillis() {
        synchronized (LOCK) {
            return jvmToWindowShownMillis;
        }
    }

    /**
     * JVM 起動から UI 操作可能までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    public static long jvmToUiReadyMillis() {
        synchronized (LOCK) {
            return jvmToUiReadyMillis;
        }
    }

    /**
     * テスト用に状態を初期化します。
     */
    static void resetForTest() {
        synchronized (LOCK) {
            launcherStartNanos = 0L;
            lastMarkNanos = 0L;
            jvmToLauncherMillis = 0L;
            toWindowShownMillis = 0L;
            toUiReadyMillis = 0L;
            jvmToWindowShownMillis = 0L;
            jvmToUiReadyMillis = 0L;
            windowShownCompleted = false;
            uiReadyCompleted = false;
        }
    }

    private static long nanosToMillis(long nanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(nanos));
    }

    private static void logPhase(String phase, long elapsedMs, long totalMs) {
        log.atDebug()
                .addKeyValue("event", "startup")
                .addKeyValue("phase", phase)
                .addKeyValue("elapsedMs", elapsedMs)
                .addKeyValue("totalMs", totalMs)
                .log("起動フェーズ完了: phase={} elapsedMs={} totalMs={}", phase, elapsedMs, totalMs);
    }

    private static void logMilestone(String milestone, long jvmMs, long launcherMs) {
        log.atDebug()
                .addKeyValue("event", "startup")
                .addKeyValue("milestone", milestone)
                .addKeyValue("jvmMs", jvmMs)
                .addKeyValue("launcherMs", launcherMs)
                .log("起動マイルストーン: milestone={} jvmMs={} launcherMs={}", milestone, jvmMs, launcherMs);
    }
}
