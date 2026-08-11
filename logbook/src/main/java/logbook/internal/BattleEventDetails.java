package logbook.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import logbook.bean.AppConfig;
import logbook.bean.BattleEventDetail;
import lombok.extern.slf4j.Slf4j;

/**
 * 戦闘イベント詳細JSONの読み書き。
 * ファイルは戦闘ログと同じディレクトリへ {@code .event.json(.gz)} サフィックスで保存する。
 */
@Slf4j
public final class BattleEventDetails {

    private BattleEventDetails() {
    }

    /**
     * 戦闘ログディレクトリへ詳細を書き込む。
     *
     * @param detail 詳細
     */
    public static void write(BattleEventDetail detail) {
        try {
            Path path = writePath(detail.getTime());
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (AppConfig.get().isCompressBattleLogs()) {
                OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
                JsonMappers.MAPPER.writeValue(out, detail);
            } else {
                try (OutputStream out = Files.newOutputStream(path)) {
                    JsonMappers.MAPPER.writeValue(out, detail);
                }
            }
        } catch (Exception e) {
            log.warn("戦闘イベント詳細の書き込み中に例外", e);
        }
    }

    /**
     * 日時に対応する詳細を読み込む。
     *
     * @param dateString CSVの日付
     * @return 詳細。存在しない場合はnull
     */
    public static BattleEventDetail read(String dateString) {
        try {
            for (Path path : tryReadPaths(dateString)) {
                if (Files.isReadable(path)) {
                    try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(path))) {
                        fileIn.mark(1024);
                        int header = (fileIn.read() | (fileIn.read() << 8));
                        fileIn.reset();
                        if (header == GZIPInputStream.GZIP_MAGIC) {
                            try (InputStream in = new GZIPInputStream(fileIn)) {
                                return JsonMappers.LENIENT_READER_WITH_UNKNOWN_LOGGING
                                        .forType(BattleEventDetail.class)
                                        .readValue(in);
                            }
                        }
                        return JsonMappers.LENIENT_READER_WITH_UNKNOWN_LOGGING
                                .forType(BattleEventDetail.class)
                                .readValue(fileIn);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("戦闘イベント詳細の読み込み中に例外", e);
        }
        return null;
    }

    private static Path baseDir() {
        return Paths.get(AppConfig.get().getBattleLogDir());
    }

    private static Path writePath(String dateString) {
        String name = dateString.replace(':', '-');
        String ext = AppConfig.get().isCompressBattleLogs() ? ".event.json.gz" : ".event.json";
        return baseDir().resolve(Paths.get(name.substring(0, 7), name + ext));
    }

    private static List<Path> tryReadPaths(String dateString) {
        Path dir = baseDir();
        String name = dateString.replace(':', '-');
        String yearMonth = name.substring(0, 7);
        return List.of(
                dir.resolve(Paths.get(yearMonth, name + ".event.json")),
                dir.resolve(Paths.get(yearMonth, name + ".event.json.gz")));
    }
}
