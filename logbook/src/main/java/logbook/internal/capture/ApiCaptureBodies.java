package logbook.internal.capture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * キャプチャ用に HTTP ボディ原文を読み取る。
 */
final class ApiCaptureBodies {

    private ApiCaptureBodies() {
    }

    /**
     * POST リクエストボディ原文。POST 以外または空の場合は {@code null}。
     */
    static String readRequestBody(RequestMetaData req) {
        if (req == null) {
            return null;
        }
        String method = req.getMethod();
        if (method == null || !"POST".equalsIgnoreCase(method)) {
            return null;
        }
        return req.getRequestBody()
                .map(ApiCaptureBodies::readStream)
                .filter(body -> !body.isEmpty())
                .orElse(null);
    }

    /**
     * レスポンスボディ原文。取得できない場合は空文字。
     */
    static String readResponseBody(ResponseMetaData res) {
        if (res == null) {
            return "";
        }
        return res.getResponseBody()
                .map(ApiCaptureBodies::readStream)
                .orElse("");
    }

    private static String readStream(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
