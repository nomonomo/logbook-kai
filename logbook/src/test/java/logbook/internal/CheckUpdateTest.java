package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * {@link CheckUpdate} のテストクラス。
 *
 * <p>CheckUpdateクラスの機能をテストします。
 * AssetNameTestの内容も含めて、包括的なテストを提供します。</p>
 */
@Slf4j
public class CheckUpdateTest {

    private static List<CheckUpdate.GitHubAsset> readAssets(String json) {
        return JsonMappers.LENIENT_READER
                .forType(new TypeReference<List<CheckUpdate.GitHubAsset>>() {
                })
                .readValue(json);
    }

    private static List<CheckUpdate.GitHubTag> readTags(String json) {
        return JsonMappers.LENIENT_READER
                .forType(new TypeReference<List<CheckUpdate.GitHubTag>>() {
                })
                .readValue(json);
    }

    private static List<CheckUpdate.GitHubTag> readTags(InputStream in) {
        return JsonMappers.LENIENT_READER
                .forType(new TypeReference<List<CheckUpdate.GitHubTag>>() {
                })
                .readValue(in);
    }

    private static CheckUpdate.GitHubRelease readRelease(InputStream in) {
        return JsonMappers.LENIENT_READER
                .forType(CheckUpdate.GitHubRelease.class)
                .readValue(in);
    }

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * Windowsプラットフォームのアセット名をテストします。
     */
    @Test
    public void testGetAssetPrefixesWindows() {
        CheckUpdate checkUpdate = new CheckUpdate();
        List<String> prefixes = checkUpdate.getAssetPrefixes("win");

        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertFalse(prefixes.isEmpty(), "プレフィックスリストが空です");
        assertEquals("logbook-win.zip", prefixes.get(0), "Windowsプラットフォームのアセット名が正しくありません");
    }

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * Intel Macプラットフォームのアセット名をテストします。
     */
    @Test
    public void testGetAssetPrefixesIntelMac() {
        CheckUpdate checkUpdate = new CheckUpdate();
        List<String> prefixes = checkUpdate.getAssetPrefixes("mac");

        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertFalse(prefixes.isEmpty(), "プレフィックスリストが空です");
        assertEquals("logbook-mac.zip", prefixes.get(0), "Intel Macプラットフォームのアセット名が正しくありません");
    }

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * Apple Silicon Macプラットフォームのアセット名をテストします。
     */
    @Test
    public void testGetAssetPrefixesAppleSiliconMac() {
        CheckUpdate checkUpdate = new CheckUpdate();
        List<String> prefixes = checkUpdate.getAssetPrefixes("mac-aarch64");

        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertFalse(prefixes.isEmpty(), "プレフィックスリストが空です");
        assertEquals("logbook-mac-aarch64.zip", prefixes.get(0), "Apple Silicon Macプラットフォームのアセット名が正しくありません");
    }

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * Linuxプラットフォームのアセット名をテストします。
     */
    @Test
    public void testGetAssetPrefixesLinux() {
        CheckUpdate checkUpdate = new CheckUpdate();
        List<String> prefixes = checkUpdate.getAssetPrefixes("linux");

        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertFalse(prefixes.isEmpty(), "プレフィックスリストが空です");
        assertEquals("logbook-linux.zip", prefixes.get(0), "Linuxプラットフォームのアセット名が正しくありません");
        assertTrue(prefixes.size() >= 3, "Linuxプラットフォームのプレフィックスリストには少なくとも3つの要素が必要です");
        assertEquals("logbook-kai-linux_", prefixes.get(1), "Linuxプラットフォームの2番目のアセット名が正しくありません");
        assertEquals("logbook-kai-ubuntu_", prefixes.get(2), "Linuxプラットフォームの3番目のアセット名が正しくありません");
    }

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * 不明なプラットフォームの場合は空リストを返すことをテストします（更新対象外）。
     */
    @Test
    public void testGetAssetPrefixesUnknown() {
        CheckUpdate checkUpdate = new CheckUpdate();
        List<String> prefixes = checkUpdate.getAssetPrefixes("unknown");

        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertTrue(prefixes.isEmpty(), "不明なプラットフォームの場合は空リストを返す必要があります（更新対象外）");
    }

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * 実際のビルドプラットフォーム情報に基づいてアセット名が正しく生成されることを確認します。
     */
    @Test
    public void testGetAssetPrefixesFromActualPlatform() {
        CheckUpdate checkUpdate = new CheckUpdate();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        List<String> prefixes = checkUpdate.getAssetPrefixes(buildPlatform);

        assertNotNull(prefixes, "プレフィックスリストがnullです");

        switch (buildPlatform) {
        case "win":
            assertFalse(prefixes.isEmpty(), "Windowsプラットフォームのプレフィックスリストが空です");
            assertEquals("logbook-win.zip", prefixes.get(0), "Windowsプラットフォームのアセット名が正しくありません");
            break;
        case "mac":
            assertFalse(prefixes.isEmpty(), "Intel Macプラットフォームのプレフィックスリストが空です");
            assertEquals("logbook-mac.zip", prefixes.get(0), "Intel Macプラットフォームのアセット名が正しくありません");
            break;
        case "mac-aarch64":
            assertFalse(prefixes.isEmpty(), "Apple Silicon Macプラットフォームのプレフィックスリストが空です");
            assertEquals("logbook-mac-aarch64.zip", prefixes.get(0), "Apple Silicon Macプラットフォームのアセット名が正しくありません");
            break;
        case "linux":
            assertFalse(prefixes.isEmpty(), "Linuxプラットフォームのプレフィックスリストが空です");
            assertEquals("logbook-linux.zip", prefixes.get(0), "Linuxプラットフォームのアセット名が正しくありません");
            break;
        default:
            assertTrue(prefixes.isEmpty(), "不明なプラットフォームの場合は空リストを返す必要があります（更新対象外）: " + buildPlatform);
        }
    }

