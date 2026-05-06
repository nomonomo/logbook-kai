package logbook.internal;

import java.io.IOException;
import java.io.InputStream;

import logbook.bean.MissionCondition;

/**
 * 遠征条件 JSON の読み込み。Missions.getMissionCondition およびテストから利用する。
 */
final class MissionConditionLoader {

    private MissionConditionLoader() {
    }

    /**
     * 遠征条件 JSON を読み込み、MissionCondition に変換して返す。
     */
    static MissionCondition load(InputStream is) throws IOException {
        MissionCondition record = JsonMappers.READER_WITH_COMMENTS
                .forType(MissionCondition.class)
                .readValue(is);
        return record;
    }
}
