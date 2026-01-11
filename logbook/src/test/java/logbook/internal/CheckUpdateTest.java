package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link CheckUpdate} のテストクラス。
 * 
 * <p>CheckUpdateクラスの機能をテストします。
 * AssetNameTestの内容も含めて、包括的なテストを提供します。</p>
 */
@Slf4j
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
     * 不明なプラットフォームの場合は空リストを返すことをテストします（更新対象外）。
     */
    @Test
    public void testGetAssetPrefixesUnknown() {
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
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
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        List<String> prefixes = checkUpdate.getAssetPrefixes(buildPlatform);
        
        assertNotNull(prefixes, "プレフィックスリストがnullです");
        
        // プラットフォームに応じた適切なアセット名が生成されることを確認
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
            // unknown やその他の値の場合は空リストを返す（更新対象外）
            assertTrue(prefixes.isEmpty(), "不明なプラットフォームの場合は空リストを返す必要があります（更新対象外）: " + buildPlatform);
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
            
            if (platform.equals("unknown")) {
                // 不明なプラットフォームの場合は空リストを返す（更新対象外）
                assertTrue(prefixes.isEmpty(), "不明なプラットフォームの場合は空リストを返す必要があります（更新対象外）");
            } else {
                // 既知のプラットフォームの場合は空でないリストを返す
                assertFalse(prefixes.isEmpty(), "プラットフォーム " + platform + " のプレフィックスリストが空です");
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
        
        // 不明なプラットフォームのアセットを検索（プラットフォーム固有のアセットが見つからない場合は更新対象外）
        Optional<JsonNode> unknownAsset = checkUpdate.findAssetForPlatform(assets, "unknown");
        assertTrue(unknownAsset.isEmpty(), "不明なプラットフォームではアセットが見つからない必要があります（更新対象外）");
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * プラットフォーム固有のアセットがない場合は更新対象外（空のOptional）を返すことをテストします。
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
        
        // Windowsプラットフォームのアセットを検索（プラットフォーム固有のアセットがない場合は更新対象外）
        Optional<JsonNode> winAsset = checkUpdate.findAssetForPlatform(assets, "win");
        assertTrue(winAsset.isEmpty(), "プラットフォーム固有のアセットがない場合は更新対象外（空のOptional）を返す必要があります");
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
            // unknown やその他の値の場合は更新対象外（空のOptional）を返す
            // このケースには到達しないはず（asset.isPresent()がfalseになるため）
            // もし到達した場合は、テストの前提条件が間違っている
            assertTrue(false, "不明なプラットフォームではアセットが見つからない必要があります（更新対象外）");
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
            "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body", "logbook-win.zip");
        assertTrue(withAsset.hasAsset(), "アセット情報が設定されている場合はtrueが返される必要があります");
        
        // アセット情報が設定されていない場合（downloadUrlがnull）
        CheckUpdate.VersionInfo withoutUrl = new CheckUpdate.VersionInfo(
            "v1.0.0", version, null, 1000000, "body", "logbook-win.zip");
        assertFalse(withoutUrl.hasAsset(), "downloadUrlがnullの場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（downloadUrlが空文字列）
        CheckUpdate.VersionInfo withEmptyUrl = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "", 1000000, "body", "logbook-win.zip");
        assertFalse(withEmptyUrl.hasAsset(), "downloadUrlが空文字列の場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（fileSizeが0）
        CheckUpdate.VersionInfo withZeroSize = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "https://example.com/logbook-win.zip", 0, "body", "logbook-win.zip");
        assertFalse(withZeroSize.hasAsset(), "fileSizeが0の場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（nameがnull）
        CheckUpdate.VersionInfo withNullName = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body", null);
        assertFalse(withNullName.hasAsset(), "nameがnullの場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（nameが空文字列）
        CheckUpdate.VersionInfo withEmptyName = new CheckUpdate.VersionInfo(
            "v1.0.0", version, "https://example.com/logbook-win.zip", 1000000, "body", "");
        assertFalse(withEmptyName.hasAsset(), "nameが空文字列の場合はfalseが返される必要があります");
        
        // アセット情報が設定されていない場合（downloadUrlとnameの両方がnull）
        CheckUpdate.VersionInfo withNullUrlAndName = new CheckUpdate.VersionInfo(
            "v1.0.0", version, null, 1000000, "body", null);
        assertFalse(withNullUrlAndName.hasAsset(), "downloadUrlとnameの両方がnullの場合はfalseが返される必要があります");
    }
    
    /**
     * {@link CheckUpdate#findAssetForPlatform(JsonNode, String)} のテスト。
     * 実際のリリース情報JSONファイルを読み込み、各プラットフォームのアセットをloggerでコンソール表示します。
     * 
     * <p>このテストは正常終了することを確認するのみです。
     * リリース情報（draft, prerelease）とアセット情報はloggerでコンソールに出力されます。</p>
     * 
     * <p>データの取得は以下のコマンドを実行してください（プロジェクトルートから実行）:</p>
     * <pre>
     * curl -s https://api.github.com/repos/nomonomo/logbook-kai/releases/tags/v26.1.1 > logbook/src/test/resources/logbook/internal/assets.json
     * </pre>
     * 
     * curl https://api.github.com/repos/nomonomo/logbook-kai/releases/tags/v26.1.1 -o test/resources/logbook/internal/assets.json
     */
    @Test
    public void testFindAssetForPlatformFromJsonFile() throws Exception {
        // テストリソースからリリース情報JSONファイルを読み込む
        InputStream jsonStream = CheckUpdateTest.class.getResourceAsStream("/logbook/internal/assets.json");
        assertNotNull(jsonStream, "リリース情報JSONファイルが見つかりません");
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode release = mapper.readTree(jsonStream);
        assertNotNull(release, "JSONのパースに失敗しました");
        
        // リリース情報をloggerで出力
        String tagName = release.has("tag_name") ? release.get("tag_name").asText() : "N/A";
        boolean draft = release.has("draft") && release.get("draft").asBoolean();
        boolean prerelease = release.has("prerelease") && release.get("prerelease").asBoolean();
        log.info("リリース情報 - タグ名: {}, draft: {}, prerelease: {}", tagName, draft, prerelease);
        
        // assets配列を抽出
        JsonNode assets = release.has("assets") && release.get("assets").isArray() 
            ? release.get("assets") 
            : mapper.createArrayNode();
        assertNotNull(assets, "assets配列が見つかりません");
        
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        
        // 各プラットフォームのアセットを検索してloggerで表示
        String[] platforms = {"win", "mac", "mac-aarch64", "linux", "unknown"};
        
        for (String platform : platforms) {
            Optional<JsonNode> asset = checkUpdate.findAssetForPlatform(assets, platform);
            
            if (asset.isPresent()) {
                JsonNode assetNode = asset.get();
                String name = assetNode.has("name") ? assetNode.get("name").asText() : "N/A";
                long size = assetNode.has("size") ? assetNode.get("size").asLong() : 0;
                String downloadUrl = assetNode.has("browser_download_url") 
                    ? assetNode.get("browser_download_url").asText() : "N/A";
                
                log.info("プラットフォーム: {} - アセット名: {}, サイズ: {} bytes, URL: {}", 
                    platform, name, size, downloadUrl);
            } else {
                log.info("プラットフォーム: {} - アセットが見つかりませんでした", platform);
            }
        }
        
        // テストは正常終了することを確認（アサーションは不要、loggerで出力のみ）
    }
    
    /**
     * {@link CheckUpdate#processTags(JsonNode)} のテスト。
     * 実際のtags JSONファイルを読み込み、バージョン情報をloggerでコンソール表示します。
     * 
     * <p>このテストは正常終了することを確認するのみです。
     * バージョン情報はloggerでコンソールに出力されます。</p>
     * 
     * <p>VersionはSupplier経由で取得され、テスト時は固定値が使用されます。</p>
     * 
     * <p>データの取得は以下のコマンドを実行してください（プロジェクトルートから実行）:</p>
     * <pre>
     * curl -s https://api.github.com/repos/nomonomo/logbook-kai/tags > logbook/src/test/resources/logbook/internal/tags.json
     * </pre>
     */
    @Test
    public void testProcessTagsFromJsonFile() throws Exception {
        // モック用のバージョンを固定値で設定
        String versionString = "23.12.1";
        Version mockVersion = new Version(versionString);
        
        // テストリソースからtags JSONファイルを読み込む
        InputStream jsonStream = CheckUpdateTest.class.getResourceAsStream("/logbook/internal/tags.json");
        assertNotNull(jsonStream, "tags JSONファイルが見つかりません");
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tags = mapper.readTree(jsonStream);
        assertNotNull(tags, "JSONのパースに失敗しました");
        assertTrue(tags.isArray(), "tagsは配列である必要があります");
        
        log.info("読み込んだtags数: {}", tags.size());
        
        CheckUpdate checkUpdate = CheckUpdate.getInstance();
        // Supplier経由でバージョンを取得するように設定
        checkUpdate.setVersionSupplier(() -> mockVersion);
        
        List<CheckUpdate.VersionInfo> candidates = checkUpdate.processTags(tags);
            
            // 処理結果をloggerで出力
            log.info("抽出された候補バージョン数: {}", candidates.size());
            log.info("現在のバージョン（モック）: {}", mockVersion);
            
            // 各候補バージョンの情報をloggerで出力
            for (int i = 0; i < candidates.size(); i++) {
                CheckUpdate.VersionInfo candidate = candidates.get(i);
                Version version = candidate.version();
                String tagName = candidate.tagname();
                boolean hasAsset = candidate.hasAsset();
                String downloadUrl = candidate.downloadUrl() != null ? candidate.downloadUrl() : "N/A";
                long fileSize = candidate.fileSize();
                
                log.info("候補[{}] - タグ名: {}, バージョン: {}, アセット有無: {}, ファイルサイズ: {} bytes, URL: {}", 
                    i, tagName, version, hasAsset, fileSize, downloadUrl);
            }
            
            // バージョンが新しい順にソートされていることを確認
            for (int i = 0; i < candidates.size() - 1; i++) {
                Version current = candidates.get(i).version();
                Version next = candidates.get(i + 1).version();
                assertTrue(current.compareTo(next) >= 0, 
                    "バージョンが新しい順にソートされていません: " + current + " >= " + next);
            }
            
        // 現在のバージョンより新しいバージョンのみが含まれることを確認
        for (CheckUpdate.VersionInfo candidate : candidates) {
            assertTrue(mockVersion.compareTo(candidate.version()) < 0,
                "現在のバージョンより新しいバージョンのみが含まれる必要があります: " + candidate.version());
        }
        
        // テストは正常終了することを確認
    }
}

