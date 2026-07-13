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
        @JsonProperty("request") String request,
        @JsonProperty("response") String response) {

    static ApiCaptureEnvelope from(ApiCaptureRecord record, Instant capturedAt) {
        return new ApiCaptureEnvelope(
                1,
                nullToEmpty(record.requestId()),
                capturedAt,
                nullToEmpty(record.method()),
                nullToEmpty(record.uriPath()),
                record.requestBody(),
                nullToEmpty(record.responseBody()));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
