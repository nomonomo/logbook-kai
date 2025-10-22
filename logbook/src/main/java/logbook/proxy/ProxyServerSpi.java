package logbook.proxy;

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

}
