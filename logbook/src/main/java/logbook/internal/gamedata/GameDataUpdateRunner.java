package logbook.internal.gamedata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import logbook.internal.JsonMappers;
import lombok.extern.slf4j.Slf4j;

/**
 * リモートマニフェストに基づくゲームデータの差分更新処理。
 */
@Slf4j
final class GameDataUpdateRunner {

    /** マニフェスト GET の最大サイズ（1 MiB） */
    static final long MAX_MANIFEST_BYTES = 1024L * 1024L;

    /** 個別ファイル GET の最大サイズ（8 MiB） */
    static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;

    private final GameDataFetcher fetcher;
    private final Supplier<Path> rootSupplier;
    private final LongSupplier currentVersionSupplier;
    private final Supplier<String> baseUrlSupplier;
    private final Runnable onMappingUpdated;
    private final Runnable onSeaAreaUpdated;
    private final Runnable onQuestsUpdated;

    GameDataUpdateRunner(
            GameDataFetcher fetcher,
            Supplier<Path> rootSupplier,
            LongSupplier currentVersionSupplier,
            Supplier<String> baseUrlSupplier,
            Runnable onMappingUpdated,
            Runnable onSeaAreaUpdated,
            Runnable onQuestsUpdated) {
        this.fetcher = Objects.requireNonNull(fetcher);
        this.rootSupplier = Objects.requireNonNull(rootSupplier);
        this.currentVersionSupplier = Objects.requireNonNull(currentVersionSupplier);
        this.baseUrlSupplier = Objects.requireNonNull(baseUrlSupplier);
        this.onMappingUpdated = Objects.requireNonNull(onMappingUpdated);
        this.onSeaAreaUpdated = Objects.requireNonNull(onSeaAreaUpdated);
        this.onQuestsUpdated = Objects.requireNonNull(onQuestsUpdated);
    }

    /**
     * リモートマニフェストを確認し、新しければ差分をダウンロードします。
     *
     * @throws Exception 更新処理の失敗
     */
    void run() throws Exception {
        String baseUrl = normalizeBaseUrl(this.baseUrlSupplier.get());
        Path root = this.rootSupplier.get().toAbsolutePath().normalize();
        Files.createDirectories(root);

        Path manifestTmp = Files.createTempFile(root, "manifest-", ".json");
        try {
            GameDataManifest remote = downloadRemoteManifest(baseUrl, manifestTmp);
            if (remote == null) {
                return;
            }
            long remoteVersion = remote.effectiveVersion();
            if (!isNewerThanCurrent(remoteVersion)) {
                return;
            }

            log.info("新しいゲームデータを検出したためダウンロードします（現行={}, リモート={}）",
                    this.currentVersionSupplier.getAsLong(), remoteVersion);

            Path staging = prepareStaging(root);
            UpdatedKinds updated = downloadChangedFiles(baseUrl, root, staging, remote);
            moveManifestIntoStaging(manifestTmp, staging);
            manifestTmp = null;

            promoteStaging(staging, root);
            deleteRecursively(staging);
            notifyReloads(updated);
            log.info("ゲームデータの更新が完了しました（版={}）", remoteVersion);
        } finally {
            if (manifestTmp != null) {
                Files.deleteIfExists(manifestTmp);
            }
        }
    }

    /**
     * リモート manifest を取得して読み込む。失敗・不正時は null。
     */
    private GameDataManifest downloadRemoteManifest(String baseUrl, Path manifestTmp) throws Exception {
        String manifestUrl = joinUrl(baseUrl, GameDataPaths.MANIFEST);
        log.debug("ゲームデータマニフェストを取得します: {}", manifestUrl);
        this.fetcher.downloadTo(manifestUrl, MAX_MANIFEST_BYTES, manifestTmp);
        if (Files.size(manifestTmp) == 0) {
            log.warn("ゲームデータマニフェストの取得に失敗しました");
            return null;
        }
        GameDataManifest remote = GameDataLoader.readManifest(manifestTmp);
        if (remote.effectiveVersion() <= 0L) {
            log.warn("リモートのゲームデータ版が不正です: {}", remote.getVersion());
            return null;
        }
        return remote;
    }

    private boolean isNewerThanCurrent(long remoteVersion) {
        long current = this.currentVersionSupplier.getAsLong();
        if (current >= remoteVersion) {
            log.debug("ゲームデータは最新です（現行={}, リモート={}）", current, remoteVersion);
            return false;
        }
        return true;
    }

    private static Path prepareStaging(Path root) throws IOException {
        Path staging = root.resolve(".staging");
        deleteRecursively(staging);
        Files.createDirectories(staging);
        return staging;
    }

