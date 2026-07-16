package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.compression.zstandard.ZstandardCompression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import logbook.internal.JsonMappers;

/**
 * {@link ApiCaptureWriteService} / {@link ApiCaptureEnvelope} /
 * {@link ApiCaptureFlushPolicy} / {@link ApiCaptureSegmentStore} のテスト。
 */
class ApiCaptureWriterTest {

    @TempDir
    Path tempDir;

    private ApiCaptureWriteService service;

    @AfterEach
    void tearDown() {
        if (this.service != null) {
            this.service.shutdown();
            this.service = null;
        }
    }

    @Test
    void rejectsEnqueueAfterShutdown() {
        this.service = newService(tempDir, () -> true);
        this.service.shutdown();
        assertFalse(this.service.enqueue(sampleRecord("req-late")));
    }

    @Test
    void shutdownIsIdempotent() {
        this.service = newService(tempDir, () -> true);
        this.service.shutdown();
        this.service.shutdown();
        assertFalse(this.service.enqueue(sampleRecord("req-after-double-shutdown")));
    }

    @Test
    void flushKeepsAccepting() {
        this.service = newService(tempDir, () -> true);
        assertTrue(this.service.enqueue(sampleRecord("req-before-flush")));
        this.service.flush();
        assertTrue(this.service.enqueue(sampleRecord("req-after-flush")));
        this.service.flush();
    }

    @Test
    void writesJsonlZstSegment() throws Exception {
        Path captureDir = tempDir.resolve("captures");
        this.service = newService(captureDir, () -> true);

        String responseBody = "svdata={\"api_result\":1,\"api_data\":{\"foo\":\"bar\"}}";
        ApiCaptureRecord record = new ApiCaptureRecord(
                "req-123",
                "POST",
                "/kcsapi/api_port/port",
                "api_token=abc",
                responseBody);

        assertTrue(this.service.enqueue(record));
        this.service.flush();

        JsonObject envelope = readFirstEnvelope(findSegment(captureDir));
        assertEquals(1, envelope.getInt("v"));
        assertEquals("req-123", envelope.getString("requestId"));
        assertEquals("POST", envelope.getString("method"));
        assertEquals("/kcsapi/api_port/port", envelope.getString("uriPath"));
        assertFalse(envelope.containsKey("uri"));
        assertEquals("api_token=abc", envelope.getString("request"));
        assertEquals(responseBody, envelope.getString("response"));
        assertTrue(envelope.containsKey("capturedAt"));
    }

    @Test
    void omitsRequestFieldWhenRequestBodyIsNull() throws Exception {
        Path captureDir = tempDir.resolve("captures");
        this.service = newService(captureDir, () -> true);

        assertTrue(this.service.enqueue(new ApiCaptureRecord(
                "req-no-body",
                "POST",
                "/kcsapi/api_port/port",
                null,
                "svdata={}")));
        this.service.flush();

        JsonObject envelope = readFirstEnvelope(findSegment(captureDir));
        assertFalse(envelope.containsKey("request"));
        assertEquals("svdata={}", envelope.getString("response"));
    }

    @Test
    void discardsWhenCaptureInactive() throws Exception {
        Path captureDir = tempDir.resolve("captures");
        this.service = newService(captureDir, () -> false);

        assertTrue(this.service.enqueue(sampleRecord("req-inactive")));
        this.service.flush();

        assertFalse(Files.exists(captureDir.resolve("segments")));
    }

    @Test
    void envelopeOmitsNullRequest() {
        ApiCaptureEnvelope envelope = ApiCaptureEnvelope.from(
                new ApiCaptureRecord("id", "POST", "/kcsapi/x", null, "svdata={}"),
                Instant.parse("2026-07-12T00:00:00Z"));
        String json = JsonMappers.MAPPER.writeValueAsString(envelope);
        assertFalse(json.contains("\"request\""));
        assertTrue(json.contains("\"response\":\"svdata={}\""));
        assertTrue(json.contains("\"capturedAt\":\"2026-07-12T00:00:00Z\""));
    }

    @Test
    void flushPolicyTriggersByRecordCount() {
        ApiCaptureFlushPolicy policy = new ApiCaptureFlushPolicy(2, 60_000L, 5, 60_000L);
        long t0 = 0L;
        assertFalse(policy.shouldFlushEncoder(1, t0, t0));
        assertTrue(policy.shouldFlushEncoder(2, t0, t0));
        assertFalse(policy.shouldCloseSegment(4, t0, t0));
        assertTrue(policy.shouldCloseSegment(5, t0, t0));
    }

    @Test
    void flushPolicyTriggersByElapsedTime() {
        ApiCaptureFlushPolicy policy = new ApiCaptureFlushPolicy(100, 1_000L, 100, 2_000L);
        long t0 = 0L;
        long after1s = 1_000_000_000L;
        long after2s = 2_000_000_000L;
        assertTrue(policy.shouldFlushEncoderByTime(t0, after1s));
        assertFalse(policy.shouldCloseSegmentByTime(t0, after1s));
        assertTrue(policy.shouldCloseSegmentByTime(t0, after2s));
    }

    private ApiCaptureWriteService newService(Path captureDir, java.util.function.Supplier<Boolean> active) {
        return new ApiCaptureWriteService(
                active,
                () -> captureDir,
                ApiCaptureFlushPolicy.DEFAULT,
                new AtomicLong()::get);
    }

    private static ApiCaptureRecord sampleRecord(String requestId) {
        return new ApiCaptureRecord(
                requestId,
                "POST",
                "/kcsapi/api_port/port",
                null,
                "svdata={}");
    }

    private static Path findSegment(Path captureDir) throws Exception {
        Path segmentsDir = captureDir.resolve("segments");
        assertTrue(Files.isDirectory(segmentsDir));
        try (var files = Files.list(segmentsDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jsonl.zst"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static JsonObject readFirstEnvelope(Path zstFile) throws Exception {
        ZstandardCompression zstandard = new ZstandardCompression();
        zstandard.start();
        byte[] compressed = Files.readAllBytes(zstFile);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        try (InputStream in = new ByteArrayInputStream(compressed);
                InputStream decoded = zstandard.newDecoderInputStream(in, zstandard.getDefaultDecoderConfig())) {
            decoded.transferTo(plain);
        }
        String text = plain.toString(StandardCharsets.UTF_8);
        String firstLine = text.substring(0, text.indexOf('\n'));
        try (JsonReader reader = Json.createReader(new StringReader(firstLine))) {
            return reader.readObject();
        }
    }
}
