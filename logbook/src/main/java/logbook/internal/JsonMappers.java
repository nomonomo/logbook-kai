package logbook.internal;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * アプリケーション全体で共有する JsonMapper と ObjectReader の定数です。
 * Jackson3 の JSON 読み書きはここで定義した定数を使用してください。
 */
public final class JsonMappers {

    /** デフォルト設定の JsonMapper（strict: 未知プロパティで失敗）。書き込みおよび strict な読み込みに使用。 */
    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 未知プロパティを無視する ObjectReader。設定・戦闘ログなどで使用。 */
    public static final ObjectReader LENIENT_READER = MAPPER.reader()
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 未知プロパティを無視しつつ検出時にログ出力する ObjectReader。デバッグ・互換性確認用。 */
    public static final ObjectReader LENIENT_READER_WITH_UNKNOWN_LOGGING = LENIENT_READER
            .withHandler(new UnknownPropertyLoggingHandler());

    /** Java 形式コメントを許容する ObjectReader。リソース内 JSON（ships.json 等）の読み込みに使用。 */
    public static final ObjectReader READER_WITH_COMMENTS = MAPPER.reader()
            .with(JsonReadFeature.ALLOW_JAVA_COMMENTS);

    /** Creator の必須プロパティが JSON に無い場合に失敗する ObjectReader。初期設定の読み込みなどに使用。 */
    public static final ObjectReader STRICT_CREATOR_READER = MAPPER.reader()
            .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    /** STRICT_CREATOR_READER に Java 形式コメント許容を加えた ObjectReader。ships.json 等のリソース読み込みに使用。 */
    public static final ObjectReader STRICT_CREATOR_READER_WITH_COMMENTS = MAPPER.reader()
            .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .with(JsonReadFeature.ALLOW_JAVA_COMMENTS);

    private JsonMappers() {
    }
}
