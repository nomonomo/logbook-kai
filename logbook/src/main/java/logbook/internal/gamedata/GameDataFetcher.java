package logbook.internal.gamedata;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ゲームデータのリモート取得。
 */
@FunctionalInterface
interface GameDataFetcher {

    /**
     * URL から {@code dest} へダウンロードします。
     * <p>
     * Content-Length が分かれば上限超過を事前拒否し、
     * 読み取り中にもサイズを数えて {@code maxBytes} 超過で中止します。
     * 失敗時は {@code dest} を削除します。
     * </p>
     *
     * @param url 取得先 URL
     * @param maxBytes 許容する最大サイズ（超過時は例外）
     * @param dest 書き込み先
     * @throws IOException 通信失敗・検証失敗・サイズ超過
     * @throws InterruptedException 割り込み
     */
    void downloadTo(String url, long maxBytes, Path dest) throws IOException, InterruptedException;
}
