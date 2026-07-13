package logbook.internal.capture;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import logbook.bean.AppConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * API キャプチャの非同期書き込み本体。
 * <p>
 * キュー・worker・ライフサイクルと flush ポリシー適用を担当する。
 * セグメント I/O は {@link ApiCaptureSegmentStore} に委譲する。
 * テストではインスタンスを直接生成する。本番は {@link ApiCaptureWriter} が保持する。
 * </p>
 */
@Slf4j
final class ApiCaptureWriteService {

    /** worker のキュー待ちタイムアウト（ミリ秒） */
    private static final long POLL_TIMEOUT_MS = 500L;

    /** 命令完了待ちタイムアウト（ミリ秒） */
    private static final long COMMAND_TIMEOUT_MS = 5_000L;

    private final Supplier<Boolean> captureActive;
    private final Supplier<Path> captureDir;
    private final ApiCaptureFlushPolicy flushPolicy;
    private final LongSupplier nanoTime;
    private final ApiCaptureSegmentStore segmentStore;

    private final Object lifecycleLock = new Object();
    private final LinkedBlockingQueue<WriterCommand> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    private int recordsSinceStreamFlush;
    private int recordsSinceSegmentClose;
    private long lastStreamFlushNanos;
    private long lastSegmentCloseNanos;

    ApiCaptureWriteService(
            Supplier<Boolean> captureActive,
            Supplier<Path> captureDir,
            ApiCaptureFlushPolicy flushPolicy,
            LongSupplier nanoTime) {
        this(captureActive, captureDir, flushPolicy, nanoTime, new ApiCaptureSegmentStore());
    }

    ApiCaptureWriteService(
            Supplier<Boolean> captureActive,
            Supplier<Path> captureDir,
            ApiCaptureFlushPolicy flushPolicy,
            LongSupplier nanoTime,
            ApiCaptureSegmentStore segmentStore) {
        this.captureActive = Objects.requireNonNull(captureActive);
        this.captureDir = Objects.requireNonNull(captureDir);
        this.flushPolicy = Objects.requireNonNull(flushPolicy);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.segmentStore = Objects.requireNonNull(segmentStore);
        startWorker();
    }

    /**
     * 本番用インスタンスを生成する。
     */
    static ApiCaptureWriteService createDefault() {
        return new ApiCaptureWriteService(
                ApiCaptureGate::isCaptureActive,
                () -> Path.of(AppConfig.get().getApiCaptureDir()),
                ApiCaptureFlushPolicy.DEFAULT,
                System::nanoTime);
    }

    private void startWorker() {
        this.worker = Thread.ofVirtual().name("api-capture-writer").start(this::runLoop);
    }

    boolean enqueue(ApiCaptureRecord record) {
        Objects.requireNonNull(record);
        synchronized (this.lifecycleLock) {
            if (!this.accepting.get()) {
                log.atDebug()
                        .setMessage(() -> "APIキャプチャの受付停止中のためレコードを破棄しました: uriPath="
                                + record.uriPath())
                        .log();
                return false;
            }
            ensureWorkerStartedLocked();
            this.queue.offer(new RecordCommand(record));
            return true;
        }
    }

    void flush() {
        CountDownLatch done = new CountDownLatch(1);
        synchronized (this.lifecycleLock) {
            if (!this.accepting.get()) {
                return;
            }
            ensureWorkerStartedLocked();
            this.queue.offer(new FlushCommand(done));
        }
        awaitCommand(done, "flush");
    }

    void shutdown() {
        CountDownLatch done = new CountDownLatch(1);
        synchronized (this.lifecycleLock) {
            if (!this.accepting.compareAndSet(true, false)) {
                return;
            }
            ensureWorkerStartedLocked();
            this.queue.offer(new ShutdownCommand(done));
        }
        awaitCommand(done, "shutdown");
    }

