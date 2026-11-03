package logbook.bean;

import java.util.EnumMap;
import java.util.EnumSet;

import logbook.internal.Config;
import lombok.Data;

/**
 * Discord 通知の設定。出す種別は {@link #notify}、色は {@link #colors} にまとめる。
 */
@Data
public class DiscordConfig implements ConfigDefaults {

    /** テスト送信の Embed 色。設定欄は置かない */
    public static final int COLOR_TEST = 0x5865F2;

    /**
     * Discord で出す通知。既定のオンオフと色を持つ。
     */
    public enum Kind {
        /** 遠征完了 */
        MISSION("遠征完了時", true, 0x2ECC71),
        /** 遠征開始 */
        MISSION_STARTED("遠征開始（第二〜第四艦隊）", true, 0x3498DB),
        /** 入渠完了 */
        NDOCK("入渠完了時", true, 0xE67E22),
        /** 遠征条件を満たしていない */
        MISSION_CONDITION("遠征条件が満たされていない場合", false, 0xF1C40F),
        /** 母港給糧 */
        PORT_SUPPLY("母港給糧完了予定時刻", true, 0x1ABC9C),
        /** 疲労回復 */
        COND("疲労回復予定時刻", false, 0x9B59B6),
        /** 出撃時の大破 */
        BADLY_SORTIE("出撃時に大破艦がいる場合", false, 0xE74C3C),
        /** 進撃時の大破 */
        BADLY_ADVANCE("進撃時に大破艦がいる場合", false, 0xC0392B),
        /** ギミック */
        GIMMICK("ギミックの達成", false, 0x8E44AD);

        private final String label;

        private final boolean enabledByDefault;

        private final int defaultColor;

        Kind(String label, boolean enabledByDefault, int defaultColor) {
            this.label = label;
            this.enabledByDefault = enabledByDefault;
            this.defaultColor = defaultColor;
        }

        /**
         * 設定画面の表示名を返します。
         *
         * @return 表示名
         */
        public String label() {
            return this.label;
        }

        /**
         * 初期状態で通知するかを返します。
         *
         * @return 通知するなら true
         */
        public boolean enabledByDefault() {
            return this.enabledByDefault;
        }

        /**
         * 既定の Embed 色を返します。
         *
         * @return 0xRRGGBB
         */
        public int defaultColor() {
            return this.defaultColor;
        }
    }

    /** 通知全体を使う。オフなら URL があっても送らない */
    private boolean enable = true;

    /** Incoming Webhook URL */
    private String incomingWebhookUrl = "";

    /** 通知する種別 */
    private EnumSet<Kind> notify = defaultNotify();

    /** 種別ごとの色。無い種別は {@link Kind#defaultColor()} を使う */
    private EnumMap<Kind, Integer> colors = defaultColors();

    /**
     * その種別を通知するかを返します。
     *
     * @param kind 種別
     * @return 通知するなら true
     */
    public boolean notifies(Kind kind) {
        return this.notify != null && this.notify.contains(kind);
    }

    /**
     * その種別の通知を切り替えます。
     *
     * @param kind 種別
     * @param enabled 通知するなら true
     */
    public void setNotifies(Kind kind, boolean enabled) {
        if (this.notify == null) {
            this.notify = EnumSet.noneOf(Kind.class);
        }
        if (enabled) {
            this.notify.add(kind);
        } else {
            this.notify.remove(kind);
        }
    }

    /**
     * その種別の色を返します。未設定なら既定色です。
     *
     * @param kind 種別
     * @return 0xRRGGBB
     */
    public int color(Kind kind) {
        if (this.colors != null) {
            Integer value = this.colors.get(kind);
            if (value != null) {
                return value;
            }
        }
        return kind.defaultColor();
    }

    /**
     * その種別の色を設定します。
     *
     * @param kind 種別
     * @param rgb 0xRRGGBB
     */
    public void setColor(Kind kind, int rgb) {
        if (this.colors == null) {
            this.colors = new EnumMap<>(Kind.class);
        }
        this.colors.put(kind, rgb);
    }

    @Override
    public void applyDefaults() {
        if (this.notify == null) {
            this.notify = defaultNotify();
        }
        if (this.colors == null) {
            this.colors = new EnumMap<>(Kind.class);
        }
        for (Kind kind : Kind.values()) {
            this.colors.putIfAbsent(kind, kind.defaultColor());
        }
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリから{@link DiscordConfig}を取得します。
     * これは次の記述と同等です
     * <blockquote>
     * <code>Config.getDefault().get(DiscordConfig.class, DiscordConfig::new)</code>
     * </blockquote>
     *
     * @return {@link DiscordConfig}
     */
    public static DiscordConfig get() {
        return Config.getDefault().get(DiscordConfig.class, DiscordConfig::new);
    }

    private static EnumSet<Kind> defaultNotify() {
        EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
        for (Kind kind : Kind.values()) {
            if (kind.enabledByDefault()) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    private static EnumMap<Kind, Integer> defaultColors() {
        EnumMap<Kind, Integer> colors = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            colors.put(kind, kind.defaultColor());
        }
        return colors;
    }
}
