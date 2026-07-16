package logbook.internal.capture;

/**
 * API キャプチャを JSONL + zstd 日次セグメントへ非同期書き込みする（本番向けファサード）。
 * <p>
 * 実体は {@link ApiCaptureWriteService}。ファイル操作は worker スレッドのみが行う。
 * {@link #flush()} / {@link #shutdown()} はキューへ命令を投入し、
 * 先行レコードの処理完了後にセグメントを閉じる。
 * </p>
 */
public final class ApiCaptureWriter {

    private static final ApiCaptureWriteService INSTANCE = ApiCaptureWriteService.createDefault();

    private ApiCaptureWriter() {
    }

    /**
     * キャプチャレコードをキューに追加する。
     * <p>
     * 呼び出し元で {@link ApiCaptureGate#isCaptureActive()} を確認済みであること。
     * 非同期書き込み時に設定 OFF になった場合は破棄する。
     * shutdown 開始後は受け付けず {@code false} を返す。
     * </p>
     *
     * @return キューに載せた場合 {@code true}
     */
    public static boolean enqueue(ApiCaptureRecord record) {
        return INSTANCE.enqueue(record);
    }

    /**
     * 呼び出し時点までにキューへ投入済みのレコードを書き切り、セグメントを閉じる。
     * <p>
     * 受付は継続する。アプリ終了時は {@link #shutdown()} を使う。
     * </p>
     */
    public static void flush() {
        INSTANCE.flush();
    }

    /**
     * 受付を停止し、キュー内の先行レコードをすべて書き込んでからセグメントを閉じ、worker を終了する。
     */
    public static void shutdown() {
        INSTANCE.shutdown();
    }
}
