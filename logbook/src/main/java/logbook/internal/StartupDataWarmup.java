package logbook.internal;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import logbook.bean.AppCondition;
import logbook.bean.DeckPortCollection;
import logbook.bean.MissionCollection;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMstCollection;
import logbook.bean.SlotItemCollection;
import logbook.bean.SlotitemMstCollection;
import lombok.extern.slf4j.Slf4j;

/**
 * 起動時に艦隊必須の Config を並列で先読みします。
 * <p>
 * 遅延ロードの仕組みはそのままです。{@link #start()} は完了を待たず、
 * 本使用時に未完了なら従来どおり呼び出しスレッドで同期ロードされます。
 * </p>
 */
@Slf4j
public final class StartupDataWarmup {

    private StartupDataWarmup() {
    }

    /**
     * 必須データの先読みを開始します（非同期・完了待ちなし）。
     */
    public static void start() {
        ExecutorService executor = ThreadManager.getExecutorService();
        submit(executor, "DeckPortCollection", DeckPortCollection::get);
        submit(executor, "ShipCollection", ShipCollection::get);
        submit(executor, "ShipMstCollection", ShipMstCollection::get);
        submit(executor, "SlotItemCollection", SlotItemCollection::get);
        submit(executor, "SlotitemMstCollection", SlotitemMstCollection::get);
        submit(executor, "AppCondition", AppCondition::get);
        submit(executor, "MissionCollection", MissionCollection::get);
    }

    private static void submit(ExecutorService executor, String name, Supplier<?> loader) {
        executor.execute(() -> {
            try {
                loader.get();
            } catch (Exception e) {
                log.warn("起動データ先読みに失敗しました: {}", name, e);
            }
        });
    }
}
