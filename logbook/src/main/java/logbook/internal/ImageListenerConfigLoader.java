package logbook.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ImageListener} 用プロパティを classpath から読む。
 * <p>
 * 配布ビルドは従来 3 category。開発ビルド（{@code -Pdev}）は
 * {@code dev/image-listener.properties} を同梱する。
 * </p>
 * <p>
 * キーは {@code img.category.<n>}（{@code n} は非負整数）。
 * <b>番号の欠番は許容</b>し、昇順に並べて category 一覧とする。
 * 同一番号（例: {@code img.category.1} と {@code img.category.01}）は重複として警告し、
 * 先に読み込んだ方を残す。
 * </p>
 */
@Slf4j
final class ImageListenerConfigLoader {

    static final String CLASSPATH_RESOURCE = "/logbook/image/image-listener.properties";

    private static final Pattern CATEGORY_KEY = Pattern.compile("^img\\.category\\.(\\d+)$");

    private ImageListenerConfigLoader() {
    }

    static ImageListenerConfig load() {
        try (InputStream in = ImageListenerConfigLoader.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                log.warn("ImageListener 設定が classpath にありません: {}", CLASSPATH_RESOURCE);
                return ImageListenerConfig.empty();
            }
            ImageListenerConfig config = parse(in);
            log.info("ImageListener 設定を読み込みました: categories={}", config.imgCategories().size());
            return config;
        } catch (IOException e) {
            log.warn("ImageListener 設定の読み込みに失敗しました", e);
            return ImageListenerConfig.empty();
        }
    }

    static ImageListenerConfig parse(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return parse(properties);
    }

    static ImageListenerConfig parse(Properties properties) {
        // TreeMap: 番号昇順。欠番があってもその順で一覧化する
        Map<Integer, String> byIndex = new TreeMap<>();
        List<String> keyNames = new ArrayList<>(properties.stringPropertyNames());
        keyNames.sort(String::compareTo);
        for (String name : keyNames) {
            Matcher matcher = CATEGORY_KEY.matcher(name);
            if (!matcher.matches()) {
                log.warn("未知の ImageListener 設定キーを無視します: {}", name);
                continue;
            }
            String value = properties.getProperty(name);
            if (value == null || value.isBlank()) {
                log.warn("ImageListener category が空です: {}", name);
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("ImageListener category の番号が不正です: {}", name);
                continue;
            }
            String trimmed = value.trim();
            String existing = byIndex.putIfAbsent(index, trimmed);
            if (existing != null) {
                log.warn(
                        "ImageListener category の番号が重複しています: {}={} を無視します（img.category.{}={} を保持）",
                        name, trimmed, index, existing);
            }
        }
        return new ImageListenerConfig(List.copyOf(byIndex.values()));
    }
}
