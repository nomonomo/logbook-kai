package logbook.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javafx.application.Platform;
import logbook.internal.Config;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 出撃などの状態
 *
 */
@Slf4j
@Data
public class AppCondition implements Serializable {

    private static final long serialVersionUID = -7213537629508340991L;

    /** 連合艦隊 */
    private boolean combinedFlag;

    /** 連合艦隊 (0=未結成, 1=機動部隊, 2=水上部隊, 3=輸送部隊, -x=解隊(-1=機動部隊, -2=水上部隊)) */
    private int combinedType = 0;

    /** 出撃中 */
    private boolean mapStart;

    /** 出撃艦隊 */
    private int deckId = 0;

    /** 戦闘結果 */
    private BattleLog battleResult;

    /** 最後の戦闘結果 */
    private BattleLog battleResultConfirm;

    /** 演習結果 */
    private BattleLog practiceBattleResult;

    /** 退避艦ID */
    private Set<Integer> escape = new HashSet<>();

    /** 資材状況 */
    private Map<Integer, Material> material;

    /** 最後に資材ログに書き込んだ時間 */
    private long wroteMaterialLogLast = 0;

    /** 泊地修理タイマー */
    private long akashiTimer = 0;

    /** cond値更新時間(エポック秒) */
    private long condUpdateTime = 0;

    /** 戦闘回数 */
    private int battleCount = 0;

    /** ルート(mapping.jsonを参照) */
    private List<String> route = new ArrayList<>();

    /** 戦闘結果更新リスナー（シリアライズ対象外） */
    @JsonIgnore
    private transient final List<NamedListener<BattleResultUpdateListener>> battleResultUpdateListeners = new CopyOnWriteArrayList<>();

    /** 演習結果更新リスナー（シリアライズ対象外） */
    @JsonIgnore
    private transient final List<NamedListener<PracticeBattleResultUpdateListener>> practiceBattleResultUpdateListeners = new CopyOnWriteArrayList<>();

    /**
     * 名前付きリスナー
     * リスナーとその名前を保持する内部クラス
     *
     * @param <T> リスナーの型
     */
    private static class NamedListener<T> {
        private final T listener;
        private final String name;

        NamedListener(T listener, String name) {
            this.listener = listener;
            this.name = name;
        }

        T getListener() {
            return this.listener;
        }