    /**
     * {@link CheckUpdate#findAssetForPlatform(List, String)} のテスト。
     * 実際のJSONデータを使用して、プラットフォームに応じたアセットが正しく検索されることを確認します。
     */
    @Test
    public void testFindAssetForPlatform() throws Exception {
        String json = """
            [
                {
                    "name": "logbook-win.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-win.zip"
                },
                {
                    "name": "logbook-mac.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac.zip"
                },
                {
                    "name": "logbook-mac-aarch64.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac-aarch64.zip"
                },
                {
                    "name": "logbook-linux.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-linux.zip"
                },
                {
                    "name": "other-file.txt",
                    "size": 100,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/other-file.txt"
                }
            ]
            """;

        List<CheckUpdate.GitHubAsset> assets = readAssets(json);
        CheckUpdate checkUpdate = new CheckUpdate();

        Optional<CheckUpdate.GitHubAsset> winAsset = checkUpdate.findAssetForPlatform(assets, "win");
        assertTrue(winAsset.isPresent(), "Windowsプラットフォームのアセットが見つかりません");
        assertEquals("logbook-win.zip", winAsset.get().name(), "Windowsプラットフォームのアセット名が正しくありません");

        Optional<CheckUpdate.GitHubAsset> macAsset = checkUpdate.findAssetForPlatform(assets, "mac");
        assertTrue(macAsset.isPresent(), "Intel Macプラットフォームのアセットが見つかりません");
        assertEquals("logbook-mac.zip", macAsset.get().name(), "Intel Macプラットフォームのアセット名が正しくありません");

        Optional<CheckUpdate.GitHubAsset> macAarch64Asset = checkUpdate.findAssetForPlatform(assets, "mac-aarch64");
        assertTrue(macAarch64Asset.isPresent(), "Apple Silicon Macプラットフォームのアセットが見つかりません");
        assertEquals("logbook-mac-aarch64.zip", macAarch64Asset.get().name(), "Apple Silicon Macプラットフォームのアセット名が正しくありません");

        Optional<CheckUpdate.GitHubAsset> linuxAsset = checkUpdate.findAssetForPlatform(assets, "linux");
        assertTrue(linuxAsset.isPresent(), "Linuxプラットフォームのアセットが見つかりません");
        assertEquals("logbook-linux.zip", linuxAsset.get().name(), "Linuxプラットフォームのアセット名が正しくありません");

        Optional<CheckUpdate.GitHubAsset> unknownAsset = checkUpdate.findAssetForPlatform(assets, "unknown");
        assertTrue(unknownAsset.isEmpty(), "不明なプラットフォームではアセットが見つからない必要があります（更新対象外）");
    }

