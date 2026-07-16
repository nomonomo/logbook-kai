package logbook.internal.gamedata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import logbook.internal.JsonMappers;
import logbook.plugin.PluginServices;
import lombok.extern.slf4j.Slf4j;

/**
 * ゲームデータの読み込み。
 * <p>
 * 外部（{@code config/gamedata}）と同梱のマニフェスト版を比較し、
 * 新しい方のソースから読みます（同版なら外部を優先）。
 * 外部のオープン／パースに失敗した場合は同梱へフォールバックします。
 * </p>
 */
@Slf4j
public final class GameDataLoader {

    /** 同梱マニフェストは起動後不変のため初回のみ読み込む */
    private static final Object BUNDLED_LOCK = new Object();

    private static volatile boolean bundledResolved;

    private static Optional<GameDataManifest> bundledManifest = Optional.empty();

    private GameDataLoader() {
    }

    /**
     * 入力ストリームから型 {@code T} を読み取る関数。
     *
     * @param <T> 読込結果の型
     */
    @FunctionalInterface
    public interface StreamReader<T> {
        /**
         * @param is 入力（呼び出し側がクローズする）
         * @return 読込結果
         * @throws Exception 読込・パース失敗
         */
        T read(InputStream is) throws Exception;
    }

    /**
     * 外部または同梱からゲームデータを読みます。
     * 外部を優先する場合でも、オープン／パース失敗時は同梱へフォールバックします。
     *
     * @param <T> 読込結果の型
     * @param relativePath data/ 配下の相対パス
     * @param classpathResource クラスパス資源名
     * @param reader ストリーム読取
     * @param empty どちらも失敗／無しのときの値
     * @return 読込結果
     */
    public static <T> T load(
            String relativePath,
            String classpathResource,
            StreamReader<T> reader,
            T empty) {
        return load(
                relativePath,
                classpathResource,
                reader,
                empty,
                GameDataLoader::preferLocalSource,
                GameDataPaths::resolveLocal,
                PluginServices::getResourceAsStream);
    }

    /**
     * {@link #load} の依存注入版（単体テスト用）。
     */
    static <T> T load(
            String relativePath,
            String classpathResource,
            StreamReader<T> reader,
            T empty,
            BooleanSupplier preferLocal,
            Function<String, Path> resolveLocal,
            Function<String, InputStream> openClasspath) {
        Path local = resolveLocal.apply(relativePath);
        if (preferLocal.getAsBoolean() && Files.isRegularFile(local)) {
            try (InputStream is = Files.newInputStream(local)) {
                log.debug("ゲームデータを外部ファイルから読み込みます: {}", local);
                return reader.read(is);
            } catch (Exception e) {
                log.warn("外部ゲームデータの読み込みに失敗したため同梱データを使用します: {}", local, e);
            }
        }
        try (InputStream is = openClasspath.apply(classpathResource)) {
            if (is == null) {
                log.error("同梱のゲームデータが見つかりません: {}", classpathResource);
                return empty;
            }
            log.debug("ゲームデータを同梱リソースから読み込みます: {}", classpathResource);
            return reader.read(is);
        } catch (Exception e) {
            log.error("同梱ゲームデータの読み込みに失敗しました: {}", classpathResource, e);
            return empty;
        }
    }

    /**
     * 外部ファイルの Path を返します（存在するときのみ）。
     * 読込ソースの版判定は含みません。
     *
     * @param relativePath data/ 配下の相対パス
     * @return Path
     */
    public static Optional<Path> findLocal(String relativePath) {
        Path local = GameDataPaths.resolveLocal(relativePath);
        if (Files.isRegularFile(local)) {
            return Optional.of(local);
        }
        return Optional.empty();
    }

    /**
     * 外部データを読込ソースとして使うか判定します。
     * 外部マニフェストが無い／不正な場合は同梱を優先します。
     *
     * @return 外部を使うなら true
     */
    static boolean preferLocalSource() {
        long local = effectiveVersion(loadLocalManifest());
        long bundled = effectiveVersion(loadBundledManifest());
        return preferLocal(local, bundled);
    }

    /**
     * 版比較による外部優先判定（テスト用にも利用）。
     * 版は 1 以上が有効。0 は「無し」扱い。
     *
     * @param local 外部版（無しは 0）
     * @param bundled 同梱版（無しは 0）
     * @return 外部を使うなら true
     */
    static boolean preferLocal(long local, long bundled) {
        if (local <= 0L) {
            return false;
        }
        if (bundled <= 0L) {
            return true;
        }
        return local >= bundled;
    }

    private static long effectiveVersion(Optional<GameDataManifest> manifest) {
        return manifest.map(GameDataManifest::effectiveVersion).orElse(0L);
    }

    /**
     * ローカル外部のマニフェストを読みます。
     *
     * @return マニフェスト（無い・失敗時は empty）
     */
    public static Optional<GameDataManifest> loadLocalManifest() {
        Path path = GameDataPaths.resolveLocal(GameDataPaths.MANIFEST);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(readManifest(path));
        } catch (Exception e) {
            log.warn("ローカルのゲームデータマニフェストの読み込みに失敗しました: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * 同梱マニフェストを読みます（初回のみ実読込。以降はキャッシュ）。
     *
     * @return マニフェスト（無い・失敗時は empty）
     */
    public static Optional<GameDataManifest> loadBundledManifest() {
        if (!bundledResolved) {
            synchronized (BUNDLED_LOCK) {
                if (!bundledResolved) {
                    bundledManifest = readBundledManifestOnce();
                    bundledResolved = true;
                }
            }
        }
        return bundledManifest;
    }

    private static Optional<GameDataManifest> readBundledManifestOnce() {
        try (InputStream is = PluginServices.getResourceAsStream(GameDataPaths.CLASSPATH_MANIFEST)) {
            if (is == null) {
                log.warn("同梱のゲームデータマニフェストが見つかりません: {}", GameDataPaths.CLASSPATH_MANIFEST);
                return Optional.empty();
            }
            return Optional.of(readManifest(is));
        } catch (Exception e) {
            log.warn("同梱のゲームデータマニフェストの読み込みに失敗しました", e);
            return Optional.empty();
        }
    }

    /**
     * ローカルと同梱のうち新しい方のデータ版を返します。
     *
     * @return 現行データ版（どちらも無い場合は 0）
     */
    public static long currentDataVersion() {
        long local = effectiveVersion(loadLocalManifest());
        long bundled = effectiveVersion(loadBundledManifest());
        return Math.max(local, bundled);
    }

    /**
     * Path からマニフェストを読みます。
     *
     * @param path マニフェスト Path
     * @return マニフェスト
     * @throws IOException 読み込み失敗
     */
    public static GameDataManifest readManifest(Path path) throws IOException {
        return JsonMappers.READER_WITH_COMMENTS
                .forType(GameDataManifest.class)
                .readValue(path);
    }

    /**
     * ストリームからマニフェストを読みます。
     *
     * @param is 入力
     * @return マニフェスト
     * @throws IOException 読み込み失敗
     */
    public static GameDataManifest readManifest(InputStream is) throws IOException {
        return JsonMappers.READER_WITH_COMMENTS
                .forType(GameDataManifest.class)
                .readValue(is);
    }
}
