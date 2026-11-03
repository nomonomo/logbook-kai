package logbook.internal.notify.discord;

/**
 * Discord Webhook へ送る確定済みリクエスト。URL はログに出さない。
 *
 * @param url Webhook URL
 * @param body JSON 本文
 * @param expectedStatus 成功とみなす HTTP ステータス
 * @param channelName ログに出すチャネル名
 */
public record DiscordWebhookRequest(
        String url,
        String body,
        int expectedStatus,
        String channelName) {
}
