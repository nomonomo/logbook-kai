package logbook.internal;

import logbook.internal.gamedata.CheckGameDataUpdate;
import logbook.plugin.lifecycle.StartUp;

/**
 * ゲームデータ更新チェック（起動時および前回チェックから 12 時間毎の定期実行）。
 */
public class CheckGameDataUpdateStartUp implements StartUp {

    @Override
    public void run() {
        CheckGameDataUpdate checker = CheckGameDataUpdate.getInstance();
        checker.runAsyncIfEnabled();
        checker.startPeriodicCheck();
    }
}
