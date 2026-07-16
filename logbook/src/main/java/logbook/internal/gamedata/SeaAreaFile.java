package logbook.internal.gamedata;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 識別札定義 JSON のルート。
 */
@Data
public class SeaAreaFile {

    /** 識別札一覧 */
    private List<Entry> areas = new ArrayList<>();

    /**
     * 識別札1件。
     */
    @Data
    public static class Entry {
        /** 表示名 */
        private String name;
        /** 識別札番号 */
        private int area;
        /** お札アイコン番号（common_event_N の N） */
        private int imageNo;
    }
}
