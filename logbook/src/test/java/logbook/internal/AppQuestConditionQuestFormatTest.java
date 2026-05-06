package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import logbook.bean.AppQuestCondition;

/**
 * src/main/resources/logbook/quest 以下の全 JSON が AppQuestCondition として読み込めるか検証する。
 * フォーマット（デシリアライズ可否）のチェックが目的。必須項目チェックは行わない。
 */
@Execution(CONCURRENT)
class AppQuestConditionQuestFormatTest {

    private static final Path QUEST_DIR = Paths.get("src/main/resources/logbook/quest");

    static Stream<Path> questJsonPaths() throws Exception {
        if (!Files.isDirectory(QUEST_DIR)) {
            return Stream.empty();
        }
        List<Path> paths;
        try (Stream<Path> list = Files.list(QUEST_DIR)) {
            paths = list
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        return paths.stream();
    }

    @ParameterizedTest(name = "quest: {0}")
    @MethodSource("questJsonPaths")
    void eachQuestJson_loadsAsAppQuestCondition(Path path) throws Exception {
        AppQuestCondition condition = AppQuestConditionLoader.load(path);
        assertNotNull(condition, "読み込み結果が null でないこと（JSON フォーマットが正しくデシリアライズできること）: " + path.getFileName());
    }
}
