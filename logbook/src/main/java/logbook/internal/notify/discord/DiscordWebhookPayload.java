package logbook.internal.notify.discord;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discord Incoming Webhook の JSON 本文。
 *
 * @param embeds Embed の並び
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DiscordWebhookPayload(List<Embed> embeds) {

    /**
     * Embed 1 件。
     *
     * @param title 題
     * @param description 本文
     * @param color 色（0xRRGGBB）
     * @param footer フッター
     * @param fields フィールド
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Embed(String title, String description, Integer color, EmbedFooter footer, List<EmbedField> fields) {
    }

    /**
     * Embed のフッター。
     *
     * @param text 表示文字
     * @param iconUrl アイコン URL
     */
    public record EmbedFooter(String text, @JsonProperty("icon_url") String iconUrl) {
    }

    /**
     * Embed のフィールド。
     *
     * @param name 名前
     * @param value 本文
     * @param inline 横並びなら true
     */
    public record EmbedField(String name, String value, boolean inline) {
    }
}
