package logbook.internal.notify.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import logbook.internal.ThreadManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Discord Webhook を仮想スレッドで POST します。
 */
@Slf4j
public final class DiscordWebhookClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final DiscordWebhookClient SHARED = new DiscordWebhookClient();

    private final HttpClient client;

    private final Executor executor;

    /**
     * 共有のクライアントを返します。
     *
     * @return クライアント
     */
    public static DiscordWebhookClient shared() {
        return SHARED;
    }

    /**
     * 仮想スレッドで送るクライアントを作ります。
     */
    public DiscordWebhookClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                ThreadManager.getExecutorService());
    }

    DiscordWebhookClient(HttpClient client, Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    /**
     * リクエストを送ります。呼び出し元のスレッドでは待ちません。
     *
     * @param request リクエスト
     * @param onSuccess 成功時。不要なら null
     * @param onError 失敗時。不要なら null
     */
    public void send(DiscordWebhookRequest request, Consumer<String> onSuccess, Consumer<Throwable> onError) {
        this.executor.execute(() -> this.post(request, onSuccess, onError));
    }

    private void post(DiscordWebhookRequest request, Consumer<String> onSuccess, Consumer<Throwable> onError) {
        try {
            URI uri = URI.create(request.url());
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request.body()))
                    .build();
            HttpResponse<String> response = this.client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == request.expectedStatus()) {
                if (onSuccess != null) {
                    onSuccess.accept(response.body());
                }
                return;
            }
            log.warn("{}通知が失敗しました status={}", request.channelName(), response.statusCode());
            if (onError != null) {
                onError.accept(new IllegalStateException(
                        request.channelName() + "通知が失敗しました status=" + response.statusCode()));
            }
        } catch (Exception e) {
            log.error("{}通知の送信に失敗しました", request.channelName(), e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }
}
