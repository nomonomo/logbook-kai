package logbook.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * スレッドを管理します
 * <p>
 * Java 21のVirtual Threadsを使用して軽量な並列処理を実現します。
 * </p>
 */
public final class ThreadManager {

    /** 
     * Virtual Thread Executor
     * <p>
     * 軽量スレッド（Virtual Thread）を使用し、大量のタスクを効率的に処理します。
     * Platform Threadと異なり、スレッド数の制限がなく、数百万のタスクを同時実行可能です。
     * </p>
     */
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * アプリケーションで共有するExecutorService
     * <p>
     * Virtual Threadsを使用するため、タスク数に制限はありません。
     * 長時間実行する必要のあるスレッドを登録する場合、割り込みされたかを検知して適切に終了するようにしてください。
     * </p>
     *
     * @return ExecutorService
     */
    public static ExecutorService getExecutorService() {
        return EXECUTOR;
    }
}