        String getName() {
            return this.name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            NamedListener<?> that = (NamedListener<?>) obj;
            return Objects.equals(this.listener, that.listener);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.listener);
        }
    }

    /**
     * 戦闘ログ更新リスナー
     * 戦闘ログが更新された際に呼び出される共通の関数型インターフェース
     */
    @FunctionalInterface
    public interface BattleLogUpdateListener {
        /**
         * 戦闘ログが更新された際に呼び出されます
         *
         * @param log 更新された戦闘ログ（nullの可能性あり）
         */
        void onBattleLogUpdated(BattleLog log);
    }

    /**
     * 戦闘結果更新リスナー
     * 戦闘結果が更新された際に呼び出される関数型インターフェース
     * {@link BattleLogUpdateListener}のエイリアス
     */
    @FunctionalInterface
    public interface BattleResultUpdateListener extends BattleLogUpdateListener {
        /**
         * 戦闘結果が更新された際に呼び出されます
         *
         * @param log 更新された戦闘ログ（nullの可能性あり）
         */
        @Override
        default void onBattleLogUpdated(BattleLog log) {
            this.onBattleResultUpdated(log);
        }

        /**
         * 戦闘結果が更新された際に呼び出されます
         *
         * @param log 更新された戦闘ログ（nullの可能性あり）
         */
        void onBattleResultUpdated(BattleLog log);
    }

    /**
     * 演習結果更新リスナー
     * 演習結果が更新された際に呼び出される関数型インターフェース
     * {@link BattleLogUpdateListener}のエイリアス
     */
    @FunctionalInterface
    public interface PracticeBattleResultUpdateListener extends BattleLogUpdateListener {
        /**
         * 演習結果が更新された際に呼び出されます
         *
         * @param log 更新された戦闘ログ（nullの可能性あり）
         */
        @Override
        default void onBattleLogUpdated(BattleLog log) {
            this.onPracticeBattleResultUpdated(log);
        }

        /**
         * 演習結果が更新された際に呼び出されます
         *
         * @param log 更新された戦闘ログ（nullの可能性あり）
         */
        void onPracticeBattleResultUpdated(BattleLog log);
    }

    /**
     * 戦闘結果更新リスナーを登録します
     * 既に登録されているリスナーは重複登録されません
     *
     * @param listener リスナー（null不可）
     * @throws NullPointerException listenerがnullの場合
     */
    public void addBattleResultUpdateListener(BattleResultUpdateListener listener) {
        this.addBattleResultUpdateListener(listener, "未命名リスナー");
    }

    /**
     * 戦闘結果更新リスナーを登録します
     * 既に登録されているリスナーは重複登録されません
     *
     * @param listener リスナー（null不可）
     * @param name リスナーの名前（null不可）
     * @throws NullPointerException listenerまたはnameがnullの場合
     */
    public void addBattleResultUpdateListener(BattleResultUpdateListener listener, String name) {
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(name, "name must not be null");
        NamedListener<BattleResultUpdateListener> namedListener = new NamedListener<>(listener, name);
        if (!this.battleResultUpdateListeners.contains(namedListener)) {
            this.battleResultUpdateListeners.add(namedListener);
        }
    }

    /**
     * 戦闘結果更新リスナーを解除します
     *
     * @param listener リスナー（null不可）
     * @throws NullPointerException listenerがnullの場合
     */
    public void removeBattleResultUpdateListener(BattleResultUpdateListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        this.battleResultUpdateListeners.removeIf(named -> named.getListener().equals(listener));
    }

    /**
     * 戦闘結果更新を通知します
     * すべてのリスナーはJavaFXアプリケーションスレッドで実行されます
     * 現在の{@link #battleResult}をリスナーに渡します。
     */
    public void notifyBattleResultUpdated() {
        this.notifyBattleResultUpdated(this.battleResult);
    }

    /**
     * 戦闘結果更新を通知します
     * すべてのリスナーはJavaFXアプリケーションスレッドで実行されます
     *
     * @param battleLog 更新された戦闘ログ（nullの可能性あり）
     */
    public void notifyBattleResultUpdated(BattleLog battleLog) {
        try {
            // JavaFXアプリケーションスレッドで実行
            // すべてのリスナーを1回のスケジューリングで実行することで、パフォーマンスとアトミック性を確保
            Platform.runLater(() -> {
                for (NamedListener<BattleResultUpdateListener> namedListener : this.battleResultUpdateListeners) {
                    try {
                        namedListener.getListener().onBattleResultUpdated(battleLog);
                    } catch (Exception e) {
                        // リスナー実行中の例外をログに記録
                        // 1つのリスナーで例外が発生しても、他のリスナーは実行される
                        log.warn("戦闘結果更新リスナーの実行に失敗しました: リスナー名=" + namedListener.getName(), e);
                    }
                }
            });
        } catch (Exception e) {
            // Platform.runLater()の呼び出し自体が失敗した場合
            // （JavaFXアプリケーションが初期化されていない場合など）
            log.warn("戦闘結果更新通知のスケジューリングに失敗しました", e);
        }
    }

    /**
     * 演習結果更新リスナーを登録します
     * 既に登録されているリスナーは重複登録されません
     *
     * @param listener リスナー（null不可）
     * @throws NullPointerException listenerがnullの場合
     */
    public void addPracticeBattleResultUpdateListener(PracticeBattleResultUpdateListener listener) {
        this.addPracticeBattleResultUpdateListener(listener, "未命名リスナー");
    }

    /**
     * 演習結果更新リスナーを登録します
     * 既に登録されているリスナーは重複登録されません
     *
     * @param listener リスナー（null不可）
     * @param name リスナーの名前（null不可）
     * @throws NullPointerException listenerまたはnameがnullの場合
     */
    public void addPracticeBattleResultUpdateListener(PracticeBattleResultUpdateListener listener, String name) {
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(name, "name must not be null");
        NamedListener<PracticeBattleResultUpdateListener> namedListener = new NamedListener<>(listener, name);
        if (!this.practiceBattleResultUpdateListeners.contains(namedListener)) {
            this.practiceBattleResultUpdateListeners.add(namedListener);
        }
    }

    /**
     * 演習結果更新リスナーを解除します
     *
     * @param listener リスナー（null不可）
     * @throws NullPointerException listenerがnullの場合
     */
    public void removePracticeBattleResultUpdateListener(PracticeBattleResultUpdateListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        this.practiceBattleResultUpdateListeners.removeIf(named -> named.getListener().equals(listener));
    }

    /**
     * 演習結果更新を通知します
     * すべてのリスナーはJavaFXアプリケーションスレッドで実行されます
     * 現在の{@link #practiceBattleResult}をリスナーに渡します。
     */
    public void notifyPracticeBattleResultUpdated() {
        this.notifyPracticeBattleResultUpdated(this.practiceBattleResult);
    }

    /**
     * 演習結果更新を通知します
     * すべてのリスナーはJavaFXアプリケーションスレッドで実行されます
     *
     * @param battleLog 更新された戦闘ログ（nullの可能性あり）
     */
    public void notifyPracticeBattleResultUpdated(BattleLog battleLog) {
        try {
            // JavaFXアプリケーションスレッドで実行
            // すべてのリスナーを1回のスケジューリングで実行することで、パフォーマンスとアトミック性を確保
            Platform.runLater(() -> {
                for (NamedListener<PracticeBattleResultUpdateListener> namedListener : this.practiceBattleResultUpdateListeners) {
                    try {
                        namedListener.getListener().onPracticeBattleResultUpdated(battleLog);
                    } catch (Exception e) {
                        // リスナー実行中の例外をログに記録
                        // 1つのリスナーで例外が発生しても、他のリスナーは実行される
                        log.warn("演習結果更新リスナーの実行に失敗しました: リスナー名=" + namedListener.getName(), e);
                    }
                }
            });
        } catch (Exception e) {
            // Platform.runLater()の呼び出し自体が失敗した場合
            // （JavaFXアプリケーションが初期化されていない場合など）
            log.warn("演習結果更新通知のスケジューリングに失敗しました", e);
        }
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリから{@link AppCondition}を取得します、
     * これは次の記述と同等です
     * <blockquote>
     *     <code>Config.getDefault().get(AppCondition.class, AppCondition::new)</code>
     * </blockquote>
     *
     * @return {@link AppCondition}
     */
    public static AppCondition get() {
        return Config.getDefault().get(AppCondition.class, AppCondition::new);
    }
}
