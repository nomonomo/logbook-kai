package logbook.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * ゲームデータ（data/）の pack / verify ツール。
 *
 * <pre>
 * mvn -pl logbook-data -am process-classes
 * mvn -pl logbook-data,logbook -am verify -Pgamedata-update
 * </pre>
 */
public final class GameDataTool {

    private static final Pattern QUEST_FILE = Pattern.compile("(\\d+)\\.json");

    private static final List<String> DELIVERABLE_FILES = List.of(
            "map/mapping.json",
            "seaarea/seaarea.json",
            "quest/quests.json");

    /** 書き込み・ノード生成用（オプションは ObjectReader / ObjectWriter 側） */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Java 形式コメントを許容する JsonNode 読み込み */
    private static final ObjectReader READER_WITH_COMMENTS = MAPPER.readerFor(JsonNode.class)
            .with(JsonReadFeature.ALLOW_JAVA_COMMENTS);

    /** Pretty 出力用（manifest など人が読む生成物） */
    private static final ObjectWriter PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter();

    /** 配信用バンドル（quests.json）は空白なしで書き出す */
    private static final ObjectWriter COMPACT_WRITER = MAPPER.writer();

    private final Path dataDir;
    private final boolean updateManifestSha;

    private GameDataTool(Path dataDir, boolean updateManifestSha) {
        this.dataDir = dataDir;
        this.updateManifestSha = updateManifestSha;
    }

    public static void main(String[] args) throws Exception {
        String command = args.length > 0 ? args[0] : "help";
        Path dataDir = resolveDataDir(args);
        boolean updateSha = hasFlag(args, "--update-sha");
        GameDataTool tool = new GameDataTool(dataDir, updateSha);
        switch (command) {
        case "pack" -> tool.pack();
        case "verify" -> {
            if (!tool.verify()) {
                System.exit(1);
            }
        }
        case "validate" -> {
            if (!tool.validateAll()) {
                System.exit(1);
            }
        }
        default -> {
            System.err.println("Usage: GameDataTool <pack|verify|validate> [--data-dir <path>] [--update-sha]");
            System.exit(2);
        }
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveDataDir(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--data-dir".equals(args[i])) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
        }
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        // logbook-data モジュールから実行した場合: ../data
        Path fromModule = userDir.resolve("../data").normalize();
        if (Files.isDirectory(fromModule.resolve("quest")) || Files.isDirectory(fromModule.resolve("map"))) {
            return fromModule;
        }
        Path named = userDir.resolve("data");
        if (Files.isDirectory(named)) {
            return named;
        }
        return fromModule;
    }

    private void pack() throws Exception {
        System.out.println("dataDir=" + dataDir);
        if (!validateAll()) {
            throw new IllegalStateException("JSON 検証に失敗したため pack を中止します");
        }
        ObjectNode quests = buildQuestsObject();
        Path questsPath = dataDir.resolve("quest/quests.json");
        Files.createDirectories(questsPath.getParent());
        COMPACT_WRITER.writeValue(questsPath, quests);
        System.out.println("wrote " + questsPath + " (" + quests.size() + " quests)");

        if (this.updateManifestSha) {
            updateManifest();
        } else {
            System.out.println("skipped manifest update (pass --update-sha, or use -Pgamedata-update)");
        }
    }

    private boolean verify() throws Exception {
        System.out.println("dataDir=" + dataDir);
        boolean ok = validateAll();

        ObjectNode expected = buildQuestsObject();
        Path questsPath = dataDir.resolve("quest/quests.json");
        if (!Files.isRegularFile(questsPath)) {
            System.err.println("missing: quest/quests.json （pack を実行してください）");
            ok = false;
        } else {
            JsonNode actual = MAPPER.readTree(questsPath);
            if (!expected.equals(actual)) {
                System.err.println("quest/quests.json が src/ と一致しません。pack を実行してください");
                ok = false;
            } else {
                System.out.println("quest/quests.json OK (" + expected.size() + " quests)");
            }
        }

        ok &= verifyManifest();
        if (ok) {
            System.out.println("verify OK");
        }
        return ok;
    }

    private boolean validateAll() {
        boolean ok = true;
        try (Stream<Path> walk = Files.walk(dataDir)) {
            List<Path> jsonFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
            for (Path path : jsonFiles) {
                try {
                    readJsonAllowingComments(path);
                } catch (Exception e) {
                    System.err.println("Invalid JSON: " + relativize(path) + " : " + e.getMessage());
                    ok = false;
                }
            }
            System.out.println("validated " + jsonFiles.size() + " json files");
        } catch (IOException e) {
            System.err.println("walk failed: " + e.getMessage());
            return false;
        }
        return ok;
    }

    private ObjectNode buildQuestsObject() throws IOException {
        record QuestSource(Path path, long questNo) {
        }

        Path srcDir = dataDir.resolve("quest/src");
        if (!Files.isDirectory(srcDir)) {
            throw new IOException("quest/src がありません: " + srcDir);
        }
        ObjectNode root = MAPPER.createObjectNode();
        try (Stream<Path> list = Files.list(srcDir)) {
            List<QuestSource> files = list
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        var matcher = QUEST_FILE.matcher(path.getFileName().toString());
                        if (!matcher.matches()) {
                            return null;
                        }
                        return new QuestSource(path, Long.parseLong(matcher.group(1)));
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(QuestSource::questNo))
                    .collect(Collectors.toList());
            for (QuestSource file : files) {
                JsonNode node = readJsonAllowingComments(file.path());
                root.set(Long.toString(file.questNo()), node);
            }
        }
        if (root.isEmpty()) {
            throw new IOException("quest/src に任務 JSON がありません");
        }
        return root;
    }

