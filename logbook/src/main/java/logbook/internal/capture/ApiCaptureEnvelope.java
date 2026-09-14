package logbook.internal.capture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * API キャプチャ JSONL 1 行分のエンベロープ。
 * <p>
 * キュー用の {@link ApiCaptureRecord} とは分離し、永続形式のみを表す。
 * {@code capturedAt} は {@link Instant} のまま渡し、Jackson 3 のデフォルト
 * （ISO-8601 文字列）で書き出す。数値タイムスタンプ化しないことは
 * {@code JsonMappersTest#mapperWritesInstantAsIso8601String} で固定する。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiCaptureEnvelope(
        int v,
        String requestId,
        Instant capturedAt,
        String method,
        String uriPath,
        String uri,
        String contentType,
        String requestEncoding,
        @JsonProperty("request") String request,
        String responseEncoding,
        @JsonProperty("response") String response) {

    static ApiCaptureEnvelope from(ApiCaptureRecord record, Instant capturedAt) {
        String requestBody = record.requestBody();
        return new ApiCaptureEnvelope(
                3,
                nullToEmpty(record.requestId()),
                capturedAt,
                nullToEmpty(record.method()),
                nullToEmpty(record.uriPath()),
                nullToEmpty(record.uri()),
                blankToNull(record.contentType()),
                requestBody == null ? null : defaultEncoding(record.requestEncoding()),
                requestBody,
                defaultEncoding(record.responseEncoding()),
                nullToEmpty(record.responseBody()));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String defaultEncoding(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return ApiCaptureBodies.ENCODING_UTF8;
        }
        return encoding;
    }
}
