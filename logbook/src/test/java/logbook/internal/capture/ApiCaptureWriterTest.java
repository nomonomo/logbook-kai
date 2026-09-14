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
                "/kcsapi/api_port/port?api_verno=1",
                "text/plain",
                "api_token=abc",
                ApiCaptureBodies.ENCODING_UTF8,
                responseBody,
                ApiCaptureBodies.ENCODING_UTF8);

        assertTrue(this.service.enqueue(record));
        this.service.flush();

        JsonObject envelope = readFirstEnvelope(findSegment(captureDir));
        assertEquals(3, envelope.getInt("v"));
        assertEquals("req-123", envelope.getString("requestId"));
        assertEquals("POST", envelope.getString("method"));
        assertEquals("/kcsapi/api_port/port", envelope.getString("uriPath"));
        assertEquals("/kcsapi/api_port/port?api_verno=1", envelope.getString("uri"));
        assertEquals("text/plain", envelope.getString("contentType"));
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, envelope.getString("requestEncoding"));
        assertEquals("api_token=abc", envelope.getString("request"));
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, envelope.getString("responseEncoding"));
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
                "/kcsapi/api_port/port",
                "text/plain",
                null,
                null,
                "svdata={}",
                ApiCaptureBodies.ENCODING_UTF8)));
        this.service.flush();

        JsonObject envelope = readFirstEnvelope(findSegment(captureDir));
        assertFalse(envelope.containsKey("request"));
        assertFalse(envelope.containsKey("requestEncoding"));
        assertEquals("svdata={}", envelope.getString("response"));
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, envelope.getString("responseEncoding"));
    }

    @Test
    void writesBase64ResponseForBinaryBody() throws Exception {
        Path captureDir = tempDir.resolve("captures");
        this.service = newService(captureDir, () -> true);

        String base64 = java.util.Base64.getEncoder().encodeToString(new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 });
        assertTrue(this.service.enqueue(new ApiCaptureRecord(
                "req-bin",
                "GET",
                "/kcs2/resources/map/007/02_image.png",
                "/kcs2/resources/map/007/02_image.png",
                "image/png",
                null,
                null,
                base64,
                ApiCaptureBodies.ENCODING_BASE64)));
        this.service.flush();

        JsonObject envelope = readFirstEnvelope(findSegment(captureDir));
        assertEquals(3, envelope.getInt("v"));
        assertEquals("image/png", envelope.getString("contentType"));
        assertEquals(ApiCaptureBodies.ENCODING_BASE64, envelope.getString("responseEncoding"));
        assertEquals(base64, envelope.getString("response"));
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
                new ApiCaptureRecord(
                        "id",
                        "POST",
                        "/kcsapi/x",
                        "/kcsapi/x",
                        null,
                        null,
                        null,
                        "svdata={}",
                        ApiCaptureBodies.ENCODING_UTF8),
                Instant.parse("2026-07-12T00:00:00Z"));
        String json = JsonMappers.MAPPER.writeValueAsString(envelope);
        assertFalse(json.contains("\"request\""));
        assertFalse(json.contains("\"requestEncoding\""));
        assertTrue(json.contains("\"response\":\"svdata={}\""));
        assertTrue(json.contains("\"responseEncoding\":\"utf8\""));
        assertTrue(json.contains("\"capturedAt\":\"2026-07-12T00:00:00Z\""));
        assertTrue(json.contains("\"v\":3"));
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
                "/kcsapi/api_port/port",
                "text/plain",
                null,
                null,
                "svdata={}",
                ApiCaptureBodies.ENCODING_UTF8);
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
