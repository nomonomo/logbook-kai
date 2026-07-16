package logbook.internal.gamedata;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ゲームデータ更新の単体／結合テスト（ネットワークなし）。
 * <p>
 * {@link GameDataUpdateRunner} のパス検証はヘルパー単体で確認し、
 * {@code run()} では版比較・差分 DL・失敗時の非 promote・reload 通知などフローを見る。
 * {@link CheckGameDataUpdate} ファサードは Mockito で有効／無効・例外・定期開始を見る。
 * </p>
 */
class CheckGameDataUpdateTest {

    private static final String BASE_URL = "https://example.test/data";

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // GameDataUpdateRunner: 抽出ヘルパー
    // -------------------------------------------------------------------------

    @Nested
    class PathHelpers {

        /** {@link GameDataUpdateRunner#isUnsafeRelativePath} */
        @Test
        void rejectsNullBlankAndDotDot() {
            assertTrue(GameDataUpdateRunner.isUnsafeRelativePath(null));
            assertTrue(GameDataUpdateRunner.isUnsafeRelativePath(""));
            assertTrue(GameDataUpdateRunner.isUnsafeRelativePath("  "));
            assertTrue(GameDataUpdateRunner.isUnsafeRelativePath("../evil.json"));
            assertTrue(GameDataUpdateRunner.isUnsafeRelativePath("map/../evil.json"));
            assertFalse(GameDataUpdateRunner.isUnsafeRelativePath(GameDataPaths.MAPPING));
            assertFalse(GameDataUpdateRunner.isUnsafeRelativePath("quest/quests.json"));
        }

        /** {@link GameDataUpdateRunner#resolveUnderParent} */
        @Test
        void resolveUnderParentKeepsInsideAndRejectsEscape() throws Exception {
            Path staging = tempDir.resolve("staging");
            Files.createDirectories(staging);

            Path ok = GameDataUpdateRunner.resolveUnderParent(staging, GameDataPaths.MAPPING);
            assertTrue(ok.startsWith(staging));

            IOException relativeEscape = assertThrows(IOException.class,
                    () -> GameDataUpdateRunner.resolveUnderParent(staging, "../evil.json"));
            assertTrue(relativeEscape.getMessage().contains("パストラバーサル"));

            String absoluteKey = tempDir.resolve("evil-outside.json")
                    .toAbsolutePath().toString().replace('\\', '/');
            IOException absoluteEscape = assertThrows(IOException.class,
                    () -> GameDataUpdateRunner.resolveUnderParent(staging, absoluteKey));
            assertTrue(absoluteEscape.getMessage().contains("パストラバーサル"));
        }

        /** CRLF / LF が混在しても SHA は同じ（配信は LF 前提） */
        @Test
        void sha256IgnoresCrLfDifference() throws Exception {
            byte[] lf = "{\"a\":1}\n".getBytes(StandardCharsets.UTF_8);
            byte[] crlf = "{\"a\":1}\r\n".getBytes(StandardCharsets.UTF_8);
            assertEquals(GameDataUpdateRunner.sha256Hex(lf), GameDataUpdateRunner.sha256Hex(crlf));

            Path crlfFile = tempDir.resolve("crlf.json");
            Files.write(crlfFile, crlf);
            assertEquals(GameDataUpdateRunner.sha256Hex(lf), GameDataUpdateRunner.sha256Hex(crlfFile));
        }
    }

    // -------------------------------------------------------------------------
    // GameDataUpdateRunner: run() フロー
    // -------------------------------------------------------------------------

    @Nested
    class RunnerFlow {

        /** 現行版 ≥ リモート版 → manifest のみ取得、promote / reload なし */
        @Test
        void skipDownloadWhenAlreadyLatest() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Files.createDirectories(root);
            MapFetcher fetcher = new MapFetcher();
            fetcher.put(BASE_URL + "/manifest.json", manifestJson(1L, Map.of()));

            AtomicInteger mappingReloads = new AtomicInteger();
            GameDataUpdateRunner runner = newRunner(
                    fetcher, root, () -> 1L, mappingReloads, new AtomicInteger(), new AtomicInteger());

            runner.run();