    private static void awaitCommand(CountDownLatch done, String commandName) {
        try {
            if (!done.await(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("APIキャプチャの {} がタイムアウトしました", commandName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureWorkerStartedLocked() {
        if (!this.running.get() && (this.worker == null || !this.worker.isAlive())) {
            startWorker();
        }
    }

    private void runLoop() {
        this.running.set(true);
        try {
            while (true) {
                WriterCommand command = this.queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (command != null) {
                    if (processCommand(command)) {
                        break;
                    }
                    continue;
                }
                maybePeriodicMaintenance();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("APIキャプチャライタースレッドで例外が発生しました", e);
        } finally {
            releasePendingCommands();
            this.segmentStore.closeQuietly();
            this.running.set(false);
        }
    }

    private boolean processCommand(WriterCommand command) {
        return switch (command) {
            case RecordCommand(var record) -> {
                writeRecord(record);
                yield false;
            }
            case FlushCommand(var done) -> {
                completeFlush(done);
                yield false;
            }
            case ShutdownCommand(var done) -> {
                completeShutdown(done);
                yield true;
            }
        };
    }

    private void completeFlush(CountDownLatch done) {
        try {
            this.segmentStore.closeQuietly();
            resetFlushCounters();
        } finally {
            done.countDown();
        }
    }

    private void completeShutdown(CountDownLatch done) {
        try {
            this.segmentStore.closeQuietly();
            resetFlushCounters();
        } finally {
            done.countDown();
        }
    }

    /**
     * worker 異常終了時にキューへ残った命令を処理する。
     * <p>
     * 正常終了は {@link ShutdownCommand} でセグメントを閉じてからループを抜けるため、
     * ここに到達するのは例外等による異常終了時のみである。
     * </p>
     * <ul>
     * <li>{@link FlushCommand} / {@link ShutdownCommand}: {@link CountDownLatch#countDown()} のみ。
     *     呼び出し側の待ちを解放する（セグメント close は直後の {@code finally}）。</li>
     * <li>{@link RecordCommand}: 未書き込みのため破棄する。</li>
     * </ul>
     */
    private void releasePendingCommands() {
        int releasedWaiters = 0;
        int droppedRecords = 0;
        WriterCommand pending;
        while ((pending = this.queue.poll()) != null) {
            switch (pending) {
                case FlushCommand(var done) -> {
                    done.countDown();
                    releasedWaiters++;
                }
                case ShutdownCommand(var done) -> {
                    done.countDown();
                    releasedWaiters++;
                }
                case RecordCommand ignored -> droppedRecords++;
            }
        }
        if (releasedWaiters > 0 || droppedRecords > 0) {
            log.warn(
                    "APIキャプチャ worker 異常終了: キュー残りを処理しました"
                            + " (待ち解放={}, 未書き込みレコード破棄={})",
                    releasedWaiters,
                    droppedRecords);
        }
    }

    private void writeRecord(ApiCaptureRecord record) {
        if (!Boolean.TRUE.equals(this.captureActive.get())) {
            return;
        }
        try {
            if (this.segmentStore.append(this.captureDir.get(), record)) {
                markSegmentOpened();
            }
            onRecordWritten();
        } catch (Exception e) {
            log.warn("APIキャプチャの保存に失敗しました: uriPath={}", record.uriPath(), e);
            this.segmentStore.closeQuietly();
            resetFlushCounters();
        }
    }

    private void onRecordWritten() {
        this.recordsSinceStreamFlush++;
        this.recordsSinceSegmentClose++;
        long now = this.nanoTime.getAsLong();
        if (this.flushPolicy.shouldFlushEncoder(
                this.recordsSinceStreamFlush, this.lastStreamFlushNanos, now)) {
            this.segmentStore.flushEncoderQuietly();
            this.recordsSinceStreamFlush = 0;
            this.lastStreamFlushNanos = this.nanoTime.getAsLong();
        }
        if (this.flushPolicy.shouldCloseSegment(
                this.recordsSinceSegmentClose, this.lastSegmentCloseNanos, now)) {
            this.segmentStore.closeQuietly();
            resetFlushCounters();
            this.lastSegmentCloseNanos = this.nanoTime.getAsLong();
        }
    }

    private void maybePeriodicMaintenance() {
        if (!this.segmentStore.isOpen()) {
            return;
        }
        long now = this.nanoTime.getAsLong();
        if (this.flushPolicy.shouldFlushEncoderByTime(this.lastStreamFlushNanos, now)) {
            this.segmentStore.flushEncoderQuietly();
            this.recordsSinceStreamFlush = 0;
            this.lastStreamFlushNanos = this.nanoTime.getAsLong();
        }
        if (this.flushPolicy.shouldCloseSegmentByTime(this.lastSegmentCloseNanos, now)) {
            this.segmentStore.closeQuietly();
            resetFlushCounters();
            this.lastSegmentCloseNanos = this.nanoTime.getAsLong();
        }
    }

    private void markSegmentOpened() {
        long now = this.nanoTime.getAsLong();
        this.lastStreamFlushNanos = now;
        this.lastSegmentCloseNanos = now;
        this.recordsSinceStreamFlush = 0;
        this.recordsSinceSegmentClose = 0;
    }

    private void resetFlushCounters() {
        this.recordsSinceStreamFlush = 0;
        this.recordsSinceSegmentClose = 0;
    }

    private sealed interface WriterCommand permits RecordCommand, FlushCommand, ShutdownCommand {
    }

    private record RecordCommand(ApiCaptureRecord record) implements WriterCommand {
    }

    private record FlushCommand(CountDownLatch done) implements WriterCommand {
    }

    private record ShutdownCommand(CountDownLatch done) implements WriterCommand {
    }
}
