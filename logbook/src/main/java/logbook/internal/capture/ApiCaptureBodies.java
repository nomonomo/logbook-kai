package logbook.internal.capture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * キャプチャ用に HTTP ボディを読み取り、JSONL 用の文字列とエンコーディングを決める。
 * <p>
 * テキストは {@code utf8}（原文）、バイナリは {@code base64} で可逆保存する。
 * </p>
 */
final class ApiCaptureBodies {

    static final String ENCODING_UTF8 = "utf8";
    static final String ENCODING_BASE64 = "base64";

    /**
     * エンベロープへ載せるボディ。{@code content} は encoding に応じた文字列。
     */
    record CapturedBody(String encoding, String content) {

        static CapturedBody utf8(String content) {
            return new CapturedBody(ENCODING_UTF8, content != null ? content : "");
        }

        static CapturedBody base64(byte[] bytes) {
            return new CapturedBody(ENCODING_BASE64, Base64.getEncoder().encodeToString(bytes));
        }

        static CapturedBody emptyUtf8() {
            return utf8("");
        }
    }

    private ApiCaptureBodies() {
    }

    /**
     * POST リクエストボディ。POST 以外または空の場合は {@code null}。
     */
    static CapturedBody readRequestBody(RequestMetaData req) {
        if (req == null) {
            return null;
        }
        String method = req.getMethod();
        if (method == null || !"POST".equalsIgnoreCase(method)) {
            return null;
        }
        return req.getRequestBody()
                .map(stream -> encode(readBytes(stream), req.getContentType()))
                .filter(body -> !body.content().isEmpty())
                .orElse(null);
    }

    /**
     * レスポンスボディ。取得できない場合は空の utf8。
     */
    static CapturedBody readResponseBody(ResponseMetaData res) {
        if (res == null) {
            return CapturedBody.emptyUtf8();
        }
        return res.getResponseBody()
                .map(stream -> encode(readBytes(stream), res.getContentType()))
                .orElse(CapturedBody.emptyUtf8());
    }

    /**
     * Content-Type とバイト列からエンコーディングを決めて文字列化する。
     * テストからも利用する。
     */
    static CapturedBody encode(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return CapturedBody.emptyUtf8();
        }
        if (isTextual(contentType, bytes)) {
            return CapturedBody.utf8(new String(bytes, StandardCharsets.UTF_8));
        }
        return CapturedBody.base64(bytes);
    }

    static boolean isTextual(String contentType, byte[] bytes) {
        String media = mediaType(contentType);
        if (media != null) {
            if (isTextualMediaType(media)) {
                return true;
            }
            if (isBinaryMediaType(media)) {
                return false;
            }
        }
        return looksLikeUtf8Text(bytes);
    }

    private static String mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String media = contentType;
        int semi = media.indexOf(';');
        if (semi >= 0) {
            media = media.substring(0, semi);
        }
        media = media.trim().toLowerCase(Locale.ROOT);
        return media.isEmpty() ? null : media;
    }

    private static boolean isTextualMediaType(String media) {
        if (media.startsWith("text/")) {
            return true;
        }
        return switch (media) {
            case "application/json",
                    "application/javascript",
                    "application/ecmascript",
                    "application/xml",
                    "application/xhtml+xml",
                    "application/x-www-form-urlencoded",
                    "application/ld+json" -> true;
            default -> media.endsWith("+json")
                    || media.endsWith("+xml")
                    || media.contains("javascript")
                    || media.contains("ecmascript");
        };
    }

    private static boolean isBinaryMediaType(String media) {
        if (media.startsWith("image/")
                || media.startsWith("audio/")
                || media.startsWith("video/")
                || media.startsWith("font/")) {
            return true;
        }
        return switch (media) {
            case "application/octet-stream",
                    "application/zip",
                    "application/gzip",
                    "application/pdf",
                    "application/wasm",
                    "application/x-protobuf" -> true;
            default -> false;
        };
    }

    private static boolean looksLikeUtf8Text(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static byte[] readBytes(InputStream stream) {
        try (stream) {
            return stream.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