            assertEquals(1, fetcher.callCount(BASE_URL + "/manifest.json"));
            assertFalse(Files.exists(root.resolve("manifest.json")));
            assertEquals(0, mappingReloads.get());
        }

        /** 新版 → 差分 DL・promote・更新した種類だけ reload・staging 削除 */
        @Test
        void downloadAndPromoteWhenNewer() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Files.createDirectories(root);

            byte[] mappingBody = "{\"1-1\": \"A\"}".getBytes(StandardCharsets.UTF_8);
            String mappingSha = GameDataUpdateRunner.sha256Hex(mappingBody);

            MapFetcher fetcher = new MapFetcher();
            fetcher.put(BASE_URL + "/manifest.json",
                    manifestJson(2L, Map.of(GameDataPaths.MAPPING, mappingSha)));
            fetcher.put(BASE_URL + "/" + GameDataPaths.MAPPING, mappingBody);

            AtomicInteger mappingReloads = new AtomicInteger();
            AtomicInteger seaReloads = new AtomicInteger();
            AtomicInteger questReloads = new AtomicInteger();
            GameDataUpdateRunner runner = newRunner(
                    fetcher, root, () -> 0L, mappingReloads, seaReloads, questReloads);

            runner.run();

            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MAPPING)));
            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MANIFEST)));
            assertEquals("{\"1-1\": \"A\"}", Files.readString(root.resolve(GameDataPaths.MAPPING)));
            assertEquals(1, mappingReloads.get());
            assertEquals(0, seaReloads.get());
            assertEquals(0, questReloads.get());
            assertFalse(Files.exists(root.resolve(".staging")));
        }

        /** 版は新しいがローカル sha 一致 → 当該ファイルは再 DL せず manifest だけ更新 */
        @Test
        void skipUnchangedFileBySha() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Path mapping = root.resolve(GameDataPaths.MAPPING);
            Files.createDirectories(mapping.getParent());
            byte[] mappingBody = "{\"keep\": \"1\"}".getBytes(StandardCharsets.UTF_8);
            Files.write(mapping, mappingBody);
            String sha = GameDataUpdateRunner.sha256Hex(mappingBody);

            MapFetcher fetcher = new MapFetcher();
            fetcher.put(BASE_URL + "/manifest.json",
                    manifestJson(3L, Map.of(GameDataPaths.MAPPING, sha)));

            AtomicInteger mappingReloads = new AtomicInteger();
            GameDataUpdateRunner runner = newRunner(
                    fetcher, root, () -> 1L, mappingReloads, new AtomicInteger(), new AtomicInteger());

            runner.run();

            assertEquals(0, fetcher.callCount(BASE_URL + "/" + GameDataPaths.MAPPING));
            assertEquals(0, mappingReloads.get());
            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MANIFEST)));
            assertEquals("{\"keep\": \"1\"}", Files.readString(mapping));
        }

        /**
         * 危険パスはスキップしつつ、他エントリは更新する（ヘルパー配線の確認）。
         * パス判定そのものは {@link PathHelpers} 側。
         */
        @Test
        void skipsUnsafePathAndUpdatesOtherFiles() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Files.createDirectories(root);

            byte[] okBody = "{}".getBytes(StandardCharsets.UTF_8);
            String okSha = GameDataUpdateRunner.sha256Hex(okBody);
            String evilUrl = GameDataUpdateRunner.joinUrl(BASE_URL, "../evil.json");

            MapFetcher fetcher = new MapFetcher();
            Map<String, String> files = new LinkedHashMap<>();
            files.put("../evil.json", "deadbeef");
            files.put(GameDataPaths.MAPPING, okSha);
            fetcher.put(BASE_URL + "/manifest.json", manifestJson(2L, files));
            fetcher.put(BASE_URL + "/" + GameDataPaths.MAPPING, okBody);
            fetcher.put(evilUrl, "{\"evil\":true}".getBytes(StandardCharsets.UTF_8));

            newRunner(fetcher, root, () -> 0L,
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger()).run();

            assertEquals(0, fetcher.callCount(evilUrl));
            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MAPPING)));
            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MANIFEST)));
        }

        /** SHA 不一致 → 失敗し、既存の配信ファイルを本番へ反映しない */
        @Test
        void abortOnShaMismatchWithoutPromote() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Path mapping = root.resolve(GameDataPaths.MAPPING);
            Files.createDirectories(mapping.getParent());
            Files.writeString(mapping, "{\"kept\":true}");
            Files.writeString(root.resolve(GameDataPaths.MANIFEST), "{\"version\":1}");

            MapFetcher fetcher = new MapFetcher();
            fetcher.put(BASE_URL + "/manifest.json",
                    manifestJson(2L, Map.of(GameDataPaths.MAPPING, "0".repeat(64))));
            fetcher.put(BASE_URL + "/" + GameDataPaths.MAPPING, "{\"x\":1}".getBytes(StandardCharsets.UTF_8));

            GameDataUpdateRunner runner = newRunner(
                    fetcher, root, () -> 0L,
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger());

            IOException ex = assertThrows(IOException.class, runner::run);
            assertTrue(ex.getMessage().contains("SHA-256"));
            assertEquals("{\"kept\":true}", Files.readString(mapping));
            assertEquals("{\"version\":1}", Files.readString(root.resolve(GameDataPaths.MANIFEST)));
        }

        /** 不正 JSON → 失敗し、既存の配信ファイルを本番へ反映しない */
        @Test
        void abortOnInvalidJsonWithoutPromote() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Path mapping = root.resolve(GameDataPaths.MAPPING);
            Files.createDirectories(mapping.getParent());
            Files.writeString(mapping, "{\"kept\":true}");
            Files.writeString(root.resolve(GameDataPaths.MANIFEST), "{\"version\":1}");

            byte[] invalid = "not-json".getBytes(StandardCharsets.UTF_8);
            String sha = GameDataUpdateRunner.sha256Hex(invalid);

            MapFetcher fetcher = new MapFetcher();
            fetcher.put(BASE_URL + "/manifest.json",
                    manifestJson(2L, Map.of(GameDataPaths.MAPPING, sha)));
            fetcher.put(BASE_URL + "/" + GameDataPaths.MAPPING, invalid);

            GameDataUpdateRunner runner = newRunner(
                    fetcher, root, () -> 0L,
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger());

            IOException ex = assertThrows(IOException.class, runner::run);
            assertTrue(ex.getMessage().contains("JSON"));
            assertEquals("{\"kept\":true}", Files.readString(mapping));
            assertEquals("{\"version\":1}", Files.readString(root.resolve(GameDataPaths.MANIFEST)));
        }

        /**
         * staging 外へ出るパスも warn + skip し、他エントリは更新する。
         * （{@code ..} 付きと同様。パス不正で更新全体は止めない）
         */
        @Test
        void skipsEscapingPathAndUpdatesOtherFiles() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Files.createDirectories(root);

            byte[] okBody = "{}".getBytes(StandardCharsets.UTF_8);
            String okSha = GameDataUpdateRunner.sha256Hex(okBody);
            String absoluteKey = tempDir.resolve("evil-outside.json")
                    .toAbsolutePath().toString().replace('\\', '/');
            byte[] evilBody = "{\"evil\":true}".getBytes(StandardCharsets.UTF_8);
            String evilUrl = GameDataUpdateRunner.joinUrl(BASE_URL, absoluteKey);

            MapFetcher fetcher = new MapFetcher();
            Map<String, String> files = new LinkedHashMap<>();
            files.put(absoluteKey, GameDataUpdateRunner.sha256Hex(evilBody));
            files.put(GameDataPaths.MAPPING, okSha);
            fetcher.put(BASE_URL + "/manifest.json", manifestJson(2L, files));
            fetcher.put(BASE_URL + "/" + GameDataPaths.MAPPING, okBody);
            fetcher.put(evilUrl, evilBody);

            newRunner(fetcher, root, () -> 0L,
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger()).run();

            assertEquals(0, fetcher.callCount(evilUrl));
            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MAPPING)));
            assertTrue(Files.isRegularFile(root.resolve(GameDataPaths.MANIFEST)));
            assertFalse(Files.exists(tempDir.resolve("evil-outside.json")));
        }
    }

    // -------------------------------------------------------------------------
    // HttpsGameDataFetcher
    // -------------------------------------------------------------------------

    @Nested
    class HttpsFetcher {

        @Test
        void copyLimitedAbortsWhenOverMaxWhileReading() {
            byte[] data = new byte[5];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOException ex = assertThrows(IOException.class,
                    () -> HttpsGameDataFetcher.copyLimited(new ByteArrayInputStream(data), out, 4L));
            assertTrue(ex.getMessage().contains("大きすぎます"));
            assertTrue(out.size() <= 4);
        }

        @Test
        void rejectIfContentLengthExceeds() {
            IOException ex = assertThrows(IOException.class,
                    () -> HttpsGameDataFetcher.rejectIfContentLengthExceeds(OptionalLong.of(100), 50L));
            assertTrue(ex.getMessage().contains("Content-Length"));

            assertDoesNotThrow(() -> {
                HttpsGameDataFetcher.rejectIfContentLengthExceeds(OptionalLong.empty(), 50L);
                HttpsGameDataFetcher.rejectIfContentLengthExceeds(OptionalLong.of(-1), 50L);
                HttpsGameDataFetcher.rejectIfContentLengthExceeds(OptionalLong.of(50), 50L);
            });
        }

        @Test
        void rejectsNonHttps() {
            HttpsGameDataFetcher fetcher = new HttpsGameDataFetcher();
            Path dest = tempDir.resolve("out.bin");
            IOException ex = assertThrows(IOException.class,
                    () -> fetcher.downloadTo("http://example.test/manifest.json", 1024, dest));
            assertTrue(ex.getMessage().contains("HTTPS"));
            assertFalse(Files.exists(dest));
        }
    }

    // -------------------------------------------------------------------------
    // CheckGameDataUpdate: ファサード（有効判定・例外・定期開始）
    // -------------------------------------------------------------------------

    @Nested
    class Facade {

        private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

        /** 設定オフなら runner を呼ばない */
        @Test
        void disabledSkipsRunner() throws Exception {
            GameDataUpdateRunner runner = mock(GameDataUpdateRunner.class);
            CheckGameDataUpdate checker = new CheckGameDataUpdate(
                    runner, this.clock, () -> false, Runnable::run);

            checker.runAsyncIfEnabled();
            checker.runAsyncIfDue();

            verify(runner, never()).run();
        }

        /** runner 例外でも run は落ちず、running が解放されて再実行できる */
        @Test
        void runSwallowsRunnerExceptionAndAllowsRetry() throws Exception {
            GameDataUpdateRunner runner = mock(GameDataUpdateRunner.class);
            doThrow(new IOException("boom")).when(runner).run();

            CheckGameDataUpdate checker = new CheckGameDataUpdate(
                    runner, this.clock, () -> true, Runnable::run);

            assertDoesNotThrow(checker::run);
            assertDoesNotThrow(checker::run);
            verify(runner, times(2)).run();
        }

        /** startPeriodicCheck は多重起動しない */
        @Test
        void startPeriodicCheckSubmitsOnce() {
            GameDataUpdateRunner runner = mock(GameDataUpdateRunner.class);
            Executor executor = mock(Executor.class);
            CheckGameDataUpdate checker = new CheckGameDataUpdate(
                    runner, this.clock, () -> true, executor);

            checker.startPeriodicCheck();
            checker.startPeriodicCheck();

            verify(executor, times(1)).execute(any());
        }
    }

    // -------------------------------------------------------------------------
    // CheckGameDataUpdate: 間隔・排他
    // -------------------------------------------------------------------------

    @Nested
    class Scheduler {

        @Test
        void isDueRespectsInterval() {
            Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
            assertTrue(CheckGameDataUpdate.isDue(null, t0, CheckGameDataUpdate.PERIODIC_INTERVAL));
            assertFalse(CheckGameDataUpdate.isDue(
                    t0, t0.plus(Duration.ofHours(11)), CheckGameDataUpdate.PERIODIC_INTERVAL));
            assertTrue(CheckGameDataUpdate.isDue(
                    t0, t0.plus(Duration.ofHours(12)), CheckGameDataUpdate.PERIODIC_INTERVAL));
        }

        @Test
        void runAsyncIfDueSkipsUntilIntervalElapsed() throws Exception {
            Path root = tempDir.resolve("gamedata");
            Files.createDirectories(root);

            MapFetcher fetcher = new MapFetcher();
            fetcher.put(BASE_URL + "/manifest.json", manifestJson(1L, Map.of()));

            AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
            Clock clock = new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now.get();
                }
            };

            GameDataUpdateRunner runner = newRunner(
                    fetcher, root, () -> 1L,
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger());

            CheckGameDataUpdate checker = new CheckGameDataUpdate(
                    runner, clock, () -> true, Runnable::run);

            checker.runAsyncIfDue();
            assertEquals(1, fetcher.callCount(BASE_URL + "/manifest.json"));

            checker.runAsyncIfDue();
            assertEquals(1, fetcher.callCount(BASE_URL + "/manifest.json"));

            now.set(now.get().plus(Duration.ofHours(12)));
            checker.runAsyncIfDue();
            assertEquals(2, fetcher.callCount(BASE_URL + "/manifest.json"));
        }

        @Test
        void concurrentRunIsSkipped() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger fetcherEntries = new AtomicInteger();

            GameDataFetcher blockingFetcher = (url, maxBytes, dest) -> {
                fetcherEntries.incrementAndGet();
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("timeout");
                }
                Files.write(dest, manifestJson(1L, Map.of()));
            };

            GameDataUpdateRunner runner = newRunner(
                    blockingFetcher,
                    tempDir,
                    () -> 1L,
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicInteger());

            CheckGameDataUpdate checker = new CheckGameDataUpdate(
                    runner,
                    Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                    () -> true,
                    Runnable::run);

            Thread t = new Thread(checker::run);
            t.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            checker.run();
            assertEquals(1, fetcherEntries.get());

            release.countDown();
            t.join(5000);
            assertEquals(1, fetcherEntries.get());
        }
    }

    private static GameDataUpdateRunner newRunner(
            GameDataFetcher fetcher,
            Path root,
            LongSupplier current,
            AtomicInteger mapping,
            AtomicInteger sea,
            AtomicInteger quest) {
        return new GameDataUpdateRunner(
                fetcher,
                () -> root,
                current,
                () -> BASE_URL,
                mapping::incrementAndGet,
                sea::incrementAndGet,
                quest::incrementAndGet);
    }

    private static byte[] manifestJson(long version, Map<String, String> fileSha) {
        StringBuilder files = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : fileSha.entrySet()) {
            if (!first) {
                files.append(',');
            }
            first = false;
            files.append('"').append(e.getKey()).append("\":{\"sha256\":\"").append(e.getValue()).append("\"}");
        }
        String json = """
                {
                  "version": %d,
                  "files": {%s}
                }
                """.formatted(version, files);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /** URL → 応答ボディの fetcher。呼び出し回数を記録する。 */
    private static final class MapFetcher implements GameDataFetcher {
        private final Map<String, byte[]> bodies = new LinkedHashMap<>();
        private final Map<String, AtomicInteger> calls = new LinkedHashMap<>();

        void put(String url, byte[] body) {
            this.bodies.put(url, body);
            this.calls.putIfAbsent(url, new AtomicInteger());
        }

        int callCount(String url) {
            AtomicInteger c = this.calls.get(url);
            return c == null ? 0 : c.get();
        }

        @Override
        public void downloadTo(String url, long maxBytes, Path dest) throws IOException {
            this.calls.computeIfAbsent(url, k -> new AtomicInteger()).incrementAndGet();
            byte[] body = this.bodies.get(url);
            if (body == null) {
                throw new IOException("not found: " + url);
            }
            if (dest.getParent() != null) {
                Files.createDirectories(dest.getParent());
            }
            try (var out = Files.newOutputStream(dest)) {
                HttpsGameDataFetcher.copyLimited(new ByteArrayInputStream(body), out, maxBytes);
            } catch (IOException e) {
                Files.deleteIfExists(dest);
                throw e;
            }
        }
    }
}
