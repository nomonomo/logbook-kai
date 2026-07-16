package logbook.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.type.TypeReference;

import logbook.bean.AppQuestCondition;
import logbook.internal.gamedata.GameDataLoader;
import logbook.internal.gamedata.GameDataPaths;
import lombok.extern.slf4j.Slf4j;

/**
 * 任務条件 JSON の読み込み。
 * {@code quest/quests.json}（マニフェスト版の新しい方。同版なら外部）から読み込みキャッシュする。
 */
@Slf4j
public final class AppQuestConditionLoader {

    /** quests.json 由来のキャッシュ（未ロード時は null） */
    private static volatile Map<Integer, AppQuestCondition> cache;

    private AppQuestConditionLoader() {
    }

    /**
     * 任務番号から条件を読み込む。無い場合・失敗時は null。
     */
    public static AppQuestCondition loadFromResource(int questNo) {
        ensureLoaded();
        return cache.get(questNo);
    }

    /**
     * 進捗 UI 等で、当該任務の条件定義があるか判定する。
     */
    public static boolean contains(int questNo) {
        ensureLoaded();
        return cache.containsKey(questNo);
    }

    /**
     * quests.json を再読み込みする（ゲームデータ DL 後など）。
     */
    public static synchronized void reload() {
        cache = loadQuestsBundle();
    }

    private static void ensureLoaded() {
        if (cache == null) {
            synchronized (AppQuestConditionLoader.class) {
                if (cache == null) {
                    cache = loadQuestsBundle();
                }
            }
        }
    }

    private static Map<Integer, AppQuestCondition> loadQuestsBundle() {
        Map<Integer, AppQuestCondition> map = GameDataLoader.load(
                GameDataPaths.QUESTS,
                GameDataPaths.CLASSPATH_QUESTS,
                is -> JsonMappers.READER_WITH_COMMENTS
                        .forType(new TypeReference<LinkedHashMap<Integer, AppQuestCondition>>() {})
                        .readValue(is),
                Map.of());
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        log.debug("quest/quests.json から {} 件の任務条件を読み込みました", map.size());
        return Collections.unmodifiableMap(map);
    }

    /**
     * 任務条件 JSON を AppQuestCondition にデシリアライズする。
     */
    public static AppQuestCondition load(InputStream is) throws IOException {
        return JsonMappers.READER_WITH_COMMENTS
                .forType(AppQuestCondition.class)
                .readValue(is);
    }

    /**
     * 指定パスの任務条件 JSON を AppQuestCondition にデシリアライズする。
     */
    public static AppQuestCondition load(Path path) throws IOException {
        return JsonMappers.READER_WITH_COMMENTS
                .forType(AppQuestCondition.class)
                .readValue(path);
    }
}
