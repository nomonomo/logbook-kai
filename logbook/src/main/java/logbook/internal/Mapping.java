package logbook.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.core.type.TypeReference;

import logbook.bean.MapinfoMst;
import logbook.bean.MapinfoMstCollection;
import logbook.internal.gamedata.GameDataLoader;
import logbook.internal.gamedata.GameDataPaths;

/**
 * セルNoと記号のマッピング
 *
 */
public class Mapping {

    private static Mapping INSTANCE = new Mapping();

    /** セルNoと記号のマッピング*/
    private Map<String, String> mapping;

    private Mapping() {
        this.mapping = loadMapping();
    }

    private static Map<String, String> loadMapping() {
        // readValue(InputStream) に渡したストリームは Jackson が閉じるため、load 側の try-with-resources との二重 close は問題ない
        return GameDataLoader.load(
                GameDataPaths.MAPPING,
                GameDataPaths.CLASSPATH_MAPPING,
                is -> JsonMappers.READER_WITH_COMMENTS
                        .forType(new TypeReference<LinkedHashMap<String, String>>() {})
                        .readValue(is),
                Collections.emptyMap());
    }

    /**
     * マッピングを再読み込みします。
     */
    public static synchronized void reload() {
        INSTANCE.mapping = loadMapping();
    }

    public Map<String, String> getMapping() {
        return this.mapping;
    }

    /**
     * 対象セルの記号を返します
     * 
     * @param key 海域-マップ番号-セル形式のキー
     * @return 対象セルの記号
     */
    public static String getCell(String key) {
        String ret = INSTANCE.getMapping().get(key);
        if (ret == null) {
            return key.substring(key.lastIndexOf('-') + 1);
        }
        return ret;
    }

    /**
     * 対象セルの記号を返します
     * 
     * @param mapareaId 海域
     * @param mapinfoNo マップ番号
     * @param no セル
     * @return 対象セルの記号
     */
    public static String getCell(Integer mapareaId, Integer mapinfoNo, Integer no) {
        String key = String.valueOf(mapareaId) + "-" + String.valueOf(mapinfoNo) + "-" + String.valueOf(no);
        String ret = INSTANCE.getMapping().get(key);
        if (ret == null) {
            return String.valueOf(no);
        }
        return ret;
    }

    /**
     * 海域名と略称(例:1-5)のマッピングを返します
     * @return 海域名と略称(例:1-5)のマッピング
     */
    public static Map<String, String> fullNameToShort() {
        return MapinfoMstCollection.get()
                .getMapinfo()
                .values()
                .stream()
                .collect(Collectors.toMap(MapinfoMst::getName,
                        m -> m.getMapareaId() + "-" + m.getNo(), (a, b) -> a));
    }
}
