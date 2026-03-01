package logbook.internal;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import logbook.bean.ShipSupplementalInfo;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 艦娘付加情報 JSON の読み込み。Ships の static 初期化およびテストから利用する。
 */
@Slf4j
final class ShipSupplementalLoader {

    private ShipSupplementalLoader() {
    }

    /**
     * 艦娘付加情報 JSON を読み込み、ID をキーとした Map を返す。
     */
    static Map<Integer, ShipSupplementalInfo> loadSupplementalMap(InputStream is) {
        try {
            Map<String, List<ShipSupplementalInfo>> json = JsonMappers.STRICT_CREATOR_READER_WITH_COMMENTS
                    .forType(new TypeReference<Map<String, List<ShipSupplementalInfo>>>() {})
                    .readValue(is);
            return Optional.ofNullable(json.get("ships"))
                    .map(list -> list.stream().collect(Collectors.toMap(ShipSupplementalInfo::id, s -> s)))
                    .orElse(Collections.emptyMap());
        } catch (Exception e) {
            log.error("艦娘付加情報の初期化に失敗しました", e);
            return Collections.emptyMap();
        }
    }
}
