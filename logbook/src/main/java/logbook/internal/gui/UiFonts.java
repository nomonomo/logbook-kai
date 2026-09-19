package logbook.internal.gui;

import java.util.List;

import javafx.scene.text.Font;

/**
 * UI で使うフォントファミリーと文字サイズの解決。
 */
public final class UiFonts {

    private static final String JAPANESE_SAMPLE = "あ漢艦";

    private UiFonts() {
    }

    /**
     * OS ごとの既定フォントファミリーを返す。
     *
     * @return 既定ファミリー名
     */
    public static String defaultFamily() {
        return defaultFamily(System.getProperty("os.name"));
    }

    /**
     * OS 名に応じた既定フォントファミリーを返す。
     *
     * @param osName {@code os.name} 相当
     * @return 既定ファミリー名
     */
    static String defaultFamily(String osName) {
        if (osName != null && osName.toLowerCase().startsWith("mac")) {
            return "Hiragino Maru Gothic ProN";
        }
        return "Yu Gothic UI";
    }

    /**
     * 設定コンボの未指定項目の表示名。
     *
     * @return {@code 既定（ファミリー名）}
     */
    public static String defaultChoiceLabel() {
        return "既定（" + defaultFamily() + "）";
    }

    /**
     * 設定値を保存用のファミリー名にする。未指定は空文字。
     *
     * @param selected コンボの値または手入力
     * @return 保存するファミリー名。未指定は {@code ""}
     */
    public static String storedFamily(String selected) {
        if (selected == null) {
            return "";
        }
        String trimmed = selected.trim();
        if (trimmed.isEmpty() || trimmed.equals(defaultChoiceLabel())) {
            return "";
        }
        return trimmed;
    }

    /**
     * 設定値を実際に適用するファミリー名にする。
     *
     * @param configured 設定のファミリー名
     * @return 空なら既定。引用符は除く
     */
    public static String resolveFamily(String configured) {
        String stored = storedFamily(configured).replace("\"", "");
        if (stored.isEmpty()) {
            return defaultFamily();
        }
        return stored;
    }

    /**
     * 文字サイズ設定の em 倍率。標準は 1.0。
     *
     * @param fontSize AppConfig の fontSize
     * @return 標準 1.0、少し大きい 1.2、大きい 1.3
     */
    static double sizeFactor(String fontSize) {
        if ("large2".equals(fontSize)) {
            return 1.3;
        }
        if ("large1".equals(fontSize)) {
            return 1.2;
        }
        return 1.0;
    }

    /**
     * シーンルートへ載せる inline style。標準は font-size を書かない。
     *
     * @param configuredFamily 設定のファミリー名
     * @param fontSize AppConfig の fontSize
     * @return {@code -fx-font-family} と、必要なとき {@code -fx-font-size}
     */
    static String rootStyle(String configuredFamily, String fontSize) {
        String family = resolveFamily(configuredFamily);
        double factor = sizeFactor(fontSize);
        if (factor == 1.0) {
            return "-fx-font-family: \"" + family + "\";";
        }
        return "-fx-font-family: \"" + family + "\"; -fx-font-size: " + factor + "em;";
    }

    /**
     * 指定ファミリーが日本語を描画できるか。
     * AWT が要求名を論理フォントへ代入した場合は不可とみなす。
     *
     * @param family JavaFX のファミリー名
     * @return 日本語を描画できれば {@code true}
     */
    public static boolean canDisplayJapanese(String family) {
        if (family == null || family.isBlank()) {
            return false;
        }
        java.awt.Font awt = new java.awt.Font(family, java.awt.Font.PLAIN, 12);
        if (!family.equalsIgnoreCase(awt.getFamily())) {
            return false;
        }
        return awt.canDisplayUpTo(JAPANESE_SAMPLE) == -1;
    }

    /**
     * インストール済みのうち、日本語を描画できるファミリー名。
     *
     * @return ソート済みのファミリー名
     */
    public static List<String> japaneseFamilies() {
        return Font.getFamilies().stream()
                .filter(UiFonts::canDisplayJapanese)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
