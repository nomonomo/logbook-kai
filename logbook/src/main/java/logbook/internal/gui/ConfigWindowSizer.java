package logbook.internal.gui;

/**
 * 設定ウィンドウの初期サイズ。文字サイズに応じて下限・希望サイズを決め、画面内に収める。
 */
final class ConfigWindowSizer {

    static final double BASE_MIN_WIDTH = 640;
    static final double BASE_MIN_HEIGHT = 560;
    static final double BASE_PREF_WIDTH = 700;
    static final double BASE_PREF_HEIGHT = 660;

    private ConfigWindowSizer() {
    }

    /**
     * 現在サイズと希望サイズの大きい方を下限以上・画面内に収める。小さくはしない。
     *
     * @param current 現在の幅または高さ（未設定なら 0 以下）
     * @param desired 希望サイズ
     * @param min 下限
     * @param max 画面の上限（0 以下なら上限なし）
     * @return 採用するサイズ
     */
    static double growWithin(double current, double desired, double min, double max) {
        double size = Math.max(min, Math.max(positive(current), desired));
        if (max > 0) {
            size = Math.min(size, max);
        }
        return size;
    }

    /**
     * ウィンドウが可視領域からはみ出さないよう座標をずらす。
     *
     * @param pos 現在の X または Y
     * @param size 幅または高さ
     * @param visMin 可視領域の最小座標
     * @param visMax 可視領域の最大座標
     * @return 収まる座標
     */
    static double clampPosition(double pos, double size, double visMin, double visMax) {
        double visSize = visMax - visMin;
        if (size >= visSize) {
            return visMin;
        }
        if (pos < visMin) {
            return visMin;
        }
        if (pos + size > visMax) {
            return visMax - size;
        }
        return pos;
    }

    static double positive(double value) {
        return (Double.isNaN(value) || value <= 0) ? 0 : value;
    }
}
