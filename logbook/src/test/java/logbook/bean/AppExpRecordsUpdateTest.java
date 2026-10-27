package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link AppExpRecords#update(Basic)} が半日境界の初回だけ基準を更新することのテスト。
 */
class AppExpRecordsUpdateTest {

    @Test
    void update_changesBaselineOncePerHalfDay() {
        Basic basic = new Basic();
        basic.setExperience(100_000);
        AppExpRecords records = new AppExpRecords();

        assertTrue(records.update(basic));
        assertEquals(100_000L, records.getExp12h());
        long firstTime = records.getTime12h();

        basic.setExperience(110_000);
        assertFalse(records.update(basic));
        assertEquals(100_000L, records.getExp12h());
        assertEquals(firstTime, records.getTime12h());
    }
}
