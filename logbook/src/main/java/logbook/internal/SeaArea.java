package logbook.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import logbook.internal.gamedata.GameDataLoader;
import logbook.internal.gamedata.GameDataPaths;
import logbook.internal.gamedata.SeaAreaFile;

/**
 * イベント海域の識別札。
 * {@code seaarea/seaarea.json}（マニフェスト版の新しい方。同版なら外部）から読み込む。件数・内容は JSON が正本。
 */
public final class SeaArea {

    private static volatile List<SeaArea> areas = List.of();
    private static volatile Map<Integer, SeaArea> byArea = Map.of();

    static {
        reload();
    }

    /** 名前 */
    private final String name;

    /** 海域(イベント海域のお札) */
    private final int area;

    /** お札アイコンの No（resources\common\common_event） */
    private final int imageNo;

    /**
     * @param name 表示名
     * @param area 識別札番号
     * @param imageNo お札アイコン番号
     */
    public SeaArea(String name, int area, int imageNo) {
        this.name = Objects.requireNonNull(name, "name");
        this.area = area;
        this.imageNo = imageNo;
    }

    /**
     * 名前を取得します。
     *
     * @return 名前
     */
    public String getName() {
        return this.name;
    }

    /**
     * 海域(イベント海域のお札)を取得します。
     *
     * @return 海域(イベント海域のお札)
     */
    public int getArea() {
        return this.area;
    }

    /**
     * お札アイコンの No を取得します。
     *
     * @return お札アイコンの No
     */
    public int getImageId() {
        return this.imageNo;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeaArea other)) {
            return false;
        }
        return this.area == other.area;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.area);
    }

    /**
     * 定義済み識別札の一覧を返します（読み取り専用）。
     *
     * @return 識別札一覧
     */
    public static List<SeaArea> values() {
        return areas;
    }

    /**
     * イベント海域を取得します。
     *
     * @param area お札
     * @return 海域（未定義時は null）
     */
    public static SeaArea fromArea(int area) {
        return byArea.get(area);
    }

    /**
     * 識別札定義を再読み込みします。
     */
    public static synchronized void reload() {
        List<SeaArea> loaded = loadAreas();
        areas = Collections.unmodifiableList(loaded);
        byArea = indexByArea(areas);
    }

    private static List<SeaArea> loadAreas() {
        return GameDataLoader.load(
                GameDataPaths.SEAAREA,
                GameDataPaths.CLASSPATH_SEAAREA,
                is -> {
                    SeaAreaFile file = JsonMappers.READER_WITH_COMMENTS
                            .forType(SeaAreaFile.class)
                            .readValue(is);
                    return toSeaAreas(file);
                },
                List.of());
    }

    private static List<SeaArea> toSeaAreas(SeaAreaFile file) {
        if (file == null || file.getAreas() == null) {
            return List.of();
        }
        List<SeaArea> result = new ArrayList<>();
        for (SeaAreaFile.Entry entry : file.getAreas()) {
            if (entry == null) {
                continue;
            }
            String name = entry.getName();
            if (name == null || name.isBlank()) {
                name = "識別札" + entry.getArea();
            }
            result.add(new SeaArea(name, entry.getArea(), entry.getImageNo()));
        }
        return result;
    }

    private static Map<Integer, SeaArea> indexByArea(List<SeaArea> list) {
        Map<Integer, SeaArea> map = new LinkedHashMap<>();
        for (SeaArea area : list) {
            map.put(area.getArea(), area);
        }
        return Collections.unmodifiableMap(map);
    }
}
