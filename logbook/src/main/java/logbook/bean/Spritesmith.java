package logbook.bean;

import java.util.Map;

/**
 * スプライト情報（Texture Packer / Spritesmith 形式の JSON 用 DTO）。
 * 想定読み込み元: プロキシ経由で保存された
 * {@code {resourcesDir}/common/*.json}, {@code duty/*.json}, {@code sally/*.json} など。
 */
public record Spritesmith(Map<String, Frame> frames) {

    /** 1 フレーム分の情報。 */
    public record Frame(Rect frame, boolean rotated, boolean trimmed,
            Rect spriteSourceSize, Size sourceSize) {
    }

    /** 矩形（x, y, w, h）。frame および spriteSourceSize 用。 */
    public record Rect(int x, int y, int w, int h) {
    }

    /** 寸法のみ（w, h）。Texture Packer の sourceSize 用。 */
    public record Size(int w, int h) {
    }
}
