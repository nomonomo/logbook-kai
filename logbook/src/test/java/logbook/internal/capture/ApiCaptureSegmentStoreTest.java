package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ApiCaptureSegmentStore} のセグメント命名・回転のテスト。
 */
class ApiCaptureSegmentStoreTest {

    @TempDir
    Path tempDir;

    private ApiCaptureSegmentStore store;

    @AfterEach
    void tearDown() {
        if (this.store != null) {
            this.store.closeQuietly();
            this.store = null;
        }
    }

    @Test
    void partFileNameUsesBaseForPartOne() {
        assertEquals("2026-07-12.jsonl.zst", ApiCaptureSegmentStore.partFileName("2026-07-12", 1));
        assertEquals("2026-07-12.part2.jsonl.zst", ApiCaptureSegmentStore.partFileName("2026-07-12", 2));
    }

    @Test
    void findLatestPartReturnsOneWhenMissing() throws Exception {
        Path segments = tempDir.resolve("segments");
        Files.createDirectories(segments);
        assertEquals(1, ApiCaptureSegmentStore.findLatestPart(segments, "2026-07-12"));
    }

    @Test
    void findLatestPartFollowsExistingParts() throws Exception {
        Path segments = tempDir.resolve("segments");
        Files.createDirectories(segments);
        Files.writeString(segments.resolve("2026-07-12.jsonl.zst"), "x");
        Files.writeString(segments.resolve("2026-07-12.part2.jsonl.zst"), "x");
        Files.writeString(segments.resolve("2026-07-12.part3.jsonl.zst"), "x");
        assertEquals(3, ApiCaptureSegmentStore.findLatestPart(segments, "2026-07-12"));
    }

    @Test
    void appendCreatesDailySegment() throws Exception {
        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 7, 12));
        this.store = new ApiCaptureSegmentStore(today::get);

        assertTrue(this.store.append(tempDir, sample("req-1")));
        assertTrue(this.store.isOpen());
        assertEquals(
                tempDir.resolve("segments").resolve("2026-07-12.jsonl.zst"),
                this.store.currentSegmentPath());
        assertFalse(this.store.append(tempDir, sample("req-2")));

        this.store.closeQuietly();
        assertFalse(this.store.isOpen());
        assertTrue(Files.exists(tempDir.resolve("segments").resolve("2026-07-12.jsonl.zst")));
    }

    @Test
    void appendRotatesOnDateChange() throws Exception {
        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 7, 12));
        this.store = new ApiCaptureSegmentStore(today::get);

        assertTrue(this.store.append(tempDir, sample("day1")));
        this.store.closeQuietly();

        today.set(LocalDate.of(2026, 7, 13));
        assertTrue(this.store.append(tempDir, sample("day2")));
        assertEquals(
                tempDir.resolve("segments").resolve("2026-07-13.jsonl.zst"),
                this.store.currentSegmentPath());
        this.store.closeQuietly();

        assertTrue(Files.exists(tempDir.resolve("segments").resolve("2026-07-12.jsonl.zst")));
        assertTrue(Files.exists(tempDir.resolve("segments").resolve("2026-07-13.jsonl.zst")));
    }

    @Test
    void appendRotatesOnNextWriteAfterRecordLimit() throws Exception {
        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 7, 12));
        // 上限 2 件。2 件目まで part1、3 件目から part2
        this.store = new ApiCaptureSegmentStore(today::get, 2);

        assertTrue(this.store.append(tempDir, sample("req-a")));
        assertFalse(this.store.append(tempDir, sample("req-b")));
        Path first = this.store.currentSegmentPath();
        assertEquals(tempDir.resolve("segments").resolve("2026-07-12.jsonl.zst"), first);

        assertTrue(this.store.append(tempDir, sample("req-c")));
        Path second = this.store.currentSegmentPath();
        assertEquals(tempDir.resolve("segments").resolve("2026-07-12.part2.jsonl.zst"), second);
        this.store.closeQuietly();
    }

    @Test
    void partRecordCountSurvivesCloseAndReopen() throws Exception {
        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 7, 12));
        this.store = new ApiCaptureSegmentStore(today::get, 3);

        assertTrue(this.store.append(tempDir, sample("req-1")));
        assertFalse(this.store.append(tempDir, sample("req-2")));
        this.store.closeQuietly();

        // close 後も同一 part の件数が残るため、あと 1 件で上限 → 次行で part2
        assertTrue(this.store.append(tempDir, sample("req-3")));
        assertEquals(
                tempDir.resolve("segments").resolve("2026-07-12.jsonl.zst"),
                this.store.currentSegmentPath());
        assertTrue(this.store.append(tempDir, sample("req-4")));
        assertEquals(
                tempDir.resolve("segments").resolve("2026-07-12.part2.jsonl.zst"),
                this.store.currentSegmentPath());
        this.store.closeQuietly();
    }

    @Test
    void resumesLatestExistingPart() throws Exception {
        Path segments = tempDir.resolve("segments");
        Files.createDirectories(segments);
        Files.writeString(segments.resolve("2026-07-12.jsonl.zst"), "old");
        Files.writeString(segments.resolve("2026-07-12.part2.jsonl.zst"), "old2");

        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 7, 12));
        this.store = new ApiCaptureSegmentStore(today::get);
        assertTrue(this.store.append(tempDir, sample("resume")));
        assertEquals(
                segments.resolve("2026-07-12.part2.jsonl.zst"),
                this.store.currentSegmentPath());
        this.store.closeQuietly();
    }

    private static ApiCaptureRecord sample(String requestId) {
        return new ApiCaptureRecord(
                requestId,
                "POST",
                "/kcsapi/api_port/port",
                null,
                "svdata={}");
    }
}