    /**
     * {@link CheckUpdate#findAssetForPlatform(List, String)} のテスト。
     * プラットフォーム固有のアセットがない場合は更新対象外（空のOptional）を返すことをテストします。
     */
    @Test
    public void testFindAssetForPlatformFallback() throws Exception {
        String json = """
            [
                {
                    "name": "logbook-generic.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-generic.zip"
                },
                {
                    "name": "other-file.txt",
                    "size": 100,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/other-file.txt"
                }
            ]
            """;

        List<CheckUpdate.GitHubAsset> assets = readAssets(json);
        CheckUpdate checkUpdate = new CheckUpdate();

        Optional<CheckUpdate.GitHubAsset> winAsset = checkUpdate.findAssetForPlatform(assets, "win");
        assertTrue(winAsset.isEmpty(), "プラットフォーム固有のアセットがない場合は更新対象外（空のOptional）を返す必要があります");
    }

    /**
     * {@link CheckUpdate#findAssetForPlatform(List, String)} のテスト。
     * nullまたは空のアセット配列の場合の動作をテストします。
     */
    @Test
    public void testFindAssetForPlatformWithNullOrEmptyAssets() throws Exception {
        CheckUpdate checkUpdate = new CheckUpdate();

        Optional<CheckUpdate.GitHubAsset> nullAsset = checkUpdate.findAssetForPlatform(null, "win");
        assertTrue(nullAsset.isEmpty(), "nullのアセット配列の場合は空のOptionalが返される必要があります");

        Optional<CheckUpdate.GitHubAsset> emptyAsset = checkUpdate.findAssetForPlatform(Collections.emptyList(), "win");
        assertTrue(emptyAsset.isEmpty(), "空のアセット配列の場合は空のOptionalが返される必要があります");
    }

    /**
     * {@link CheckUpdate#findAssetForPlatform(List, String)} のテスト。
     * 実際のビルドプラットフォームに基づいて、正しく動作することを確認します。
     */
    @Test
    public void testFindAssetForPlatformWithActualPlatform() throws Exception {
        String buildPlatform = SystemPlatform.getBuildPlatform();

        String json = """
            [
                {
                    "name": "logbook-win.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-win.zip"
                },
                {
                    "name": "logbook-mac.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac.zip"
                },
                {
                    "name": "logbook-mac-aarch64.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac-aarch64.zip"
                },
                {
                    "name": "logbook-linux.zip",
                    "size": 89654472,
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-linux.zip"
                }
            ]
            """;

        List<CheckUpdate.GitHubAsset> assets = readAssets(json);
        CheckUpdate checkUpdate = new CheckUpdate();

        Optional<CheckUpdate.GitHubAsset> asset = checkUpdate.findAssetForPlatform(assets, buildPlatform);

        assertTrue(asset.isPresent(), "プラットフォーム " + buildPlatform + " に対応するアセットが見つかりません");

        String assetName = asset.get().name();
        assertNotNull(assetName, "アセット名がnullです");
        assertFalse(assetName.isEmpty(), "アセット名が空文字列です");

        switch (buildPlatform) {
        case "win":
            assertEquals("logbook-win.zip", assetName, "Windowsプラットフォームのアセット名が正しくありません");
            break;
        case "mac":
            assertEquals("logbook-mac.zip", assetName, "Intel Macプラットフォームのアセット名が正しくありません");
            break;
        case "mac-aarch64":
            assertEquals("logbook-mac-aarch64.zip", assetName, "Apple Silicon Macプラットフォームのアセット名が正しくありません");
            break;
        case "linux":
            assertEquals("logbook-linux.zip", assetName, "Linuxプラットフォームのアセット名が正しくありません");
            break;
        default:
            assertTrue(false, "不明なプラットフォームではアセットが見つからない必要があります（更新対象外）");
        }
    }

