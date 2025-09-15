package logbook.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 艦種グループ
 *
 */
public enum ShipTypeGroup {

    海防艦("海防艦"),
    駆逐艦("駆逐艦"),
    巡洋艦("軽巡洋艦", "重雷装巡洋艦", "重巡洋艦", "航空巡洋艦", "練習巡洋艦"),
    空母("軽空母", "正規空母", "装甲空母"),
    戦艦("戦艦", "航空戦艦"),
    潜水艦("潜水艦", "潜水空母"),
    水上機母艦("水上機母艦"),
    揚陸艦("揚陸艦"),
    工作艦("工作艦"),
    潜水母艦("潜水母艦"),
    補給艦("補給艦");

    private static final Map<String, List<String>> groupMap = new HashMap<>();

    static {
        for (ShipTypeGroup shipTypeGroup : values()) {
            groupMap.put(shipTypeGroup.name(), Arrays.asList(shipTypeGroup.group));
        }
    }

    private String[] group;

    private ShipTypeGroup(String... shipTypes) {
        this.group = shipTypes;
    }

    public List<String> shipTypes() {
        return Collections.unmodifiableList(Arrays.asList(this.group));
    }

    public static List<String> shipTypes(String groupName) {
        return groupMap.getOrDefault(groupName, Collections.emptyList());
    }
}
