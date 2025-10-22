package logbook.plugin.lifecycle;

/**
 * アプリケーション開始時の処理を定義する関数型インターフェースです。<br>
 * <br>
 * 実装クラスはServiceLoaderより取得されます。<br>
 * 実装クラスが呼び出されるようにするには、module-info.java で以下のように宣言してください：
 * <pre>{@code
 * module your.module {
 *     provides logbook.plugin.lifecycle.StartUp with your.package.YourStartUp;
 * }
 * }</pre>
 * <br>
 * <h3>実行環境</h3>
 * <ul>
 * <li>JavaFX Application Thread で実行されます</li>
 * <li>メインウィンドウ（primaryStage）が確実に初期化された後に実行されます</li>
 * <li>UI操作を直接実行できます（Platform.runLater() は不要です）</li>
 * </ul>
 * <br>
 * <h3>実装例</h3>
 * <pre>{@code
 * public class MyStartUp implements StartUp {
 *     @Override
 *     public void run() {
 *         // JavaFX Application Thread で実行される
 *         Stage stage = Main.getPrimaryStage(); // 確実に取得可能
 *         // UI操作を直接実行可能
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface StartUp extends Runnable {

}
