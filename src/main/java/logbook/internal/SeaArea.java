package logbook.internal;

import java.util.stream.Stream;

/**
 * 海域
 *
 */
public enum SeaArea {

    識別札1("横須賀防備戦隊", 1, 4),
    識別札2("第二水雷戦隊", 2, 6),
    識別札3("支援連合艦隊", 3, 8),
    識別札4("連合艦隊", 4, 10),
    識別札5("空母機動部隊", 5, 12),
    識別札6("伊号作戦部隊", 6, 14),
    識別札7("逆上陸部隊", 7, 16),
    識別札8("決戦連合艦隊", 8, 18),
    識別札9("識別札9", 9, 20),
    識別札10("識別札10", 10, 22);

    /** 名前 */
    private String name;

    /** 海域(イベント海域のお札) */
    private int area;


    /** お札アイコンのNo
     *  resources\common\common_event
     *  */
    private int imageNo;
    
    SeaArea(String name, int area, int imageNo) {
        this.name = name;
        this.area = area;
        this.imageNo = imageNo;
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

    /**
     * お札アイコンのNoを取得します。
     * @return お札アイコンのNo
     */
    public int getImageId() {
        return this.imageNo;
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
