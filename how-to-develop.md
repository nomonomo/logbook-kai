# プラグイン開発

航海日誌では、プラグイン開発のために次のインターフェイスを用意しています。

実装クラスは [ServiceLoader](https://docs.oracle.com/javase/jp/25/docs/api/java.base/java/util/ServiceLoader.html) で取得します。取得の仕組みは変わっていません。

登録は、プラグインの `module-info.java` に `provides` を書きます。`META-INF/services/インターフェイス名` に実装クラスの完全修飾名を書くのは、旧来の方法です。

```java
module your.module {
    provides logbook.plugin.lifecycle.StartUp with your.package.YourStartUp;
}
```

## 外部向けインターフェイス

起動時に1度だけ行う処理
 * [logbook.plugin.lifecycle.StartUp](logbook/src/main/java/logbook/plugin/lifecycle/StartUp.java)

メイン画面へのGUI要素の追加
 * [logbook.plugin.gui.MainCalcMenu](logbook/src/main/java/logbook/plugin/gui/MainCalcMenu.java)
 * [logbook.plugin.gui.MainCommandMenu](logbook/src/main/java/logbook/plugin/gui/MainCommandMenu.java)
 * [logbook.plugin.gui.MainExtMenu](logbook/src/main/java/logbook/plugin/gui/MainExtMenu.java)

艦隊タブの注釈へのGUI要素の追加
 * [logbook.plugin.gui.FleetTabRemark](logbook/src/main/java/logbook/plugin/gui/FleetTabRemark.java)

通信のキャプチャ
 * [logbook.proxy.ContentListenerSpi](logbook/src/main/java/logbook/proxy/ContentListenerSpi.java)
  * 関連クラス
    * [logbook.proxy.RequestMetaData](logbook/src/main/java/logbook/proxy/RequestMetaData.java)
    * [logbook.proxy.ResponseMetaData](logbook/src/main/java/logbook/proxy/ResponseMetaData.java)

APIのキャプチャ
 * [logbook.api.APIListenerSpi](logbook/src/main/java/logbook/api/APIListenerSpi.java)

プロキシサーバー
 * [logbook.proxy.ProxyServerSpi](logbook/src/main/java/logbook/proxy/ProxyServerSpi.java)
  * プロキシサーバーの実装が複数存在する場合、デフォルト実装以外の実装を1つだけ選択します。実装の選択方法は未定義です。
  * [デフォルト実装](logbook/src/main/java/logbook/internal/proxy/ProxyServerImpl.java)

通知
 * [logbook.notify.NotificationListener](logbook/src/main/java/logbook/notify/NotificationListener.java)
  * 発行された通知はすべて渡ります。出すかはリスナーが決めます。
  * 遠征完了の再通知だけは、10秒以上3600秒以下の間隔を返したリスナーに渡します。
  * 通知の種類は [logbook.notify.Notification](logbook/src/main/java/logbook/notify/Notification.java) を見てください。
