package logbook.internal;

/**
 * 開発モードの実行時オプションを管理します。
 *
 * <p>開発モードが有効な場合、画面表示用にビルド日時を付与します（{@link #formatVersionDisplay(Version)}）。</p>
 *
 * <ul>
 *   <li>コマンドライン引数: {@code --dev}</li>
 *   <li>システムプロパティ: {@code -Dlogbook.dev=true}</li>
 * </ul>
 */
public final class DevMode {

    /** システムプロパティ名 */
    public static final String PROPERTY = "logbook.dev";

    private DevMode() {
    }

    /**
     * コマンドライン引数から開発モードを設定します。
     * アプリケーション起動の最初に呼び出してください。
     *
     * @param args コマンドライン引数
     */
    public static void configure(String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if ("--dev".equals(arg)) {
                System.setProperty(PROPERTY, "true");
                return;
            }
        }
    }

    /**
     * 開発モードが有効かどうかを返します。
     *
     * @return 開発モードが有効な場合 {@code true}
     */
    public static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /**
     * 画面表示用のバージョン文字列を返します。
     * 開発モード時はビルド日時を付与し、実行中ビルドの識別を容易にします。
     *
     * @param version バージョン情報
     * @return 表示用文字列（例: {@code 26.6.3} または {@code 26.6.3-2026-02-24T06:01:23Z}）
     */
    public static String formatVersionDisplay(Version version) {
        String base = version.toBaseString();
        if (!isEnabled()) {
            return base;
        }
        String buildTimestamp = version.getBuildTimestamp();
        if (buildTimestamp == null || buildTimestamp.isEmpty()) {
            return base;
        }
        return base + "-" + buildTimestamp;
    }
}
