package logbook.internal.capture;

import java.util.concurrent.TimeUnit;

/**
 * エンコーダ flush / セグメント close の件数・時間判定。
 * <p>
 * 開発用キャプチャのため 100% 即時永続化は狙わない。
 * 既定は encode flush: 100 件または 30 秒、segment close: 1000 件または 10 分。
 * </p>
 */
final class ApiCaptureFlushPolicy {

    static final ApiCaptureFlushPolicy DEFAULT = new ApiCaptureFlushPolicy(
            100,
            30_000L,
            1_000,
            600_000L);

    private final int streamFlushEveryRecords;
    private final long streamFlushIntervalMs;
    private final int segmentCloseEveryRecords;
    private final long segmentCloseIntervalMs;

    ApiCaptureFlushPolicy(
            int streamFlushEveryRecords,
            long streamFlushIntervalMs,
            int segmentCloseEveryRecords,
            long segmentCloseIntervalMs) {
        this.streamFlushEveryRecords = streamFlushEveryRecords;
        this.streamFlushIntervalMs = streamFlushIntervalMs;
        this.segmentCloseEveryRecords = segmentCloseEveryRecords;
        this.segmentCloseIntervalMs = segmentCloseIntervalMs;
    }

    boolean shouldFlushEncoder(int recordsSinceFlush, long lastFlushNanos, long nowNanos) {
        return recordsSinceFlush >= this.streamFlushEveryRecords
                || intervalElapsed(lastFlushNanos, nowNanos, this.streamFlushIntervalMs);
    }

    boolean shouldCloseSegment(int recordsSinceClose, long lastCloseNanos, long nowNanos) {
        return recordsSinceClose >= this.segmentCloseEveryRecords
                || intervalElapsed(lastCloseNanos, nowNanos, this.segmentCloseIntervalMs);
    }

    boolean shouldFlushEncoderByTime(long lastFlushNanos, long nowNanos) {
        return intervalElapsed(lastFlushNanos, nowNanos, this.streamFlushIntervalMs);
    }

    boolean shouldCloseSegmentByTime(long lastCloseNanos, long nowNanos) {
        return intervalElapsed(lastCloseNanos, nowNanos, this.segmentCloseIntervalMs);
    }

    private static boolean intervalElapsed(long sinceNanos, long nowNanos, long intervalMs) {
        return nowNanos - sinceNanos >= TimeUnit.MILLISECONDS.toNanos(intervalMs);
    }
}
