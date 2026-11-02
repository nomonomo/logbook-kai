package logbook.notify;

import java.time.Duration;
import java.util.Optional;

/**
 * 通知を受け取るリスナーです。<br>
 * 登録したリスナーには発行された通知がすべて渡ります。出すか捨てるかはリスナーが決めます。<br>
 * 遠征完了の再通知だけは、{@link #missionRemindInterval()} が間隔を返すリスナーに渡します。<br>
 * <br>
 * 実装クラスは ServiceLoader より取得されます。<br>
 * プラグイン JAR では {@code META-INF/services/logbook.notify.NotificationListener} に実装クラス名を書きます。
 * <pre>{@code
 * module your.module {
 *     provides logbook.notify.NotificationListener with your.package.YourListener;
 * }
 * }</pre>
 * <br>
 * {@link #onNotify(Notification)} は JavaFX アプリケーションスレッドでは呼ばれません。
 * 画面更新はリスナー側で {@code Platform.runLater} してください。
 * 1 件の例外は他のリスナーへ影響しません。
 */
public interface NotificationListener {

    /** 遠征完了を繰り返す最短の間隔（秒） */
    int MISSION_REMIND_MIN_SECONDS = 10;

    /** 遠征完了を繰り返す最長の間隔（秒） */
    int MISSION_REMIND_MAX_SECONDS = 3600;

    /**
     * 通知を受け取ります。
     *
     * @param event 通知
     */
    void onNotify(Notification event);

    /**
     * 遠征完了を繰り返す間隔を返します。繰り返さないときは empty です。
     * 既定は繰り返しません。プラグインは自分の設定の秒数を返します。<br>
     * 再通知に使われるのは {@value #MISSION_REMIND_MIN_SECONDS} 秒以上
     * {@value #MISSION_REMIND_MAX_SECONDS} 秒以下だけです。範囲外は繰り返しません。
     * empty は繰り返さないだけで、範囲外とは別です。
     *
     * @return 繰り返し間隔
     */
    default Optional<Duration> missionRemindInterval() {
        return Optional.empty();
    }
}
