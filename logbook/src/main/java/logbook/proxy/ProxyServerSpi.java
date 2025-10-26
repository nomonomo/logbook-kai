package logbook.proxy;

import logbook.bean.AppConfig;

/**
 * プロキシサーバーの実装を定義するインターフェースです。<br>
 * <br>
 * 実装クラスはServiceLoaderより取得されます。<br>
 * 実装クラスが呼び出されるようにするには、module-info.java で以下のように宣言してください：
 * <pre>{@code
 * module your.module {
 *     provides logbook.proxy.ProxyServerSpi with your.package.YourProxyServer;
 * }
 * }</pre>
 * <br>
 * <h3>実行環境</h3>
 * <ul>
 * <li>デーモンスレッドとして実行されます</li>
 * <li>アプリケーション終了時にスレッドに対して割り込みを行います</li>
 * <li>実装は割り込みに対して適切に終了処理を行う必要があります</li>
 * </ul>
 */
public interface ProxyServerSpi extends Runnable {
    
    /**
     * 設定を再読み込みする。
     * サーバーを再起動せずに、実行時に設定を反映させる。
     * 
     * @param config 適用するアプリケーション設定（変更途中のデータも渡せる）
     * @return 再読み込みの結果（成功または失敗の詳細情報を含む）
     */
    default ConfigReloadResult reloadConfig(AppConfig config) {
        // デフォルト実装（何もしない）
        // プラグインで実装されていない場合は失敗を返す
        return ConfigReloadResult.failure("プラグインで実装されていません");
    }

}
