package logbook.internal.capture;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * API キャプチャ対象ルールをプロパティから読み取る。
 * <p>
 * classpath の {@link #CLASSPATH_RESOURCE} のみを読む。
 * 配布ビルドは空ルール、開発ビルド（{@code -Pdev}）は
 * {@code dev/api-capture-rules.properties} を同梱する。
 * </p>
 */
@Slf4j
final class ApiCaptureRulesLoader {

    /** 配布 / 開発ビルドが同梱する classpath 上のリソース */
    static final String CLASSPATH_RESOURCE = "/logbook/capture/api-capture-rules.properties";

    private static final Pattern PREFIX_KEY = Pattern.compile("^prefix\\.(\\d+)$");

    private ApiCaptureRulesLoader() {
    }

    /**
     * classpath からルール一覧を返す（空でもよい）。
     */
    static List<ApiCaptureTargetRule> load() {
        try (InputStream in = ApiCaptureRulesLoader.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                log.warn("APIキャプチャルールが classpath にありません: {}", CLASSPATH_RESOURCE);
                return List.of();
            }
            List<ApiCaptureTargetRule> rules = parse(in);
            if (rules.isEmpty()) {
                log.debug("APIキャプチャルールは空です（配布ビルドまたは未設定）");
            } else {
                log.info("APIキャプチャルールを classpath から読み込みました: rules={}", rules.size());
            }
            return rules;
        } catch (IOException e) {
            log.warn("APIキャプチャルールの読み込みに失敗しました", e);
            return List.of();
        }
    }

    /**
     * プロパティから {@code prefix.<n>} を昇順で読み、ルール一覧にする。
     */
    static List<ApiCaptureTargetRule> parse(InputStream in) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return parse(properties);
    }

    static List<ApiCaptureTargetRule> parse(Properties properties) {
        List<Map.Entry<Integer, String>> entries = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            Matcher matcher = PREFIX_KEY.matcher(name);
            if (matcher.matches()) {
                String value = properties.getProperty(name);
                if (value == null || value.isBlank()) {
                    log.warn("APIキャプチャルールが空です: {}", name);
                    continue;
                }
                entries.add(Map.entry(Integer.parseInt(matcher.group(1)), value.trim()));
                continue;
            }
            log.warn("未知のAPIキャプチャルールキーを無視します: {}", name);
        }
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        List<ApiCaptureTargetRule> rules = new ArrayList<>(entries.size());
        for (Map.Entry<Integer, String> entry : entries) {
            try {
                rules.add(ApiCaptureTargetRule.prefix(entry.getValue()));
            } catch (IllegalArgumentException e) {
                log.warn("不正なAPIキャプチャ prefix を無視します: prefix.{}={}",
                        entry.getKey(), entry.getValue(), e);
            }
        }
        return List.copyOf(rules);
    }
}
