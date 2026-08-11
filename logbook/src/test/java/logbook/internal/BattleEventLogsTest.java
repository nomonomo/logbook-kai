package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import logbook.bean.BattleEventLog;
import logbook.bean.BattleLog;
import logbook.bean.MapStartNext;

public class BattleEventLogsTest {

    @Test
    public void testCreatePortGimmickWithLastArea() {
        MapStartNext next = new MapStartNext();
        next.setMapareaId(6);
        next.setMapinfoNo(5);
        next.setNo(15);
        BattleLog battle = new BattleLog();
        battle.getNext().add(next);

        BattleEventLog event = BattleEventLogs.createPortGimmick(battle, 1);

        assertNotNull(event);
        assertEquals("帰還時通知", event.getType());
        assertTrue(event.getArea().startsWith("6-5"));
        assertEquals("15", event.getCell());
        assertEquals("ギミック達成通知", event.getGimmick());
    }

    @Test
    public void testCreatePortGimmickWithoutLastArea() {
        BattleEventLog event = BattleEventLogs.createPortGimmick(null, 1);

        assertNotNull(event);
        assertEquals("", event.getArea());
        assertEquals("", event.getCell());
        assertNull(BattleEventLogs.createPortGimmick(null, 0));
    }

    @Test
    public void testCreateNextAndDestructionGimmicks() {
        MapStartNext next = new MapStartNext();
        next.setM1(2);
        next.setM2(1);
        MapStartNext.DestructionBattle destruction = new MapStartNext.DestructionBattle();
        destruction.setM1(3);
        next.setDestructionBattle(destruction);

        List<BattleEventLog> direct = BattleEventLogs.createNextGimmicks(next);
        BattleEventLog airRaid = BattleEventLogs.createAirRaid(next);

        assertEquals(1, direct.size());
        assertEquals("ルート追加等、装甲破砕等", direct.get(0).getGimmick());
        assertEquals("ルート追加等", airRaid.getGimmick());
    }
}