    /**
     * {@link CheckUpdate#processTags(List)} のテスト。
     * 無効なtags JSONの場合の動作をテストします。
     */
    @Test
    public void testProcessTagsWithInvalidData() throws Exception {
        CheckUpdate checkUpdate = new CheckUpdate();

        List<CheckUpdate.VersionInfo> nullResult = checkUpdate.processTags(null);
        assertNotNull(nullResult, "nullの場合でも空リストが返される必要があります");
        assertTrue(nullResult.isEmpty(), "nullの場合は空リストが返される必要があります");

        List<CheckUpdate.VersionInfo> emptyResult = checkUpdate.processTags(Collections.emptyList());
        assertNotNull(emptyResult, "空の配列の場合でも空リストが返される必要があります");
        assertTrue(emptyResult.isEmpty(), "空の配列の場合は空リストが返される必要があります");

        String invalidJson = """
            [
                { "name": "invalid-tag" },
                { "name": "no-version" },
                { "name": "" }
            ]
            """;
        List<CheckUpdate.GitHubTag> invalidTags = readTags(invalidJson);
        List<CheckUpdate.VersionInfo> invalidResult = checkUpdate.processTags(invalidTags);
        assertNotNull(invalidResult, "無効なtagのみの場合でも空リストが返される必要があります");
        assertTrue(invalidResult.isEmpty(), "無効なtagのみの場合は空リストが返される必要があります");
    }

    /**
     * {@link CheckUpdate.VersionInfo#hasAsset()} のテスト。
     */
    @Test
    public void testVersionInfoHasAsset() {
        Version version = new Version("1.0.0");

        CheckUpdate.VersionInfo withAsset = new CheckUpdate.VersionInfo(
                "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body", "logbook-win.zip");
        assertTrue(withAsset.hasAsset(), "アセット情報が設定されている場合はtrueが返される必要があります");

        CheckUpdate.VersionInfo withoutUrl = new CheckUpdate.VersionInfo(
                "v1.0.0", version, null, 1000000, "body", "logbook-win.zip");
        assertFalse(withoutUrl.hasAsset(), "downloadUrlがnullの場合はfalseが返される必要があります");

        CheckUpdate.VersionInfo withEmptyUrl = new CheckUpdate.VersionInfo(
                "v1.0.0", version, "", 1000000, "body", "logbook-win.zip");
        assertFalse(withEmptyUrl.hasAsset(), "downloadUrlが空文字列の場合はfalseが返される必要があります");

        CheckUpdate.VersionInfo withZeroSize = new CheckUpdate.VersionInfo(
                "v1.0.0", version, "https://example.com/logbook-win.zip", 0, "body", "logbook-win.zip");
        assertFalse(withZeroSize.hasAsset(), "fileSizeが0の場合はfalseが返される必要があります");

        CheckUpdate.VersionInfo withNullName = new CheckUpdate.VersionInfo(
                "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body", null);
        assertFalse(withNullName.hasAsset(), "nameがnullの場合はfalseが返される必要があります");

        CheckUpdate.VersionInfo withEmptyName = new CheckUpdate.VersionInfo(
                "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body", "");
        assertFalse(withEmptyName.hasAsset(), "nameが空文字列の場合はfalseが返される必要があります");

        CheckUpdate.VersionInfo withNullUrlAndName = new CheckUpdate.VersionInfo(
                "v1.0.0", version, null, 1000000, "body", null);
        assertFalse(withNullUrlAndName.hasAsset(), "downloadUrlとnameの両方がnullの場合はfalseが返される必要があります");
    }