    /**
     * 配信ファイルの SHA を再計算し、変化があれば {@code version} を +1 して manifest を書き出す。
     * 変化がなければ（かつ不要フィールドもなければ）ファイルを触らない。
     */
    private void updateManifest() throws Exception {
        Path manifestPath = dataDir.resolve("manifest.json");
        ObjectNode manifest;
        boolean created = false;
        if (Files.isRegularFile(manifestPath)) {
            manifest = (ObjectNode) MAPPER.readTree(manifestPath);
        } else {
            manifest = MAPPER.createObjectNode();
            created = true;
        }

        ObjectNode newFiles = MAPPER.createObjectNode();
        for (String relative : DELIVERABLE_FILES) {
            Path file = dataDir.resolve(relative);
            if (!Files.isRegularFile(file)) {
                throw new IOException("配信ファイルがありません: " + relative);
            }
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("sha256", sha256Hex(file));
            newFiles.set(relative, entry);
        }

        JsonNode oldFiles = manifest.get("files");
        boolean contentChanged = !deliverableFilesEqual(oldFiles, newFiles);
        long oldVersion = manifest.path("version").asLong(0);
        long newVersion;
        if (contentChanged) {
            newVersion = oldVersion < 1 ? 1 : oldVersion + 1;
        } else {
            newVersion = oldVersion < 1 ? 1 : oldVersion;
        }

        boolean removedUpdatedAt = manifest.remove("updatedAt") != null;
        boolean needFormatVersion = !manifest.has("formatVersion");
        boolean needWrite = created || contentChanged || removedUpdatedAt || needFormatVersion
                || oldVersion != newVersion || !manifest.hasNonNull("version");

        if (!needWrite) {
            System.out.println("manifest.json unchanged (version=" + newVersion + ", sha match)");
            return;
        }

        if (contentChanged) {
            System.out.println("manifest version: " + oldVersion + " -> " + newVersion
                    + " (deliverables changed)");
        } else if (oldVersion != newVersion) {
            System.out.println("manifest version: " + oldVersion + " -> " + newVersion);
        }
        if (removedUpdatedAt) {
            System.out.println("removed obsolete field: updatedAt");
        }

        manifest.put("version", newVersion);
        if (needFormatVersion) {
            manifest.put("formatVersion", 1);
        }
        manifest.set("files", newFiles);
        PRETTY_WRITER.writeValue(manifestPath, manifest);
        System.out.println("updated " + manifestPath);
    }

