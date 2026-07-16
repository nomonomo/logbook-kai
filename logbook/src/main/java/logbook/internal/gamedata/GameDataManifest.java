package logbook.internal.gamedata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * ゲームデータ配信用マニフェスト。
 */
@Data
public class GameDataManifest {

    /**
     * データ版（アプリ版とは独立の単調増加連番）。
     * 比較・新旧判定に用いる。1 以上が有効。
     */
    private Long version = 0L;

    /** フォーマット版（破壊的変更時に上げる。未設定時は 1 扱い） */
    private Integer formatVersion;

    /** 相対パス → ファイルメタデータ */
    private Map<String, FileEntry> files = new LinkedHashMap<>();

    /**
     * ファイル単位のメタデータ。
     */
    @Data
    public static class FileEntry {
        /** SHA-256（小文字 hex）。未設定可 */
        private String sha256;
    }

    /**
     * 有効なデータ版を返します。
     *
     * @return 1 以上の版。未設定・不正時は 0
     */
    public long effectiveVersion() {
        if (this.version == null || this.version <= 0L) {
            return 0L;
        }
        return this.version;
    }

    /**
     * ファイル一覧を空でない Map として返します。
     *
     * @return ファイル一覧
     */
    public Map<String, FileEntry> safeFiles() {
        if (this.files == null) {
            return Collections.emptyMap();
        }
        return this.files;
    }
}
