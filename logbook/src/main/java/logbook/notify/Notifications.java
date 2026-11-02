package logbook.notify;

import java.util.List;
import java.util.concurrent.Executor;

import logbook.internal.ThreadManager;
import logbook.plugin.PluginServices;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知を登録済みリスナーへ渡します。
 */
@Slf4j
public final class Notifications {

    private static volatile List<NotificationListener> listeners;

    private Notifications() {
    }

    /**
     * 通知を発行します。リスナーごとに別タスクで渡し、例外はリスナー単位で握ります。
     *
     * @param event 通知。null なら何もしない
     */
    public static void publish(Notification event) {
        dispatch(event, listeners(), ThreadManager.getExecutorService());
    }

    /**
     * 通知を指定したリスナーだけに渡します。遠征完了の再通知に使います。
     *
     * @param event 通知。null なら何もしない
     * @param targets 渡すリスナー
     */
    public static void publishTo(Notification event, List<NotificationListener> targets) {
        dispatch(event, targets, ThreadManager.getExecutorService());
    }

    /**
     * 登録済みリスナーを返します。
     *
     * @return リスナー
     */
    public static List<NotificationListener> registeredListeners() {
        return listeners();
    }

    static void dispatch(Notification event, List<NotificationListener> targets, Executor executor) {
        if (event == null || targets == null || targets.isEmpty()) {
            return;
        }
        for (NotificationListener listener : targets) {
            executor.execute(() -> invoke(listener, event));
        }
    }

    private static void invoke(NotificationListener listener, Notification event) {
        try {
            listener.onNotify(event);
        } catch (Exception e) {
            log.warn("通知リスナーで例外が発生しました: {}", listener.getClass().getName(), e);
        }
    }

    private static List<NotificationListener> listeners() {
        List<NotificationListener> current = listeners;
        if (current == null) {
            synchronized (Notifications.class) {
                current = listeners;
                if (current == null) {
                    current = load();
                    listeners = current;
                }
            }
        }
        return current;
    }

    private static List<NotificationListener> load() {
        try {
            return PluginServices.instances(NotificationListener.class).toList();
        } catch (Exception e) {
            log.warn("通知リスナーの読み込みに失敗しました", e);
            return List.of();
        }
    }
}