    /**
     * 配信対象のパス集合と各 sha256 が一致するか（大文字小文字無視）。
     * 旧キーが残っている場合は不一致とする。
     */
    private static boolean deliverableFilesEqual(JsonNode oldFiles, ObjectNode newFiles) {
        if (oldFiles == null || !oldFiles.isObject()) {
            return false;
        }
        for (String relative : DELIVERABLE_FILES) {
            String oldSha = oldFiles.path(relative).path("sha256").asString("");
            String newSha = newFiles.path(relative).path("sha256").asString("");
            if (oldSha.isEmpty() || !oldSha.equalsIgnoreCase(newSha)) {
                return false;
            }
        }
        int count = 0;
        Iterator<String> names = oldFiles.propertyNames().iterator();
        while (names.hasNext()) {
            String name = names.next();
            count++;
            if (!DELIVERABLE_FILES.contains(name)) {
                return false;
            }
        }
        return count == DELIVERABLE_FILES.size();
    }

    private boolean verifyManifest() throws Exception {
        Path manifestPath = dataDir.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            System.err.println("missing: manifest.json");
            return false;
        }
        JsonNode manifest = MAPPER.readTree(manifestPath);
        JsonNode files = manifest.get("files");
        if (files == null || !files.isObject()) {
            System.err.println("manifest.files が不正です");
            return false;
        }
        boolean ok = true;
        for (String relative : DELIVERABLE_FILES) {
            if (!files.has(relative)) {
                System.err.println("manifest に不足: " + relative);
                ok = false;
                continue;
            }
            Path file = dataDir.resolve(relative);
            if (!Files.isRegularFile(file)) {
                System.err.println("ファイル不足: " + relative);
                ok = false;
                continue;
            }
            String expected = files.get(relative).path("sha256").asString("");
            String actual = sha256Hex(file);
            if (!expected.equalsIgnoreCase(actual)) {
                System.err.println("sha256 不一致: " + relative);
                ok = false;
            }
        }
        Iterator<String> fieldNames = files.propertyNames().iterator();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            if (name.startsWith("quest/") && name.endsWith(".json") && !name.equals("quest/quests.json")) {
                System.err.println("旧形式の manifest エントリが残っています: " + name);
                ok = false;
            }
        }
        if (ok) {
            System.out.println("manifest sha256 OK");
        }
        return ok;
    }

    private JsonNode readJsonAllowingComments(Path path) throws IOException {
        // ObjectReader に readTree(Path) は無く、型は readerFor で固定する
        return READER_WITH_COMMENTS.readValue(path);
    }

    private String relativize(Path path) {
        try {
            return dataDir.relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString();
        }
    }

    private static String sha256Hex(Path path) throws Exception {
        return sha256Hex(Files.readAllBytes(path));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(normalizeNewlinesToLf(data)));
    }

    /**
     * 改行を LF に揃える。Windows で CRLF 保存されても、Git / GitHub raw（LF）と同じ SHA になるようにする。
     */
    static byte[] normalizeNewlinesToLf(byte[] data) {
        if (data == null || data.length == 0) {
            return data == null ? new byte[0] : data;
        }
        boolean hasCr = false;
        for (byte b : data) {
            if (b == '\r') {
                hasCr = true;
                break;
            }
        }
        if (!hasCr) {
            return data;
        }
        byte[] out = new byte[data.length];
        int j = 0;
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b == '\r') {
                if (i + 1 < data.length && data[i + 1] == '\n') {
                    i++;
                }
                out[j++] = '\n';
            } else {
                out[j++] = b;
            }
        }
        if (j == data.length) {
            return out;
        }
        byte[] trimmed = new byte[j];
        System.arraycopy(out, 0, trimmed, 0, j);
        return trimmed;
    }
}
