package logbook.map;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * KC3 の edges.json から {@code data/map/mapping.json} を生成する手動実行ツール。
 * <p>
 * 改行は LF 固定（SHA-256 / {@code .gitattributes} と一致させる）。
 * </p>
 */
public class MappingGenerator {
    /** Source URL, thanks to KC3 */
    private static final String SOURCE_URL = "https://raw.githubusercontent.com/KC3Kai/KC3Kai/master/src/data/edges.json";
    /** Prefix of each key representing map area */
    private static final String KEY_PREFIX = "World ";

    /** logbook モジュールから実行したときの出力先 */
    private static final Path OUTPUT = Path.of("../data/map/mapping.json");

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws JacksonException, MalformedURLException, IOException {
        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Map<String, List<String>>> json;
        try (InputStream in = URI.create(SOURCE_URL).toURL().openStream()) {
            json = mapper.readValue(in, Map.class);
        }
        Files.createDirectories(OUTPUT.getParent());
        String body = json.keySet().stream()
                .filter(key -> key.startsWith(KEY_PREFIX))
                .flatMap(key -> {
                    String area = key.substring(KEY_PREFIX.length());
                    Map<String, List<String>> cells = json.get(key);
                    return cells.entrySet().stream()
                            .filter(cell -> cell.getValue().size() == 2 && !"Start".equals(cell.getValue().get(1)))
                            .map(cell -> "    \"" + area + "-" + cell.getKey() + "\": \""
                                    + cell.getValue().get(1) + "\"");
                })
                .collect(Collectors.joining(",\n"));
        try (BufferedWriter out = Files.newBufferedWriter(OUTPUT, StandardCharsets.UTF_8)) {
            out.write("{\n");
            out.write(body);
            out.write("\n}\n");
        }
        System.out.println("wrote " + OUTPUT.toAbsolutePath().normalize());
    }
}
