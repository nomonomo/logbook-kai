package logbook.internal.gamedata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.core.type.TypeReference;

import logbook.bean.AppQuestCondition;
import logbook.internal.JsonMappers;

/**
 * ゲームデータローダの単体テストと、配信 JSON のアプリ型デシリアライズ確認。
 * <p>
 * JSON 構文・quests 再生成一致・manifest sha は {@code GameDataTool} / CI の verify が担う。
 * 本クラスはそれらと重複せず、アプリが実際に使う型で読めることだけを見る。
 * data 更新検証では {@code -Pgamedata-update}（groups=gamedata）で実行する。
 * </p>
 */
@Tag("gamedata")
class GameDataLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readManifestFromPath() throws Exception {
        Path manifest = tempDir.resolve("manifest.json");
        Files.writeString(manifest, """
                {
                  "version": 3,
                  "files": {
                    "map/mapping.json": { "sha256": "abc" }
                  }
                }
                """);
        GameDataManifest loaded = GameDataLoader.readManifest(manifest);
        assertEquals(3L, loaded.getVersion());
        assertEquals(3L, loaded.effectiveVersion());
        assertEquals(1, loaded.safeFiles().size());
        assertEquals("abc", loaded.safeFiles().get("map/mapping.json").getSha256());
    }

    @Test
    void findLocalEmptyWhenMissing() {
        Optional<Path> missing = GameDataLoader.findLocal("no-such-file.json");
        assertTrue(missing.isEmpty());
    }

    @Test
    void preferLocalWhenLocalVersionIsNewerOrEqual() {
        assertTrue(GameDataLoader.preferLocal(2L, 1L));
        assertTrue(GameDataLoader.preferLocal(1L, 1L));
        assertFalse(GameDataLoader.preferLocal(1L, 2L));
        assertFalse(GameDataLoader.preferLocal(0L, 1L));
        assertTrue(GameDataLoader.preferLocal(1L, 0L));
        assertFalse(GameDataLoader.preferLocal(0L, 0L));
    }

    /** 外部が壊れていても同梱へフォールバックする */
    @Test
    void loadFallsBackToBundledWhenLocalJsonBroken() throws Exception {
        Path localRoot = tempDir.resolve("gamedata");
        Path mapping = localRoot.resolve("map/mapping.json");
        Files.createDirectories(mapping.getParent());
        Files.writeString(mapping, "not-json");

        AtomicInteger bundledOpens = new AtomicInteger();
        Map<String, String> result = GameDataLoader.load(
                "map/mapping.json",
                "bundled-mapping",
                is -> JsonMappers.READER_WITH_COMMENTS
                        .forType(new TypeReference<LinkedHashMap<String, String>>() {})
                        .readValue(is),
                Map.of(),
                () -> true,
                rel -> localRoot.resolve(rel),
                name -> {
                    bundledOpens.incrementAndGet();
                    return new ByteArrayInputStream("{\"from\":\"bundled\"}".getBytes(StandardCharsets.UTF_8));
                });

        assertEquals(Map.of("from", "bundled"), result);
        assertEquals(1, bundledOpens.get());
        assertTrue(Files.isRegularFile(mapping), "壊れた外部ファイルは削除しない");
    }

    /** 外部が正しければ同梱は読まない */
    @Test
    void loadUsesLocalWhenValid() throws Exception {
        Path localRoot = tempDir.resolve("gamedata");
        Path mapping = localRoot.resolve("map/mapping.json");
        Files.createDirectories(mapping.getParent());
        Files.writeString(mapping, "{\"from\":\"local\"}");

        AtomicInteger bundledOpens = new AtomicInteger();
        Map<String, String> result = GameDataLoader.load(
                "map/mapping.json",
                "bundled-mapping",
                is -> JsonMappers.READER_WITH_COMMENTS
                        .forType(new TypeReference<LinkedHashMap<String, String>>() {})
                        .readValue(is),
                Map.of(),
                () -> true,
                rel -> localRoot.resolve(rel),
                name -> {
                    bundledOpens.incrementAndGet();
                    return new ByteArrayInputStream("{\"from\":\"bundled\"}".getBytes(StandardCharsets.UTF_8));
                });

        assertEquals(Map.of("from", "local"), result);
        assertEquals(0, bundledOpens.get());
    }

    /**
     * data/ 配信ファイルが、ランタイムと同じ型で読めること。
     * seaarea はイベント非開催時 {@code {"areas":[]}} を許容する。
     */
    @Test
    void deliverableJsonLoadAsAppTypes() throws Exception {
        Path dataDir = dataDir();
        Path mapping = dataDir.resolve("map/mapping.json");
        Path seaarea = dataDir.resolve("seaarea/seaarea.json");
        Path quests = dataDir.resolve("quest/quests.json");

        assertTrue(Files.isRegularFile(mapping), "mapping.json");
        assertTrue(Files.isRegularFile(seaarea), "seaarea.json");
        assertTrue(Files.isRegularFile(quests), "quests.json");

        Map<String, String> mappingMap = JsonMappers.READER_WITH_COMMENTS
                .forType(new TypeReference<LinkedHashMap<String, String>>() {})
                .readValue(mapping);
        assertFalse(mappingMap.isEmpty(), "mapping は空でないこと");
        mappingMap.forEach((cell, symbol) -> {
            assertNotNull(cell, "mapping のキー");
            assertFalse(cell.isBlank(), "mapping のキーが空");
            assertNotNull(symbol, "mapping の値: " + cell);
        });

        SeaAreaFile seaAreaFile = JsonMappers.READER_WITH_COMMENTS
                .forType(SeaAreaFile.class)
                .readValue(seaarea);
        assertNotNull(seaAreaFile.getAreas(), "areas 配列（空可）");
        for (SeaAreaFile.Entry entry : seaAreaFile.getAreas()) {
            assertNotNull(entry, "areas の要素");
            assertTrue(entry.getArea() > 0, "area は 1 以上: " + entry);
            assertNotNull(entry.getName(), "name: area=" + entry.getArea());
        }

        Map<Integer, AppQuestCondition> questMap = JsonMappers.READER_WITH_COMMENTS
                .forType(new TypeReference<LinkedHashMap<Integer, AppQuestCondition>>() {})
                .readValue(quests);
        assertFalse(questMap.isEmpty(), "quests は空でないこと");
        questMap.forEach((questNo, condition) -> {
            assertNotNull(questNo, "questNo");
            assertTrue(questNo > 0, "questNo は正の整数: " + questNo);
            // type / resetType は任務によって省略あり。デシリアライズ成功が主目的
            assertNotNull(condition, "AppQuestCondition: " + questNo);
        });
    }

    private static Path dataDir() {
        String prop = System.getProperty("logbook.data.dir");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        return Path.of("../data").toAbsolutePath().normalize();
    }
}
