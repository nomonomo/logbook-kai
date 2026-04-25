package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import logbook.internal.BattleLogs.SimpleBattleLog;

public class BattleLogsTest {
    @Test
    public void testCsvLine() {
        String line = "2020-09-18 13:32:40,7-1 ブルネイ泊地沖,4,出撃,S,Ｔ字戦不利,単横陣,単横陣,制空権確保,,,深海潜水艦隊 II群,,,Fletcher Mk.II(Lv148),40/43,夕張改二特(Lv149),47/47,Janus改(Lv99),31/31,Johnston改(Lv99),34/34,朝潮改二丁(Lv99),29/34,,,,,,,,,,,,,,,潜水ソ級(elite),45/45,潜水カ級,19/19,潜水カ級,19/19,潜水カ級,19/19,,,,,,,,,,,,,,,,,,1170,140";
        SimpleBattleLog log = new SimpleBattleLog(line);
        assertEquals(1170, Integer.parseInt(log.getShipExp()));
        line = "2020-09-18 12:16:27,7-3 ペナン島沖,4,,S,同航戦,複縦陣,単縦陣,制空権確保,,,深海5,500t級軽巡洋艦,,,羽黒改二(Lv99),57/57,阿武隈改二(Lv172),51/51,日進甲(Lv98),49/49,神風改(Lv97),23/23,,,,,,,,,,,,,,,,,軽巡ホ級(flagship),53/53,駆逐イ級,20/20,駆逐イ級,20/20,,,,,,,,,,,,,,,,,,,,660,160";
        log = new SimpleBattleLog(line);
        assertEquals(660, Integer.parseInt(log.getShipExp()));
        assertEquals("深海5,500t級軽巡洋艦", log.getEfleet());
        line = "2020-09-18 12:16:27,7-3 ペナン島沖,4,,S,同航戦,複縦陣,単縦陣,制空権確保,,,\"深海5,500t級軽巡洋艦\",,,羽黒改二(Lv99),57/57,阿武隈改二(Lv172),51/51,日進甲(Lv98),49/49,神風改(Lv97),23/23,,,,,,,,,,,,,,,,,軽巡ホ級(flagship),53/53,駆逐イ級,20/20,駆逐イ級,20/20,,,,,,,,,,,,,,,,,,,,660,160";
        log = new SimpleBattleLog(line);
        assertEquals(660, Integer.parseInt(log.getShipExp()));
        assertEquals("深海5,500t級軽巡洋艦", log.getEfleet());
        assertEquals("4", log.getCell());
        assertEquals("制空権確保", log.getDispseiku());
        line = "2020-09-18 12:16:27,7-3 ペナン島沖,4,,S,同航戦,複縦陣,単縦陣,制空権確保,,,\"深海5,500t級軽巡洋\"\"艦\",,,羽黒改二(Lv99),57/57,阿武隈改二(Lv172),51/51,日進甲(Lv98),49/49,神風改(Lv97),23/23,,,,,,,,,,,,,,,,,軽巡ホ級(flagship),53/53,駆逐イ級,20/20,駆逐イ級,20/20,,,,,,,,,,,,,,,,,,,,660,160";
        log = new SimpleBattleLog(line);
        assertEquals(660, Integer.parseInt(log.getShipExp()));
        assertEquals("深海5,500t級軽巡洋\"艦", log.getEfleet());
        line = "2020-09-18 12:16:27,7-3 ペナン島沖,4,,S,同航戦,複縦陣,単縦陣,制空権確保,,,\"深海5,500t級軽巡洋\"\"\"\",\"\"艦\",,,羽黒改二(Lv99),57/57,阿武隈改二(Lv172),51/51,日進甲(Lv98),49/49,神風改(Lv97),23/23,,,,,,,,,,,,,,,,,軽巡ホ級(flagship),53/53,駆逐イ級,20/20,駆逐イ級,20/20,,,,,,,,,,,,,,,,,,,,660,160";
        log = new SimpleBattleLog(line);
        assertEquals(660, Integer.parseInt(log.getShipExp()));
        assertEquals("深海5,500t級軽巡洋\"\",\"艦", log.getEfleet());
        line = "2020-09-18 12:16:27,7-3 ペナン島沖,4,,S,同航戦,複縦陣,単縦陣,制空権確保,,,\"深海5,500t級軽巡洋\"\"\"\",\"\"艦\",,,羽黒改二(Lv99),57/57,阿武隈改二(Lv172),51/51,日進甲(Lv98),49/49,神風改(Lv97),23/23,,,,,,,,,,,,,,,,,軽巡ホ級(flagship),53/53,駆逐イ級,20/20,駆逐イ級,20/20,,,,,,,,,,,,,,,,,,,,660,";
        log = new SimpleBattleLog(line);
        assertEquals(660, Integer.parseInt(log.getShipExp()));
        assertEquals("深海5,500t級軽巡洋\"\",\"艦", log.getEfleet());
    }

    @TestFactory
    Stream<DynamicTest> testBattleLogJsonRoundTrip() throws IOException, URISyntaxException {
        URL resource = BattleLogsTest.class.getClassLoader().getResource("logbook/battlelog");
        if (resource == null) {
            throw new IllegalStateException("テストリソース logbook/battlelog が見つかりません");
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
        return jsonPaths.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
            try (InputStream in = Files.newInputStream(path);
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                BattleLogs.toJson(BattleLogs.fromJson(in), out);
                String roundTripJson = out.toString(StandardCharsets.UTF_8);
                String normalizedOriginalJson = normalizeJson(path);
                assertThatJson(roundTripJson).isEqualTo(normalizedOriginalJson);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    private static String normalizeJson(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
