package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link CheckUpdate} のテストクラス。
 * 
 * <p>CheckUpdateクラスの機能をテストします。
 * AssetNameTestの内容も含めて、包括的なテストを提供します。</p>
 */
public class CheckUpdateTest {

    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * Windowsプラットフォームのアセット名をテストします。
     */
    @Test
    public void testGetAssetPrefixesWindows() {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
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
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
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
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
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
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
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
     * 不明なプラットフォームのアセット名をテストします。
     */
    @Test
    public void testGetAssetPrefixesUnknown() {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        List<String> prefixes = checkUpdate.getAssetPrefixes("unknown");
        
        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertFalse(prefixes.isEmpty(), "プレフィックスリストが空です");
        assertEquals("", prefixes.get(0), "不明なプラットフォームのアセット名が正しくありません");
    }
    
    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * 実際のビルドプラットフォーム情報に基づいてアセット名が正しく生成されることを確認します。
     */
    @Test
    public void testGetAssetPrefixesFromActualPlatform() {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        List<String> prefixes = checkUpdate.getAssetPrefixes(buildPlatform);
        
        assertNotNull(prefixes, "プレフィックスリストがnullです");
        assertFalse(prefixes.isEmpty(), "プレフィックスリストが空です");
        
        // プラットフォームに応じた適切なアセット名が生成されることを確認
        switch (buildPlatform) {
        case "win":
            assertEquals("logbook-win.zip", prefixes.get(0), "Windowsプラットフォームのアセット名が正しくありません");
            break;
        case "mac":
            assertEquals("logbook-mac.zip", prefixes.get(0), "Intel Macプラットフォームのアセット名が正しくありません");
            break;
        case "mac-aarch64":
            assertEquals("logbook-mac-aarch64.zip", prefixes.get(0), "Apple Silicon Macプラットフォームのアセット名が正しくありません");
            break;
        case "linux":
            assertEquals("logbook-linux.zip", prefixes.get(0), "Linuxプラットフォームのアセット名が正しくありません");
            break;
        default:
            // unknown やその他の値の場合もエラーにしない
            assertTrue(true, "不明なプラットフォーム: " + buildPlatform);
        }
    }
    
    /**
     * {@link CheckUpdate#getAssetPrefixes(String)} のテスト。
     * すべてのプラットフォームでアセット名が正しく生成されることを確認します。
     */
    @Test
    public void testGetAssetPrefixesAllPlatforms() {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        String[] platforms = {"win", "mac", "mac-aarch64", "linux", "unknown"};
        
        for (String platform : platforms) {
            List<String> prefixes = checkUpdate.getAssetPrefixes(platform);
            
            assertNotNull(prefixes, "プラットフォーム " + platform + " のプレフィックスリストがnullです");
            assertFalse(prefixes.isEmpty(), "プラットフォーム " + platform + " のプレフィックスリストが空です");
            
            // 最初の要素が空文字列でないことを確認（unknown以外）
            if (!platform.equals("unknown")) {
                assertFalse(prefixes.get(0).isEmpty(), "プラットフォーム " + platform + " の最初のアセット名が空文字列です");
            }
        }
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * 実際のJSONデータを使用して、プラットフォームに応じたアセットが正しく検索されることを確認します。
     */
    @Test
    public void testFindAssetForPlatform() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        // テスト用のJSONデータを作成（実際のGitHub APIレスポンス形式に合わせる）
        String json = """
            [
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454051",
                    "id": 335454051,
                    "node_id": "RA_kwDOG6p2_s4T_p9j",
                    "name": "logbook-win.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 9,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-win.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454052",
                    "id": 335454052,
                    "node_id": "RA_kwDOG6p2_s4T_p9k",
                    "name": "logbook-mac.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 5,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454053",
                    "id": 335454053,
                    "node_id": "RA_kwDOG6p2_s4T_p9l",
                    "name": "logbook-mac-aarch64.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 3,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac-aarch64.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454054",
                    "id": 335454054,
                    "node_id": "RA_kwDOG6p2_s4T_p9m",
                    "name": "logbook-linux.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 2,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-linux.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454055",
                    "id": 335454055,
                    "node_id": "RA_kwDOG6p2_s4T_p9n",
                    "name": "other-file.txt",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "text/plain",
                    "state": "uploaded",
                    "size": 100,
                    "digest": "sha256:abc123",
                    "download_count": 0,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/other-file.txt"
                }
            ]
            """;
        
        JsonNode assets = mapper.readTree(json);
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // Windowsプラットフォームのアセットを検索
        Optional<JsonNode> winAsset = checkUpdate.findAssetForPlatform(assets, "win");
        assertTrue(winAsset.isPresent(), "Windowsプラットフォームのアセットが見つかりません");
        assertEquals("logbook-win.zip", winAsset.get().get("name").asText(), "Windowsプラットフォームのアセット名が正しくありません");
        
        // Intel Macプラットフォームのアセットを検索
        Optional<JsonNode> macAsset = checkUpdate.findAssetForPlatform(assets, "mac");
        assertTrue(macAsset.isPresent(), "Intel Macプラットフォームのアセットが見つかりません");
        assertEquals("logbook-mac.zip", macAsset.get().get("name").asText(), "Intel Macプラットフォームのアセット名が正しくありません");
        
        // Apple Silicon Macプラットフォームのアセットを検索
        Optional<JsonNode> macAarch64Asset = checkUpdate.findAssetForPlatform(assets, "mac-aarch64");
        assertTrue(macAarch64Asset.isPresent(), "Apple Silicon Macプラットフォームのアセットが見つかりません");
        assertEquals("logbook-mac-aarch64.zip", macAarch64Asset.get().get("name").asText(), "Apple Silicon Macプラットフォームのアセット名が正しくありません");
        
        // Linuxプラットフォームのアセットを検索
        Optional<JsonNode> linuxAsset = checkUpdate.findAssetForPlatform(assets, "linux");
        assertTrue(linuxAsset.isPresent(), "Linuxプラットフォームのアセットが見つかりません");
        assertEquals("logbook-linux.zip", linuxAsset.get().get("name").asText(), "Linuxプラットフォームのアセット名が正しくありません");
        
        // 不明なプラットフォームのアセットを検索（汎用アセットが返されることを確認）
        Optional<JsonNode> unknownAsset = checkUpdate.findAssetForPlatform(assets, "unknown");
        assertTrue(unknownAsset.isPresent(), "不明なプラットフォームでも汎用アセットが見つかる必要があります");
        String unknownAssetName = unknownAsset.get().get("name").asText();
        assertTrue(unknownAssetName.startsWith("logbook") && unknownAssetName.endsWith(".zip"), 
            "不明なプラットフォームでは汎用アセット（logbook*.zip）が返される必要があります。取得値: " + unknownAssetName);
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * プラットフォーム固有のアセットがない場合のフォールバック動作をテストします。
     */
    @Test
    public void testFindAssetForPlatformFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        // プラットフォーム固有のアセットがない場合のテストデータ（実際のAPIレスポンス形式に合わせる）
        String json = """
            [
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454056",
                    "id": 335454056,
                    "node_id": "RA_kwDOG6p2_s4T_p9o",
                    "name": "logbook-generic.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 1,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-generic.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454057",
                    "id": 335454057,
                    "node_id": "RA_kwDOG6p2_s4T_p9p",
                    "name": "other-file.txt",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "text/plain",
                    "state": "uploaded",
                    "size": 100,
                    "digest": "sha256:abc123",
                    "download_count": 0,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/other-file.txt"
                }
            ]
            """;
        
        JsonNode assets = mapper.readTree(json);
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // Windowsプラットフォームのアセットを検索（フォールバックで汎用アセットが返される）
        Optional<JsonNode> winAsset = checkUpdate.findAssetForPlatform(assets, "win");
        assertTrue(winAsset.isPresent(), "Windowsプラットフォームで汎用アセットが見つかりません");
        assertEquals("logbook-generic.zip", winAsset.get().get("name").asText(), "フォールバックで汎用アセットが返される必要があります");
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * アセットが見つからない場合の動作をテストします。
     */
    @Test
    public void testFindAssetForPlatformNotFound() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        // アセットが見つからない場合のテストデータ（実際のAPIレスポンス形式に合わせる）
        String json = """
            [
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454058",
                    "id": 335454058,
                    "node_id": "RA_kwDOG6p2_s4T_p9q",
                    "name": "other-file.txt",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "text/plain",
                    "state": "uploaded",
                    "size": 100,
                    "digest": "sha256:abc123",
                    "download_count": 0,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/other-file.txt"
                }
            ]
            """;
        
        JsonNode assets = mapper.readTree(json);
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // Windowsプラットフォームのアセットを検索（見つからない）
        Optional<JsonNode> winAsset = checkUpdate.findAssetForPlatform(assets, "win");
        assertTrue(winAsset.isEmpty(), "アセットが見つからない場合は空のOptionalが返される必要があります");
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * nullまたは空のアセット配列の場合の動作をテストします。
     */
    @Test
    public void testFindAssetForPlatformWithNullOrEmptyAssets() throws Exception {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // nullの場合
        Optional<JsonNode> nullAsset = checkUpdate.findAssetForPlatform(null, "win");
        assertTrue(nullAsset.isEmpty(), "nullのアセット配列の場合は空のOptionalが返される必要があります");
        
        // 空の配列の場合
        ObjectMapper mapper = new ObjectMapper();
        JsonNode emptyAssets = mapper.readTree("[]");
        Optional<JsonNode> emptyAsset = checkUpdate.findAssetForPlatform(emptyAssets, "win");
        assertTrue(emptyAsset.isEmpty(), "空のアセット配列の場合は空のOptionalが返される必要があります");
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * 実際のビルドプラットフォームに基づいて、正しく動作することを確認します。
     */
    @Test
    public void testFindAssetForPlatformWithActualPlatform() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // すべてのプラットフォームのアセットを含むテストデータ（実際のAPIレスポンス形式に合わせる）
        String json = """
            [
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454051",
                    "id": 335454051,
                    "node_id": "RA_kwDOG6p2_s4T_p9j",
                    "name": "logbook-win.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 9,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-win.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454052",
                    "id": 335454052,
                    "node_id": "RA_kwDOG6p2_s4T_p9k",
                    "name": "logbook-mac.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 5,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454053",
                    "id": 335454053,
                    "node_id": "RA_kwDOG6p2_s4T_p9l",
                    "name": "logbook-mac-aarch64.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 3,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-mac-aarch64.zip"
                },
                {
                    "url": "https://api.github.com/repos/nomonomo/logbook-kai/releases/assets/335454054",
                    "id": 335454054,
                    "node_id": "RA_kwDOG6p2_s4T_p9m",
                    "name": "logbook-linux.zip",
                    "label": "",
                    "uploader": {
                        "login": "github-actions[bot]",
                        "id": 41898282,
                        "node_id": "MDM6Qm90NDE4OTgyODI=",
                        "type": "Bot",
                        "site_admin": false
                    },
                    "content_type": "application/zip",
                    "state": "uploaded",
                    "size": 89654472,
                    "digest": "sha256:f2fafa98693ac023cf57029d4d7614ddef7f5c0a5d4d6779569f72a1cd4ce1d0",
                    "download_count": 2,
                    "created_at": "2026-01-02T08:46:01Z",
                    "updated_at": "2026-01-02T08:46:04Z",
                    "browser_download_url": "https://github.com/nomonomo/logbook-kai/releases/download/v26.1.1/logbook-linux.zip"
                }
            ]
            """;
        
        JsonNode assets = mapper.readTree(json);
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // 実際のビルドプラットフォームに基づいてアセットを検索
        Optional<JsonNode> asset = checkUpdate.findAssetForPlatform(assets, buildPlatform);
        
        assertTrue(asset.isPresent(), "プラットフォーム " + buildPlatform + " に対応するアセットが見つかりません");
        
        String assetName = asset.get().get("name").asText();
        assertNotNull(assetName, "アセット名がnullです");
        assertFalse(assetName.isEmpty(), "アセット名が空文字列です");
        
        // プラットフォームに応じた適切なアセットが返されることを確認
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
            // unknown やその他の値の場合もエラーにしない
            assertTrue(assetName.startsWith("logbook") && assetName.endsWith(".zip"), 
                "不明なプラットフォームでは汎用アセットが返される必要があります。取得値: " + assetName);
        }
    }
    
    /**
     * {@link CheckUpdate#processTags(JsonNode)} のテスト。
     * tagsのJSONを処理し、新しいバージョンを抽出してソートする機能をテストします。
     */
    @Test
    public void testProcessTags() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // テスト用のtags JSONデータを作成
        // 現在のバージョンより新しいバージョンを含む
        Version currentVersion = Version.getCurrent();
        int nextMajor = currentVersion.getMajor() + 1;
        int nextMinor = currentVersion.getMinor() + 1;
        int nextRevision = currentVersion.getRevision() + 1;
        
        // 実際のGitHub APIレスポンス形式に合わせたJSONデータを作成
        // 実際のAPIレスポンスには name, zipball_url, tarball_url, commit, node_id が含まれる
        String json = String.format("""
            [
                {
                    "name": "v%d.%d.%d",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/v%d.%d.%d",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/v%d.%d.%d",
                    "commit": {
                        "sha": "18564885e7df7d7dd39d9f80e29e22ae66b8e8e8",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/18564885e7df7d7dd39d9f80e29e22ae66b8e8e8"
                    },
                    "node_id": "REF_kwDOG6p2_rFyZWZzL3RhZ3MvdjI2LjEuMQ"
                },
                {
                    "name": "v%d.%d",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/v%d.%d",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/v%d.%d",
                    "commit": {
                        "sha": "1c34715d64ad10a89bf32edb9d31fc894cc25b1f",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/1c34715d64ad10a89bf32edb9d31fc894cc25b1f"
                    },
                    "node_id": "REF_kwDOG6p2_rJyZWZzL3RhZ3MvdjI1LjEyLjE"
                },
                {
                    "name": "v%d.%d.%d",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/v%d.%d.%d",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/v%d.%d.%d",
                    "commit": {
                        "sha": "55f1ef0d9ee6c8dcdf01992be59222853e601054",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/55f1ef0d9ee6c8dcdf01992be59222853e601054"
                    },
                    "node_id": "REF_kwDOG6p2_rJyZWZzL3RhZ3MvdjI1LjExLjI"
                },
                {
                    "name": "invalid-tag",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/invalid-tag",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/invalid-tag",
                    "commit": {
                        "sha": "af8bbe8b686937ea85afdddd50fe9a1c689f6178",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/af8bbe8b686937ea85afdddd50fe9a1c689f6178"
                    },
                    "node_id": "REF_kwDOG6p2_rJyZWZzL3RhZ3MvdjI1LjExLjE"
                },
                {
                    "name": "v1.0.0",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/v1.0.0",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/v1.0.0",
                    "commit": {
                        "sha": "0b4a6f72c150057da2b3a05c619cc37b43b2a499",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/0b4a6f72c150057da2b3a05c619cc37b43b2a499"
                    },
                    "node_id": "REF_kwDOG6p2_rFyZWZzL3RhZ3MvdjI1LjEwLjQ"
                },
                {
                    "name": "v%d.%d.%d",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/v%d.%d.%d",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/v%d.%d.%d",
                    "commit": {
                        "sha": "1bfe077a1e4ef5d003b275fac42d59384f7f55e7",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/1bfe077a1e4ef5d003b275fac42d59384f7f55e7"
                    },
                    "node_id": "REF_kwDOG6p2_rFyZWZzL3RhZ3MvdjI1LjEwLjM"
                }
            ]
            """, 
            nextMajor, nextMinor, nextRevision, nextMajor, nextMinor, nextRevision, nextMajor, nextMinor, nextRevision,
            nextMajor, nextMinor, nextMajor, nextMinor, nextMajor, nextMinor,
            nextMajor, nextMinor, 0, nextMajor, nextMinor, 0, nextMajor, nextMinor, 0,
            nextMajor, nextMinor, nextRevision + 1, nextMajor, nextMinor, nextRevision + 1, nextMajor, nextMinor, nextRevision + 1);
        
        JsonNode tags = mapper.readTree(json);
        List<CheckUpdate.VersionInfo> candidates = checkUpdate.processTags(tags);
        
        // 新しいバージョンが抽出されることを確認
        assertNotNull(candidates, "候補バージョンリストがnullです");
        assertFalse(candidates.isEmpty(), "新しいバージョンが見つかりません");
        
        // バージョンが新しい順にソートされていることを確認
        for (int i = 0; i < candidates.size() - 1; i++) {
            Version current = candidates.get(i).version();
            Version next = candidates.get(i + 1).version();
            assertTrue(current.compareTo(next) >= 0, 
                "バージョンが新しい順にソートされていません: " + current + " >= " + next);
        }
        
        // 現在のバージョンより新しいバージョンのみが含まれることを確認
        for (CheckUpdate.VersionInfo candidate : candidates) {
            assertTrue(Version.getCurrent().compareTo(candidate.version()) < 0,
                "現在のバージョンより新しいバージョンのみが含まれる必要があります: " + candidate.version());
        }
    }
    