    /**
     * 変更のある配信ファイルだけ staging へ DL・検証する。
     */
    private UpdatedKinds downloadChangedFiles(
            String baseUrl,
            Path root,
            Path staging,
            GameDataManifest remote) throws Exception {
        UpdatedKinds updated = new UpdatedKinds();
        for (Map.Entry<String, GameDataManifest.FileEntry> entry : remote.safeFiles().entrySet()) {
            String relative = entry.getKey();
            if (isUnsafeRelativePath(relative)) {
                log.warn("不正な相対パスをスキップします: {}", relative);
                continue;
            }
            String expectedSha = entry.getValue() != null ? entry.getValue().getSha256() : null;
            if (isUnchangedLocally(root, relative, expectedSha)) {
                log.debug("変更なしのためスキップ: {}", relative);
                continue;
            }
            Path dest;
            try {
                dest = resolveUnderParent(staging, relative);
            } catch (IOException e) {
                log.warn("不正な相対パスをスキップします: {} ({})", relative, e.getMessage());
                continue;
            }
            String fileUrl = joinUrl(baseUrl, relative);
            this.fetcher.downloadTo(fileUrl, MAX_FILE_BYTES, dest);
            verifyDownloadedFile(dest, relative, expectedSha);
            updated.mark(relative);
        }
        return updated;
    }

    /** {@code ..}・空・null など、manifest 上で受け付けない相対パス */
    static boolean isUnsafeRelativePath(String relative) {
        return relative == null || relative.isBlank() || relative.contains("..");
    }

    private static boolean isUnchangedLocally(Path root, String relative, String expectedSha)
            throws Exception {
        if (expectedSha == null || expectedSha.isBlank()) {
            return false;
        }
        Path localExisting = root.resolve(relative).normalize();
        return Files.isRegularFile(localExisting)
                && expectedSha.equalsIgnoreCase(sha256Hex(localExisting));
    }

    /**
     * parent 配下に収まる Path を返す。外へ出る場合は例外。
     */
    static Path resolveUnderParent(Path parent, String relative) throws IOException {
        Path dest = parent.resolve(relative).normalize();
        if (!dest.startsWith(parent)) {
            throw new IOException("パストラバーサルを検出: " + relative);
        }
        return dest;
    }

    private static void verifyDownloadedFile(Path dest, String relative, String expectedSha)
            throws Exception {
        if (expectedSha != null && !expectedSha.isBlank()) {
            String actual = sha256Hex(dest);
            if (!expectedSha.equalsIgnoreCase(actual)) {
                throw new IOException("SHA-256 不一致: " + relative);
            }
        }
        if (relative.endsWith(".json")) {
            try (InputStream is = Files.newInputStream(dest)) {
                JsonMappers.READER_WITH_COMMENTS.readTree(is);
            } catch (Exception e) {
                throw new IOException("JSON 検証失敗: " + relative, e);
            }
        }
    }

    private static void moveManifestIntoStaging(Path manifestTmp, Path staging) throws IOException {
        Path manifestDest = staging.resolve(GameDataPaths.MANIFEST);
        Files.move(manifestTmp, manifestDest, StandardCopyOption.REPLACE_EXISTING);
    }

    private void notifyReloads(UpdatedKinds updated) {
        if (updated.mapping) {
            this.onMappingUpdated.run();
        }
        if (updated.seaArea) {
            this.onSeaAreaUpdated.run();
        }
        if (updated.quests) {
            this.onQuestsUpdated.run();
        }
    }

    static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return GameDataPaths.DEFAULT_BASE_URL;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static String joinUrl(String base, String relative) {
        String rel = relative.startsWith("/") ? relative.substring(1) : relative;
        return base + "/" + rel;
    }

    static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }

    static String sha256Hex(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path);
                DigestInputStream din = new DigestInputStream(in, digest);
                OutputStream discard = OutputStream.nullOutputStream()) {
            din.transferTo(discard);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void promoteStaging(Path staging, Path root) throws IOException {
        Files.createDirectories(root);
        try (var walk = Files.walk(staging)) {
            walk.filter(Files::isRegularFile).forEach(src -> {
                try {
                    Path rel = staging.relativize(src);
                    Path dest = root.resolve(rel);
                    Files.createDirectories(dest.getParent());
                    try {
                        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException atomicFailed) {
                        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** どの配信ファイルを更新したか（reload 通知用） */
    private static final class UpdatedKinds {
        private boolean mapping;
        private boolean seaArea;
        private boolean quests;

        void mark(String relative) {
            if (GameDataPaths.MAPPING.equals(relative)) {
                this.mapping = true;
            } else if (GameDataPaths.SEAAREA.equals(relative)) {
                this.seaArea = true;
            } else if (GameDataPaths.QUESTS.equals(relative)) {
                this.quests = true;
            }
        }
    }
}
