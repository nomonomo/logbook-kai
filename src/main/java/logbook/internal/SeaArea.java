package logbook.internal;

import java.util.stream.Stream;

/**
 * 海域
 *
 */
public enum SeaArea {

    識別札1("南西方面艦隊", 1),
    識別札2("第三十一戦隊", 2),
    識別札3("輸送部隊", 3),
    識別札4("連合艦隊", 4),
    識別札5("第十六戦隊", 5),
    識別札6("シマ船団護衛", 6),
    識別札7("遊撃部隊", 7),
    識別札8("決戦艦隊", 8),
    識別札9("識別札9", 9),
    識別札10("識別札10", 10);

    /** 名前 */
    private String name;

    /** 海域(イベント海域のお札) */
    private int area;

    SeaArea(String name, int area) {
        this.name = name;
        this.area = area;
    }

    /**
     * 名前を取得します。
     * @return 名前
     */
    public String getName() {
        return this.name;
    }

    /**
     * 海域(イベント海域のお札)を取得します。
     * @return 海域(イベント海域のお札)
     */
    public int getArea() {
        return this.area;
    }

    @Override
    public String toString() {
        return this.name;
    }

    /**
     * イベント海域を取得します
     *
     * @param area お札
     * @return 海域
     */
    public static SeaArea fromArea(int area) {
        return Stream.of(SeaArea.values()).filter(s -> s.getArea() == area).findAny().orElse(null);
    }
}
