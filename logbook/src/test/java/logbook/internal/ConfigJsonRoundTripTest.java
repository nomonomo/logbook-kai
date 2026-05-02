package logbook.internal;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * {@link Config} が読み書きする設定 JSON（ファイル名 = Bean の canonical 名 + ".json"）が、
 * {@link JsonMappers#LENIENT_READER} と {@link JsonMappers#MAPPER} のラウンドトリップで内容を保つことを検証する。
 */
public class ConfigJsonRoundTripTest {

    @EnabledIfSystemProperty(named = "test.profile", matches = "dev")
    @TestFactory
    Stream<DynamicTest> testConfigJsonRoundTrip() throws IOException, URISyntaxException {
        URL resource = ConfigJsonRoundTripTest.class.getClassLoader().getResource("logbook/config");
        if (resource == null) {
            throw new IllegalStateException("テストリソース logbook/config が見つかりません");
        }
        Path root = Paths.get(resource.toURI());
        List<Path> jsonPaths;
        try (Stream<Path> walk = Files.walk(root)) {
            jsonPaths = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }
        ClassLoader loader = ConfigJsonRoundTripTest.class.getClassLoader();
        return jsonPaths.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
            try {
                String fileName = path.getFileName().toString();
                String canonical = fileName.substring(0, fileName.length() - ".json".length());
                Class<?> clazz = Class.forName(canonical, false, loader);
                Object instance = JsonMappers.LENIENT_READER.forType(clazz).readValue(path);
                String roundTripJson = JsonMappers.MAPPER.writeValueAsString(instance);
                String originalJson = Files.readString(path, StandardCharsets.UTF_8);
                assertThatJson(roundTripJson).isEqualTo(originalJson);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("設定 JSON に対応するクラスが見つかりません: " + path, e);
            } catch (IOException e) {
                throw new UncheckedIOException("設定 JSON のラウンドトリップに失敗: " + path, e);
            }
        }));
    }
}
