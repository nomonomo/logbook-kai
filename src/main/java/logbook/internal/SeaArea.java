package logbook.internal;

import java.util.stream.Stream;

/**
 * 海域
 *
 */
public enum SeaArea {

    識別札1("Ｒ方面防備部隊", 1, 4),
    識別札2("進出第一陣", 2, 8),
    識別札3("進出第二陣", 3, 10),
    識別札4("機動部隊", 4, 12),
    識別札5("方面護衛隊", 5, 14),
    識別札6("呉防備戦隊", 6, 16),
    識別札7("第二艦隊", 7, 18),
    識別札8("機動部隊別動隊", 8, 20),
    識別札9("佐世保配備艦隊", 9, 22),
    識別札10("連合救援艦隊", 10, 5);

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
