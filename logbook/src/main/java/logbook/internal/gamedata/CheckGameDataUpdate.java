package logbook.internal.gamedata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import logbook.bean.AppConfig;
import logbook.internal.AppQuestConditionLoader;
import logbook.internal.Mapping;
import logbook.internal.SeaArea;
import logbook.internal.ThreadManager;
import lombok.extern.slf4j.Slf4j;

/**
 * ゲームデータ（マップ・任務・識別札）のリモート更新チェックとダウンロード。
 * <p>
 * チェックタイミングは起動時・設定画面を開いた時、
 * および前回チェックから 12 時間経過後の定期実行。
 * </p>
 */
@Slf4j
public final class CheckGameDataUpdate {

    /** 前回チェックからの最短間隔（定期実行用） */
    static final Duration PERIODIC_INTERVAL = Duration.ofHours(12);

    /** 定期ポーリング間隔（前回チェックからの経過を見る粒度） */
    private static final Duration POLL_INTERVAL = Duration.ofMinutes(30);

    private static final CheckGameDataUpdate INSTANCE = new CheckGameDataUpdate();

    private final GameDataUpdateRunner runner;
    private final Clock clock;
    private final BooleanSupplier enabledSupplier;
    private final Executor executor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicBoolean periodicStarted = new AtomicBoolean(false);

    /** 前回チェック開始時刻（未実施時は null） */
    private final AtomicReference<Instant> lastCheckAt = new AtomicReference<>();

    private CheckGameDataUpdate() {
        this(
                createDefaultRunner(),
                Clock.systemUTC(),
                () -> AppConfig.get().isCheckGameDataUpdate(),
                ThreadManager.getExecutorService());
    }

    /**
     * テスト用コンストラクタ。
     *
     * @param runner 更新実行本体
     * @param clock 時刻源
     * @param enabledSupplier 更新チェック有効判定
     * @param executor 非同期実行先
     */
    CheckGameDataUpdate(
            GameDataUpdateRunner runner,
            Clock clock,
            BooleanSupplier enabledSupplier,
            Executor executor) {
        this.runner = Objects.requireNonNull(runner);
        this.clock = Objects.requireNonNull(clock);
        this.enabledSupplier = Objects.requireNonNull(enabledSupplier);
        this.executor = Objects.requireNonNull(executor);
    }

    private static GameDataUpdateRunner createDefaultRunner() {
        return new GameDataUpdateRunner(
                new HttpsGameDataFetcher(),
                GameDataPaths::root,
                GameDataLoader::currentDataVersion,
                () -> GameDataPaths.DEFAULT_BASE_URL,
                Mapping::reload,
                SeaArea::reload,
                AppQuestConditionLoader::reload);
    }

    /**
     * シングルトンを取得します。
     *
     * @return インスタンス
     */
    public static CheckGameDataUpdate getInstance() {
        return INSTANCE;
    }

    /**
     * 設定が有効な場合、バックグラウンドで更新チェックを実行します（起動・設定画面用。間隔無視）。
     */
    public void runAsyncIfEnabled() {
        if (!this.enabledSupplier.getAsBoolean()) {
            return;
        }
        this.executor.execute(this::run);
    }

    /**
     * 前回チェックから 12 時間経過していれば、バックグラウンドで更新チェックを実行します。
     */
    public void runAsyncIfDue() {
        if (!this.enabledSupplier.getAsBoolean()) {
            return;
        }
        if (!isDue(this.lastCheckAt.get(), this.clock.instant(), PERIODIC_INTERVAL)) {
            return;
        }
        this.executor.execute(this::run);
    }

    /**
     * 前回チェックからの経過を監視する定期チェックを開始します（多重起動しない）。
     */
    public void startPeriodicCheck() {
        if (!this.periodicStarted.compareAndSet(false, true)) {
            return;
        }
        this.executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(POLL_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                this.runAsyncIfDue();
            }
        });
    }

    /**
     * リモートマニフェストを確認し、新しければ差分をダウンロードします。
     */
    public void run() {
        if (!this.running.compareAndSet(false, true)) {
            log.debug("ゲームデータ更新チェックは既に実行中のためスキップします");
            return;
        }
        this.lastCheckAt.set(this.clock.instant());
        try {
            this.runner.run();
        } catch (Exception e) {
            log.warn("ゲームデータの更新チェックに失敗しました（同梱データを継続利用します）", e);
        } finally {
            this.running.set(false);
        }
    }

    /**
     * 定期実行の期限到来判定。
     *
     * @param lastCheckAt 前回チェック時刻（未実施は null）
     * @param now 現在時刻
     * @param interval 最短間隔
     * @return 実行すべきなら true
     */
    static boolean isDue(Instant lastCheckAt, Instant now, Duration interval) {
        if (lastCheckAt == null) {
            return true;
        }
        return Duration.between(lastCheckAt, now).compareTo(interval) >= 0;
    }
}
