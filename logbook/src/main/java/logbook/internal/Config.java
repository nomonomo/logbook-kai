package logbook.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import logbook.bean.ConfigDefaults;
import lombok.extern.slf4j.Slf4j;


/**
 * アプリケーションの設定を読み書きします
 *
 */
@Slf4j
public final class Config {

    private static final Path CONFIG_DIR = Paths.get("./config"); //$NON-NLS-1$

    private static volatile Config DEFAULT = new Config(CONFIG_DIR);

    private final Path dir;

    private final Map<Class<?>, Object> map = new ConcurrentHashMap<>();

    /** requestStore で書き込み待ちの型 */
    private final Set<Class<?>> dirtyTypes = ConcurrentHashMap.newKeySet();

    /** requestStore の単一フライト */
    private final AtomicBoolean storing = new AtomicBoolean(false);

    /** write 呼び出し回数（テスト用） */
    private final AtomicInteger writeCount = new AtomicInteger();

    private final Object idleMonitor = new Object();

    /**
     * アプリケーション設定の読み書きを指定のディレクトリで行います
     *
     * @param dir アプリケーション設定ディレクトリ
     */
    public Config(Path dir) {
        this.dir = dir;
    }

    /**
     * clazzで指定された型からインスタンスを復元します
     *
     * @param <T> Bean型
     * @param clazz Bean型 Classオブジェクト
     * @param def デフォルト値を供給するSupplier
     * @return 設定
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Supplier<T> def) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(def);

        T instance = (T) this.map.computeIfAbsent(clazz, key -> {
            T v = this.read((Class<T>) key);
            if (v == null) {
                v = def.get();
            }
            applyConfigurationDefaults(v);
            return v;
        });
        return instance;
    }

    /**
     * 読み込まれたすべてのインスタンスをファイルに書き込みます
     */
    public synchronized void store() {
        this.map.entrySet()
                .forEach(this::store);
    }

    /**
     * 指定した型だけをファイルに書き込みます。未ロードの型は無視します。
     *
     * @param classes 書き込む Bean 型
     */
    public synchronized void store(Class<?>... classes) {
        if (classes == null) {
            return;
        }
        for (Class<?> clazz : classes) {
            if (clazz == null) {
                continue;
            }
            Object instance = this.map.get(clazz);
            if (instance != null) {
                this.write(clazz, instance);
            }
        }
    }

    /**
     * 指定した型の書き込みを要求します。書き込み中なら dirty のみ立て、キューには積みません。
     *
     * @param classes 書き込む Bean 型
     */
    public void requestStore(Class<?>... classes) {
        if (classes == null || classes.length == 0) {
            return;
        }
        for (Class<?> clazz : classes) {
            if (clazz != null) {
                this.dirtyTypes.add(clazz);
            }
        }
        this.tryStartRequestedStore();
    }

    private void tryStartRequestedStore() {
        if (!this.storing.compareAndSet(false, true)) {
            return;
        }
        ThreadManager.getExecutorService().execute(this::drainRequestedStore);
    }

    private void drainRequestedStore() {
        try {
            do {
                Set<Class<?>> batch = Set.copyOf(this.dirtyTypes);
                this.dirtyTypes.removeAll(batch);
                this.store(batch.toArray(Class<?>[]::new));
            } while (!this.dirtyTypes.isEmpty());
        } finally {
            this.storing.set(false);
            if (!this.dirtyTypes.isEmpty()) {
                this.tryStartRequestedStore();
            } else {
                synchronized (this.idleMonitor) {
                    this.idleMonitor.notifyAll();
                }
            }
        }
    }

    /**
     * 進行中の {@link #requestStore(Class[])} が終わるまで待ちます。
     *
     * @throws InterruptedException 割り込み
     */
    void awaitRequestStore() throws InterruptedException {
        synchronized (this.idleMonitor) {
            while (this.storing.get() || !this.dirtyTypes.isEmpty()) {
                this.idleMonitor.wait(100);
            }
        }
    }

    int writeCountForTest() {
        return this.writeCount.get();
    }

    private void store(Entry<Class<?>, ?> entry) {
        this.write(entry.getKey(), entry.getValue());
    }

    private <T> T read(Class<T> clazz) {
        T instance = null;
        try {
            tryRead: {
                Path filepath = this.jsonPath(clazz);
                // 通常ファイル読み込み
                if (canRead(filepath)) {
                    try {
                        instance = this.readFromPath(filepath, clazz);
                        break tryRead;
                    } catch (Exception e) {
                        instance = null;
                        log.warn("アプリケーションの設定を読み込み中に例外が発生", e); //$NON-NLS-1$
                    }
                }
                // ファイルが読み込めないまたはサイズがゼロの場合バックアップファイルを読み込む
                filepath = this.backupPath(filepath);
                if (canRead(filepath)) {
                    instance = this.readFromPath(filepath, clazz);
                    break tryRead;
                }
            }
        } catch (Exception e) {
            instance = null;
            log.warn("アプリケーションの設定を読み込み中に例外が発生", e); //$NON-NLS-1$
        }
        return instance;
    }

    private boolean canRead(Path path) {
        try {
            return Files.isReadable(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Path から 1 件読み込み。
     * @param path パス
     * @param clazz クラス
     * @return 読み込み結果
     * @throws Exception 読み込み例外
     */
    private <T> T readFromPath(Path path, Class<T> clazz) throws Exception {
        return JsonMappers.LENIENT_READER.forType(clazz).readValue(path);
    }

    private void write(Class<?> clazz, Object instance) {
        this.writeCount.incrementAndGet();
        try {
            Path filepath = this.jsonPath(clazz);

            // create parent directory
            if (!Files.exists(filepath)) {
                Path parent = filepath.getParent();
                if (parent != null) {
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                }
            }

            if (Files.exists(filepath) && (Files.size(filepath) > 0)) {
                Path backup = this.backupPath(filepath);
                Files.move(filepath, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            JsonMappers.MAPPER.writeValue(filepath, instance);
        } catch (Exception e) {
            log.warn("アプリケーションの設定を書き込み中に例外が発生", e); //$NON-NLS-1$
        }
    }

    private Path jsonPath(Class<?> clazz) {
        return this.dir.resolve(clazz.getCanonicalName() + ".json"); //$NON-NLS-1$
    }

    private Path backupPath(Path filepath) {
        return filepath.resolveSibling(filepath.getFileName() + ".backup"); //$NON-NLS-1$
    }

    private static void applyConfigurationDefaults(Object instance) {
        if (instance instanceof ConfigDefaults configurable) {
            configurable.applyDefaults();
        }
    }

    /**
     * 設定の読み書きに使用するディレクトリを返します。
     *
     * @return アプリケーション設定ディレクトリ
     */
    public Path getConfigDir() {
        return this.dir;
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリから設定を取得します
     *
     * @return アプリケーションのデフォルト設定ディレクトリ
     */
    public static Config getDefault() {
        return DEFAULT;
    }

    /**
     * テスト用。{@link #getDefault()} が返す Config を差し替えます。
     *
     * @param config 差し替える Config（本番では呼ばない）
     */
    static void setDefaultForTest(Config config) {
        DEFAULT = config;
    }
}