    /**
     * {@link CheckUpdate#findAssetForPlatform(List, String)} のテスト。
     * 実際のリリース情報JSONファイルを読み込み、各プラットフォームのアセットをloggerでコンソール表示します。
     */
    @Test
    public void testFindAssetForPlatformFromJsonFile() throws Exception {
        InputStream jsonStream = CheckUpdateTest.class.getResourceAsStream("/logbook/internal/assets.json");
        assertNotNull(jsonStream, "リリース情報JSONファイルが見つかりません");

        CheckUpdate.GitHubRelease release = readRelease(jsonStream);
        assertNotNull(release, "JSONのパースに失敗しました");

        log.info("リリース情報 - draft: {}, prerelease: {}", release.draft(), release.prerelease());

        List<CheckUpdate.GitHubAsset> assets = release.assets() != null ? release.assets() : Collections.emptyList();
        CheckUpdate checkUpdate = new CheckUpdate();

        String[] platforms = { "win", "mac", "mac-aarch64", "linux", "unknown" };

        for (String platform : platforms) {
            Optional<CheckUpdate.GitHubAsset> asset = checkUpdate.findAssetForPlatform(assets, platform);

            if (asset.isPresent()) {
                CheckUpdate.GitHubAsset assetNode = asset.get();
                log.info("プラットフォーム: {} - アセット名: {}, サイズ: {} bytes, URL: {}",
                        platform, assetNode.name(), assetNode.size(), assetNode.browserDownloadUrl());
            } else {
                log.info("プラットフォーム: {} - アセットが見つかりませんでした", platform);
            }
        }
    }

    /**
     * {@link CheckUpdate#processTags(List)} のテスト。
     * 実際のtags JSONファイルを読み込み、バージョン情報をloggerでコンソール表示します。
     */
    @Test
    public void testProcessTagsFromJsonFile() throws Exception {
        String versionString = "23.12.1";
        Version mockVersion = new Version(versionString);

        InputStream jsonStream = CheckUpdateTest.class.getResourceAsStream("/logbook/internal/tags.json");
        assertNotNull(jsonStream, "tags JSONファイルが見つかりません");

        List<CheckUpdate.GitHubTag> tags = readTags(jsonStream);
        assertNotNull(tags, "JSONのパースに失敗しました");

        log.info("読み込んだtags数: {}", tags.size());

        CheckUpdate checkUpdate = new CheckUpdate();
        checkUpdate.setVersionSupplier(() -> mockVersion);

        List<CheckUpdate.VersionInfo> candidates = checkUpdate.processTags(tags);

        log.info("抽出された候補バージョン数: {}", candidates.size());
        log.info("現在のバージョン（モック）: {}", mockVersion);

        for (int i = 0; i < candidates.size(); i++) {
            CheckUpdate.VersionInfo candidate = candidates.get(i);
            log.info("候補[{}] - タグ名: {}, バージョン: {}, アセット有無: {}, ファイルサイズ: {} bytes, URL: {}",
                    i, candidate.tagname(), candidate.version(), candidate.hasAsset(),
                    candidate.fileSize(),
                    candidate.downloadUrl() != null ? candidate.downloadUrl() : "N/A");
        }

        for (int i = 0; i < candidates.size() - 1; i++) {
            Version current = candidates.get(i).version();
            Version next = candidates.get(i + 1).version();
            assertTrue(current.compareTo(next) >= 0,
                    "バージョンが新しい順にソートされていません: " + current + " >= " + next);
        }

        for (CheckUpdate.VersionInfo candidate : candidates) {
            assertTrue(mockVersion.compareTo(candidate.version()) < 0,
                    "現在のバージョンより新しいバージョンのみが含まれる必要があります: " + candidate.version());
        }
    }
}