    /**
     * {@link CheckUpdate#processTags(JsonNode)} のテスト。
     * 無効なtags JSONの場合の動作をテストします。
     */
    @Test
    public void testProcessTagsWithInvalidData() throws Exception {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        ObjectMapper mapper = new ObjectMapper();
        
        // nullの場合
        List<CheckUpdate.VersionInfo> nullResult = checkUpdate.processTags(null);
        assertNotNull(nullResult, "nullの場合でも空リストが返される必要があります");
        assertTrue(nullResult.isEmpty(), "nullの場合は空リストが返される必要があります");
        
        // 空の配列の場合
        JsonNode emptyTags = mapper.readTree("[]");
        List<CheckUpdate.VersionInfo> emptyResult = checkUpdate.processTags(emptyTags);
        assertNotNull(emptyResult, "空の配列の場合でも空リストが返される必要があります");
        assertTrue(emptyResult.isEmpty(), "空の配列の場合は空リストが返される必要があります");
        
        // 無効な形式のtagのみの場合（実際のAPIレスポンス形式に合わせる）
        String invalidJson = """
            [
                {
                    "name": "invalid-tag",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/invalid-tag",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/invalid-tag",
                    "commit": {
                        "sha": "af8bbe8b686937ea85afdddd50fe9a1c689f6178",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/af8bbe8b686937ea85afdddd50fe9a1c689f6178"
                    },
                    "node_id": "REF_kwDOG6p2_rJyZWZzL3RhZ3MvdjI1LjExLjE"
                },
                {
                    "name": "no-version",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/no-version",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/no-version",
                    "commit": {
                        "sha": "0b4a6f72c150057da2b3a05c619cc37b43b2a499",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/0b4a6f72c150057da2b3a05c619cc37b43b2a499"
                    },
                    "node_id": "REF_kwDOG6p2_rFyZWZzL3RhZ3MvdjI1LjEwLjQ"
                },
                {
                    "name": "",
                    "zipball_url": "https://api.github.com/repos/nomonomo/logbook-kai/zipball/refs/tags/",
                    "tarball_url": "https://api.github.com/repos/nomonomo/logbook-kai/tarball/refs/tags/",
                    "commit": {
                        "sha": "1bfe077a1e4ef5d003b275fac42d59384f7f55e7",
                        "url": "https://api.github.com/repos/nomonomo/logbook-kai/commits/1bfe077a1e4ef5d003b275fac42d59384f7f55e7"
                    },
                    "node_id": "REF_kwDOG6p2_rFyZWZzL3RhZ3MvdjI1LjEwLjM"
                }
            ]
            """;
        JsonNode invalidTags = mapper.readTree(invalidJson);
        List<CheckUpdate.VersionInfo> invalidResult = checkUpdate.processTags(invalidTags);
        assertNotNull(invalidResult, "無効なtagのみの場合でも空リストが返される必要があります");
        assertTrue(invalidResult.isEmpty(), "無効なtagのみの場合は空リストが返される必要があります");
    }
    
    /**
     * {@link CheckUpdate.VersionInfo#hasAsset()} のテスト。
     * アセット情報が設定されているかどうかを確認する機能をテストします。
     */
    @Test
    public void testVersionInfoHasAsset() {
        Version version = new Version("1.0.0");
        
        // アセット情報が設定されている場合
        CheckUpdate.VersionInfo withAsset = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body");
        assertTrue(withAsset.hasAsset(), "アセット情報が設定されている場合はtrueが返される必要があります");
        
        // アセット情報が設定されていない場合（downloadUrlがnull）
        CheckUpdate.VersionInfo withoutUrl = new CheckUpdate.VersionInfo(
            "v1.0.0", version, null, 1000000, "body");
        assertFalse(withoutUrl.hasAsset(), "downloadUrlがnullの場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（downloadUrlが空文字列）
        CheckUpdate.VersionInfo withEmptyUrl = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "", 1000000, "body");
        assertFalse(withEmptyUrl.hasAsset(), "downloadUrlが空文字列の場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（fileSizeが0）
        CheckUpdate.VersionInfo withZeroSize = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "https://example.com/logbook-win.zip", 0, "body");
        assertFalse(withZeroSize.hasAsset(), "fileSizeが0の場合はfalseが返される必要があります");
    }
}

