package logbook.internal.capture;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.compression.zstandard.ZstandardCompression;

import logbook.internal.JsonMappers;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectWriter;

/**
 * API キャプチャ専用の日次セグメント（JSONL + zstd）書き込み。
 * <p>
 * {@link ApiCaptureRecord} を {@link ApiCaptureEnvelope} に詰め、ストリームへ直書きする。
 * 日付変更、または同一 part への書き込み件数が上限以上のときは<strong>次の書き込み</strong>で part を切り替える。
 * part 件数はプロセス内メモリ上の累計で、セグメント close 後も同一 part では引き継ぐ。
 * プロセス再起動時は 0 から数え直す（ディスク上の既存行は見ない）。
 * 呼び出し側（ライター worker）から単一スレッドで使う前提。
 * </p>
 */
@Slf4j
final class ApiCaptureSegmentStore {

    /** 1 セグメントあたりのレコード件数上限。到達後の次行から回転する。 */
    static final int MAX_SEGMENT_RECORDS = 5_000;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 同一セグメントへ複数行書くため、書き込み先ストリームは閉じない */
    private static final ObjectWriter JSON_WRITER = JsonMappers.MAPPER.writer()
            .without(StreamWriteFeature.AUTO_CLOSE_TARGET);

    private final ZstandardCompression zstandard;
    private final Supplier<LocalDate> today;
    private final int maxSegmentRecords;

    private LocalDate currentDate;
    private int currentPart;
    /** 現在 part への書き込み累計（close ではリセットしない） */
    private int partRecords;
    private Path currentSegmentPath;
    private OutputStream fileOutput;
    private OutputStream compressedOutput;

    ApiCaptureSegmentStore() {
        this(LocalDate::now, MAX_SEGMENT_RECORDS);
    }

    /**
     * @param today テスト用に日付を注入する（システム既定タイムゾーンの暦日）
     */
    ApiCaptureSegmentStore(Supplier<LocalDate> today) {
        this(today, MAX_SEGMENT_RECORDS);
    }

    /**
     * @param today テスト用に日付を注入する
     * @param maxSegmentRecords セグメント回転の件数上限（テスト用に縮小可）
     */
    ApiCaptureSegmentStore(Supplier<LocalDate> today, int maxSegmentRecords) {
        this.today = Objects.requireNonNull(today);
        if (maxSegmentRecords <= 0) {
            throw new IllegalArgumentException("maxSegmentRecords must be positive");
        }
        this.maxSegmentRecords = maxSegmentRecords;
        try {
            this.zstandard = new ZstandardCompression();
            this.zstandard.start();
        } catch (Exception e) {
            throw new IllegalStateException("Zstandard の初期化に失敗しました", e);
        }
    }

    /**
     * 必要ならセグメントを開き、レコードを 1 行 JSONL として書き込む。
     *
     * @return この呼び出しで新規セグメントを開いた場合は {@code true}
     */
    boolean append(Path captureDir, ApiCaptureRecord record) throws Exception {
        Objects.requireNonNull(captureDir);
        Objects.requireNonNull(record);
        Files.createDirectories(captureDir.resolve("segments"));
        boolean opened = openIfNeeded(captureDir);
        ApiCaptureEnvelope envelope = ApiCaptureEnvelope.from(record, Instant.now());
        JSON_WRITER.writeValue(this.compressedOutput, envelope);
        this.compressedOutput.write('\n');
        this.partRecords++;
        return opened;
    }

    boolean isOpen() {
        return this.compressedOutput != null;
    }

    void flushEncoderQuietly() {
        if (this.compressedOutput == null) {
            return;
        }
        try {
            this.compressedOutput.flush();
        } catch (IOException e) {
            log.debug("APIキャプチャエンコーダの flush に失敗しました", e);
        }
    }

    void closeQuietly() {
        if (this.compressedOutput != null) {
            try {
                this.compressedOutput.close();
            } catch (IOException e) {
                log.debug("APIキャプチャセグメントのクローズに失敗しました", e);
            }
        }
        this.compressedOutput = null;
        this.fileOutput = null;
        this.currentSegmentPath = null;
        // partRecords は維持（同一 part への再 APPEND 時に件数天井へ引き継ぐ）
    }

    Path currentSegmentPath() {
        return this.currentSegmentPath;
    }

    /**
     * 日付変更、または同一 part の累計件数が上限以上ならセグメントを開き直す。
     *
     * @return 新規にセグメントを開いた場合は {@code true}
     */
    private boolean openIfNeeded(Path captureDir) throws Exception {
        LocalDate date = this.today.get();
        if (date == null) {
            date = LocalDate.now(ZoneId.systemDefault());
        }
        if (this.compressedOutput != null) {
            if (date.equals(this.currentDate)
                    && this.partRecords < this.maxSegmentRecords) {
                return false;
            }
            if (date.equals(this.currentDate)) {
                this.currentPart++;
            } else {
                this.currentPart = 0;
                this.currentDate = date;
            }
            closeQuietly();
            this.partRecords = 0;
        } else if (this.currentDate == null || !date.equals(this.currentDate)) {
            this.currentDate = date;
            this.currentPart = 0;
            this.partRecords = 0;
        }
        Path segmentsDir = captureDir.resolve("segments");
        String baseName = DATE_FORMAT.format(this.currentDate);
        if (this.currentPart <= 0) {
            this.currentPart = findLatestPart(segmentsDir, baseName);
        }
        this.currentSegmentPath = segmentsDir.resolve(partFileName(baseName, this.currentPart));
        boolean append = Files.exists(this.currentSegmentPath);
        this.fileOutput = Files.newOutputStream(
                this.currentSegmentPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        this.compressedOutput = this.zstandard.newEncoderOutputStream(
                this.fileOutput,
                this.zstandard.getDefaultEncoderConfig());
        return true;
    }

    static int findLatestPart(Path segmentsDir, String baseName) throws IOException {
        Path first = segmentsDir.resolve(baseName + ".jsonl.zst");
        if (!Files.exists(first)) {
            return 1;
        }
        int latest = 1;
        int part = 2;
        while (true) {
            Path candidate = segmentsDir.resolve(baseName + ".part" + part + ".jsonl.zst");
            if (!Files.exists(candidate)) {
                return latest;
            }
            latest = part;
            part++;
        }
    }

    static String partFileName(String baseName, int part) {
        if (part <= 1) {
            return baseName + ".jsonl.zst";
        }
        return baseName + ".part" + part + ".jsonl.zst";
    }
}
