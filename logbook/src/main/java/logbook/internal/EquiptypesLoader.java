package logbook.internal;

import java.io.InputStream;
import java.util.Collections;

import logbook.bean.Equiptypes;
import lombok.extern.slf4j.Slf4j;

/**
 * 装備カテゴリ情報 JSON の読み込み。Items.getCategories() およびテストから利用する。
 */
@Slf4j
final class EquiptypesLoader {

    private EquiptypesLoader() {
    }

    /**
     * 装備カテゴリ情報 JSON を読み込み、Equiptypes を返す。失敗時は空の categories で返す。
     */
    static Equiptypes load(InputStream is) {
        try {
            return JsonMappers.STRICT_CREATOR_READER_WITH_COMMENTS
                    .forType(Equiptypes.class)
                    .readValue(is);
        } catch (Exception e) {
            log.error("装備のカテゴリ情報の読み込みに失敗しました", e);
            return new Equiptypes(Collections.emptyList());
        }
    }
}
