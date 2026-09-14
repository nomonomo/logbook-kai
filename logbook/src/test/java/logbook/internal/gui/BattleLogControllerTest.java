package logbook.internal.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import logbook.internal.BattleLogs;
import logbook.internal.BattleLogs.SimpleBattleLog;

public class BattleLogControllerTest {

    @Test
    public void testMergeGimmicksIntoBattleAndAirRaidRows() {
        SimpleBattleLog battle = battle(
                "2026-08-08 01:49:05", "7-5 ジャワ島沖", "11", "ルート追加等");
        SimpleBattleLog nextGimmick = event(
                "2026-08-08 01:49:20", "ギミック", "7-5 ジャワ島沖", "12",
                "ルート追加等", "ルート追加等");
        SimpleBattleLog portGimmick = event(
                "2026-08-08 01:49:38", "帰還時通知", "7-5 ジャワ島沖", "11",
                "ギミック達成通知", "ギミック達成通知");
        SimpleBattleLog airRaid = event(
                "2026-08-08 01:50:00", "空襲", "7-5 ジャワ島沖", "13",
                "損害なし", "装甲破砕等");

        List<BattleLogDetail> details = BattleLogs.mergeLogsForUnit(
                List.of(battle),
                List.of(nextGimmick, portGimmick, airRaid)).stream()
                .map(BattleLogDetail::toBattleLogDetail)
                .toList();

        assertEquals(2, details.size());
        BattleLogDetail battleDetail = details.stream()
                .filter(detail -> "戦闘".equals(detail.getEventType()))
                .findFirst()
                .orElseThrow();
        assertEquals("ルート追加等", battleDetail.getGimmick());
        assertEquals("ギミック達成通知", battleDetail.getReturnNotice());

        BattleLogDetail airRaidDetail = details.stream()
                .filter(detail -> "空襲".equals(detail.getEventType()))
                .findFirst()
                .orElseThrow();
        assertEquals("空襲", airRaidDetail.getCell());
        assertEquals("損害なし", airRaidDetail.getRank());
        assertEquals("装甲破砕等", airRaidDetail.getGimmick());
    }

    private static SimpleBattleLog battle(String date, String area, String cell, String gimmick) {
        String[] columns = columns(66);
        columns[0] = date;
        columns[1] = area;
        columns[2] = cell;
        columns[65] = gimmick;
        return new SimpleBattleLog(String.join(",", columns));
    }

    private static SimpleBattleLog event(
            String date,
            String type,
            String area,
            String cell,
            String content,
            String gimmick) {
        String[] columns = columns(40);
        columns[0] = date;
        columns[1] = type;
        columns[2] = area;
        columns[3] = cell;
        columns[4] = content;
        columns[39] = gimmick;
        return SimpleBattleLog.fromEventLine(String.join(",", columns));
    }

    private static String[] columns(int size) {
        String[] columns = new String[size];
        Arrays.fill(columns, "");
        return columns;
    }
}
