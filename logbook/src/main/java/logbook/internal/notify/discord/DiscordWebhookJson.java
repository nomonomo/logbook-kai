package logbook.internal.notify.discord;

import java.util.ArrayList;
import java.util.List;

import logbook.internal.JsonMappers;
import logbook.internal.Version;
import tools.jackson.core.JacksonException;

/**
 * {@link DiscordWebhookPayload} の組み立てと JSON 文字列化。
 */
public final class DiscordWebhookJson {

    /** フッターアイコン */
    static final String FOOTER_ICON_URL =
            "https://raw.githubusercontent.com/nomonomo/logbook-kai/master/logbook/src/main/resources/logbook/gui/icon_64x64.png";

    private DiscordWebhookJson() {
    }

    /**
     * 題と本文だけの Embed を作ります。
     *
     * @param title 題
     * @param description 本文
     * @param color 色（0xRRGGBB）
     * @return ペイロード
     */
    static DiscordWebhookPayload embed(String title, String description, int color) {
        return payload(new DiscordWebhookPayload.Embed(title, description, color, footer(), new ArrayList<>()));
    }

    /**
     * フィールド付きの Embed を作ります。
     *
     * @param title 題
     * @param fields フィールド
     * @param color 色（0xRRGGBB）
     * @return ペイロード
     */
    static DiscordWebhookPayload embedWithFields(String title, List<DiscordWebhookPayload.EmbedField> fields, int color) {
        return payload(new DiscordWebhookPayload.Embed(title, "", color, footer(), fields));
    }

    /**
     * フッターに出す題名と版です。
     *
     * @return フッター文字列
     */
    static String footerText() {
        Version current = Version.getCurrent();
        String title = current.getTitle();
        if (title == null || title.isBlank()) {
            title = "航海日誌";
        }
        return title + " " + current.toBaseString();
    }

    /**
     * JSON 文字列にします。
     *
     * @param payload ペイロード
     * @return JSON
     * @throws JacksonException 生成に失敗したとき
     */
    static String write(DiscordWebhookPayload payload) throws JacksonException {
        return JsonMappers.MAPPER.writeValueAsString(payload);
    }

    private static DiscordWebhookPayload.EmbedFooter footer() {
        return new DiscordWebhookPayload.EmbedFooter(footerText(), FOOTER_ICON_URL);
    }

    private static DiscordWebhookPayload payload(DiscordWebhookPayload.Embed embed) {
        List<DiscordWebhookPayload.Embed> embeds = new ArrayList<>();
        embeds.add(embed);
        return new DiscordWebhookPayload(embeds);
    }
}
