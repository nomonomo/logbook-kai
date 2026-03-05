package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import logbook.internal.BouyomiChanUtils.BouyomiDefaultSettings;
import logbook.internal.BouyomiChanUtils.BouyomiSetting;
import logbook.internal.BouyomiChanUtils.Params;

/**
 * {@link BouyomiChanUtils} の初期設定読み込み（STRICT_CREATOR_READER）と
 * BouyomiDefaultSettings / BouyomiSetting / Params のデシリアライズを検証する。
 */
class BouyomiChanUtilsTest {

    private static final String SETTINGS_RESOURCE = "logbook/bouyomi/test.json";

    @Test
    void strictCreatorReader_deserializesSettingsJson_fieldByFieldCheck() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SETTINGS_RESOURCE)) {
            assertNotNull(in, "テスト用 test.json が存在すること");

            BouyomiDefaultSettings root = JsonMappers.STRICT_CREATOR_READER
                    .forType(BouyomiDefaultSettings.class)
                    .readValue(in);

            List<BouyomiSetting> settings = root.settings();
            assertNotNull(settings);
            assertEquals(2, settings.size(), "settings 件数");

            BouyomiSetting first = settings.get(0);
            assertEquals("MapStartNextAlert", first.id());
            assertEquals("大破での進撃", first.label());
            assertEquals("${hiraganaNames} が大破しています", first.text());
            List<Params> firstParams = first.params();
            assertNotNull(firstParams);
            assertEquals(2, firstParams.size());

            Params param0 = firstParams.get(0);
            assertEquals("${hiraganaNames}", param0.tag());
            assertEquals("艦娘の名前（ひらがな）", param0.comment());

            Params param1 = firstParams.get(1);
            assertEquals("${kanjiNames}", param1.tag());
            assertEquals("艦娘の名前（漢字）", param1.comment());

            BouyomiSetting second = settings.get(1);
            assertEquals("AchievementGimmick2", second.id());
            assertEquals("ギミック解除\n(装甲破砕等)", second.label());
            assertEquals("ギミック解除 ギミックの達成を確認しました。", second.text());
            assertNotNull(second.params());
            assertEquals(0, second.params().size());
        }
    }
}
