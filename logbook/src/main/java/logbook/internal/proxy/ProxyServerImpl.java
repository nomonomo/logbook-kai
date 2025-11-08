package logbook.internal.proxy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLHandshakeException;

import logbook.internal.Version;

import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextArea;
import logbook.Messages;
import logbook.bean.AppConfig;
import logbook.internal.gui.ConfigController;
import logbook.internal.gui.InternalFXMLLoader;
import logbook.internal.gui.Main;
import logbook.internal.ssl.SslCertificateUtil;
import logbook.proxy.ConfigReloadResult;
import logbook.proxy.ProxyServerSpi;
import lombok.extern.slf4j.Slf4j;

/**
 * プロキシサーバーです
 *
 */
@Slf4j
public final class ProxyServerImpl implements ProxyServerSpi {

    /** Server */
    private Server server;
    
    /** SSL Context Factory (サーバー証明書用) */
    private SslContextFactory.Server sslContextFactoryServer;
    
    /** SSL Context Factory (クライアント接続用) */
    private SslContextFactory.Client sslContextFactoryClient;
    
    /** SSL証明書エラーの検知カウンター */
    private final AtomicInteger sslCertErrorCount = 
        new AtomicInteger(0);
    
    /** SSL証明書エラーダイアログ表示済みフラグ */
    private final AtomicBoolean sslCertErrorDialogShown = 
        new AtomicBoolean(false);
    
    /** エラーダイアログ表示の閾値（この回数以上検知したらダイアログ表示） */
    private static final int SSL_ERROR_THRESHOLD = 3;

    @Override
    public void run() {
        try {
            // JVMバージョン情報をログ出力
            logJvmInfo();
            
            // 航海日誌のバージョン情報をログ出力（brotli4j情報も含む）
            logApplicationVersion();
            
            // SSL Context Factoryの初期化（証明書の存在確認を含む）
            initializeSslFactories();
            
            // バッファプールの設定（オプション）
            ArrayByteBufferPool bufferPool = new ArrayByteBufferPool(
                0,              // minCapacity
                4096,           // factor
                262144,          // maxCapacity (256KB)
                64,            // maxBucketSize
                0,              // maxHeapMemory (デフォルト)
                0               // maxDirectMemory (デフォルト)
            );
            
            // Serverをバッファプール指定で作成
            // これにより、getServer().getByteBufferPool()でこのバッファプールが返される
            this.server = new Server(null, null, bufferPool);
            
            boolean allowLocalOnly = AppConfig.get()
                    .isAllowOnlyFromLocalhost();

            // The HTTP configuration object.
            HttpConfiguration httpConfig = new HttpConfiguration();

            // The ConnectionFactory for HTTP/1.1.
            HttpConnectionFactory h1 = new HttpConnectionFactory(httpConfig);

            ServerConnector connector = new ServerConnector(server, h1);            

            connector.setPort(AppConfig.get().getListenPort());
            if (allowLocalOnly) {
                connector.setHost("localhost");
            }
            this.server.addConnector(connector);

            // httpsをプロキシできるようにReverseConnectHandlerを設定
            // SSL Context Factoryをコンストラクタで渡す
            ReverseConnectHandler proxy = new ReverseConnectHandler(sslContextFactoryServer, sslContextFactoryClient);
            
            // SSL証明書エラーリスナーをBeanとして登録（Jetty標準のContainerLifeCycleパターン）
            // ReverseConnectHandler.wrapWithSSLForDownstream()がgetBeans()で自動的に取得する
            proxy.addBean(createSslHandshakeListener());
            
            this.server.setHandler(proxy);

            // httpsプロキシを行うためのConnectHandlerを設定（対象を絞っているので必要無いが念のため）
            ConnectHandler proxy2 = new ConnectHandler();
            proxy.setHandler(proxy2);
            
            // httpはこっちのハンドラでプロキシ
            ServletContextHandler context = new ServletContextHandler("/", ServletContextHandler.SESSIONS);

            //ReverseConnectHandler
            //└ServletConextHandler
            //  └ServletHolder
            //    └ReverseProxyServlet.class

            proxy2.setHandler(context);
           
            ServletHolder holder = context.addServlet(ReverseProxyServlet.class, "/*");
            holder.setInitParameter("maxThreads", "256");
            holder.setInitParameter("timeout", "600000");

            try {
                try {
                    this.server.start();
                    this.server.join();
                } finally {
                    try {
                        this.server.stop();
                    } catch (Exception ex) {
                        log.warn("Proxyサーバーのシャットダウンで例外", ex);
                    }
                }
            } catch (Exception e) {
                handleException(e);
            }
        } catch (Exception e) {
            log.error("Proxyサーバーの起動に失敗しました", e);
        }
    }

    /**
     * 設定画面の通信タブを開く
     */
    private static void openConfigCommunicationTab() {
        try {
            // 通信タブのインデックスは3（一般=0, 戦闘・艦隊・艦娘=1, 画像=2, 通信=3）
            ConfigController.openWithTab(Main.getPrimaryStage(), 3);
        } catch (Exception e) {
            log.error("設定画面を開く際にエラーが発生しました", e);
        }
    }

    private static void handleException(Exception e) {
        // Title
        String title = Messages.getString("ProxyServer.7"); //$NON-NLS-1$
        // Message
        StringBuilder sb = new StringBuilder(Messages.getString("ProxyServer.8")); //$NON-NLS-1$
        if (e instanceof BindException) {
            sb.append("\n"); //$NON-NLS-1$
            sb.append(Messages.getString("ProxyServer.10")); //$NON-NLS-1$
        }
        String message = sb.toString();
        // StackTrace
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        String stackTrace = w.toString();

        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            TextArea textArea = new TextArea(stackTrace);
            alert.getDialogPane().setExpandableContent(textArea);

            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        };

        Platform.runLater(runnable);
    }
    
    /**
     * JVMバージョン情報をログ出力する。
     * 起動時に一度だけ呼び出される。
     */
    private static void logJvmInfo() {
        Runtime runtime = Runtime.getRuntime();
        
        // JVMバージョン情報
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String javaVmName = System.getProperty("java.vm.name");
        String javaVmVersion = System.getProperty("java.vm.version");
        String javaVmVendor = System.getProperty("java.vm.vendor");
        
        // OS情報
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        
        // JVMヒープメモリ情報（MB単位）
        // - maxMemory: JVMが使用可能な最大ヒープサイズ（-Xmxで指定された値）
        // - totalMemory: JVMが現在割り当てているヒープサイズ（OSから割り当て済み）
        // - freeMemory: JVMの未使用ヒープメモリ（totalMemory内で未使用）
        // - usedMemory: JVMの使用中ヒープメモリ（totalMemory - freeMemory）
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = (totalMemory - freeMemory);
        
        // ログ出力（INFOレベル）
        log.info("==================== JVM Information ====================");
        log.info("Java Version    : {} ({})", javaVersion, javaVendor);
        log.info("Java VM         : {} {} ({})", javaVmName, javaVmVersion, javaVmVendor);
        log.info("OS              : {} {} ({})", osName, osVersion, osArch);
        log.info("Heap Max        : {} MB ", maxMemory);
        log.info("Heap Total      : {} MB ", totalMemory);
        log.info("Heap Used       : {} MB ", usedMemory);
        log.info("Heap Free       : {} MB ", freeMemory);
    }
    
    /**
     * 航海日誌のバージョン情報をログ出力する。
     * 起動時に一度だけ呼び出される。
     */
    private static void logApplicationVersion() {
        Version version = Version.getCurrent();
        String title = version.getTitle();
        String vendor = version.getVendor();
        
        // Applicationの表示形式: title(vendor) version
        String applicationInfo;
        if (title != null && vendor != null && !vendor.isEmpty()) {
            applicationInfo = String.format("%s(%s) %s", title, vendor, version.toString());
        } else if (title != null) {
            applicationInfo = String.format("%s %s", title, version.toString());
        } else if (vendor != null && !vendor.isEmpty()) {
            applicationInfo = String.format("航海日誌(%s) %s", vendor, version.toString());
        } else {
            applicationInfo = String.format("航海日誌 %s", version.toString());
        }
        
        log.info("================ Application Information ================");
        log.info("Application     : {}", applicationInfo);
        configureBrotliNativeLibraryPath();
        log.info("=========================================================");
    }
    
    /**
     * brotli4jのネイティブライブラリパスを設定する。
     * jlinkで作成されたランタイムイメージの場合、libディレクトリからbrotli.dllを読み込む。
     */
    private static void configureBrotliNativeLibraryPath() {
        // 既に設定されている場合はスキップ（システムプロパティや環境変数で設定済みの場合）
        if (System.getProperty("brotli4j.library.path") != null) {
            log.debug("brotli4j.library.path is already set: {}", System.getProperty("brotli4j.library.path"));
            return;
        }
        
        // java.homeからlibディレクトリのパスを構築
        // jlinkで作成されたランタイムイメージの場合、java.homeはランタイムイメージのルートディレクトリ
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            log.warn("java.home is not set, cannot configure brotli4j.library.path");
            return;
        }
        
        // プラットフォームごとのネイティブライブラリ名を決定
        String osName = System.getProperty("os.name", "").toLowerCase();
        String nativeLibName;
        if (osName.contains("win")) {
            nativeLibName = "brotli.dll";
        } else if (osName.contains("linux")) {
            nativeLibName = "libbrotli.so";
        } else if (osName.contains("mac")) {
            nativeLibName = "libbrotli.dylib";
        } else {
            log.debug("Unsupported OS for brotli native library: {}", osName);
            return;
        }
        
        // libディレクトリのパスを構築
        Path libPath = Paths.get(javaHome, "lib", nativeLibName);
        
        // ファイルが存在するか確認
        if (Files.exists(libPath)) {
            System.setProperty("brotli4j.library.path", libPath.toAbsolutePath().toString());
            log.info("Brotli4j        : Loaded from lib directory: {}", nativeLibName);
        } else {
            log.info("Brotli4j        : Loaded from temp directory: {}", nativeLibName);
        }
    }
    
    /**
     * SSL Context Factoryを初期化する。
     * 証明書ファイルの存在確認と初期化を行う。
     */
    private void initializeSslFactories() {
        sslContextFactoryServer = null;
        sslContextFactoryClient = null;
        
        // Clientファクトリを初期化（ServerFactoryとは独立して動作）
        initializeClientFactory();
        
        // AppConfigから証明書パスを取得
        String configuredPath = AppConfig.get().getServerCertificatePath();
        
        // 証明書ファイルが未設定（空文字列）の場合
        if (configuredPath == null || configuredPath.isEmpty()) {
            log.warn("サーバー証明書ファイルが設定されていません");
            showCertificateNotConfiguredAlert();
            return;
        }
        
        // 証明書ファイルの存在確認
        Path certFile = Paths.get(configuredPath);
        if (!Files.exists(certFile)) {
            log.warn("サーバー証明書ファイルが見つかりません: {}", configuredPath);
            showCertificateNotFoundAlert(configuredPath);
            return;
        }
        
        // 設定されたパスから証明書をロード
        var serverFactory = SslCertificateUtil.tryLoadServerFactory(configuredPath);
        if (serverFactory != null) {
            this.sslContextFactoryServer = serverFactory;
        } else {
            log.error("証明書の読み込みに失敗しました（パス: {}）。SSL接続は利用できません。", configuredPath);
        }
    }
    
    /**
     * 証明書未設定の警告ダイアログを表示する。
     */
    private static void showCertificateNotConfiguredAlert() {
        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle("サーバー証明書ファイルが設定されていません");
            alert.setHeaderText("サーバー証明書ファイルが設定されていません");
            alert.setContentText(
                    "サーバー証明書ファイル（P12形式）が設定されていません。\n\n" +
                    "設定画面の「通信」タブで証明書ファイル（*.p12, *.pfx）を指定してください。");
            alert.showAndWait();
            
            // OK押下後、設定画面の通信タブを開く（別のrunLaterで実行）
            Platform.runLater(() -> openConfigCommunicationTab());
        };
        
        Platform.runLater(runnable);
    }
    
    /**
     * 証明書ファイルが見つからない警告ダイアログを表示する。
     */
    private static void showCertificateNotFoundAlert(String certPath) {
        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle("サーバー証明書ファイルが見つかりません");
            alert.setHeaderText("サーバー証明書ファイルが見つかりません");
            alert.setContentText(
                    "設定で指定されたサーバー証明書ファイルが見つかりませんでした。\n" +
                    "ファイルパス: " + certPath + "\n\n" +
                    "設定画面の「通信」タブで証明書ファイルのパスを確認してください。");
            alert.showAndWait();
            
            // OK押下後、設定画面の通信タブを開く（別のrunLaterで実行）
            Platform.runLater(() -> openConfigCommunicationTab());
        };
        
        Platform.runLater(runnable);
    }
    
    @Override
    public synchronized ConfigReloadResult reloadConfig(AppConfig config) {
        log.info("設定をリロードします");
        
        // 引数から証明書パスを取得
        String certificatePath = config.getServerCertificatePath();
        
        // 証明書パスの検証
        if (certificatePath == null || certificatePath.isEmpty()) {
            return ConfigReloadResult.failure(
                "証明書ファイルパスが設定されていません",
                "serverCertificatePath"
            );
        }
        
        // 証明書ファイルの存在確認
        Path certFile = Paths.get(certificatePath);
        if (!Files.exists(certFile)) {
            return ConfigReloadResult.failure(
                "証明書ファイルが見つかりません: " + certificatePath,
                "serverCertificatePath"
            );
        }
        
        // サーバー起動状態の確認
        if (!isServerRunning()) {
            return ConfigReloadResult.failure(
                "プロキシサーバーが起動していません",
                "proxyServer"
            );
        }
        
        // 証明書をリロード
        this.sslContextFactoryServer = reloadCertificate(certificatePath, this.sslContextFactoryServer);
        
        if (this.sslContextFactoryServer == null) {
            return ConfigReloadResult.failure(
                "証明書の読み込みに失敗しました。ファイルが破損しているか、パスワードが間違っている可能性があります",
                "serverCertificatePath"
            );
        }
        
        updateReverseConnectHandlerSslContext(this.sslContextFactoryServer);
        
        // SSL証明書エラーカウンターをリセット
        resetSslCertificateErrorCounter();
        
        log.info("設定が正常にリロードされました");
        return ConfigReloadResult.success();
    }
    
    /**
     * Clientファクトリを初期化する。
     * ServerFactoryとは独立して動作し、リモートサーバーへの接続用に使用される。
     */
    private void initializeClientFactory() {
        // プロキシとしてリモートサーバー（DMM等）に接続する際、
        // 証明書検証とホスト名検証を無効化する（意図的な設定）
        // checkTrustAll()とcheckEndPointIdentificationAlgorithm()をオーバーライドして警告を抑制
        SslContextFactory.Client clientFactory = new SslContextFactory.Client() {
            @Override
            protected void checkTrustAll() {
                // プロキシとして意図的にTrustAllを使用しているため警告を抑制
            }
            
            @Override
            protected void checkEndPointIdentificationAlgorithm() {
                // プロキシとして意図的にホスト名検証を無効化しているため警告を抑制
            }
        };
        
        clientFactory.setTrustAll(true);
        clientFactory.setEndpointIdentificationAlgorithm(null);
        
        try {
            clientFactory.start();
            this.sslContextFactoryClient = clientFactory;
            log.info("SSLクライアントファクトリを初期化しました（証明書検証: 無効）");
        } catch (Exception e) {
            log.error("SSLクライアントファクトリの初期化に失敗しました", e);
        }
    }
    
    /**
     * プロキシサーバーが起動しているか確認する。
     * 
     * @return サーバーが起動している場合true、それ以外false
     */
    public boolean isServerRunning() {
        return this.server != null && this.server.isStarted();
    }
    
    /**
     * サーバー証明書を再読み込みする。
     * 設定画面で証明書パスが変更された場合に呼び出される。
     * 
     * @param newCertificatePath 新しい証明書ファイルのパス
     * @return 再読み込みに成功した場合true、失敗した場合false
     */
    public synchronized SslContextFactory.Server reloadCertificate(String newCertificatePath, SslContextFactory.Server sslContextFactoryServer) {
        // サーバー起動状態の確認
        if (!isServerRunning()) {
            log.warn("プロキシサーバーが起動していないため、証明書をリロードできません");
            return null;
        }
        
        // SslCertificateUtilに委譲して証明書をリロード
        SslContextFactory.Server newFactory = SslCertificateUtil.reloadServerFactory(
            newCertificatePath, 
            this.sslContextFactoryServer
        );
        
        return newFactory;
    }
    
    /**
     * ReverseConnectHandlerのSslContextFactoryを更新する。
     * 証明書リロード後に呼び出される。
     * sslContextFactoryServerがnullの場合、SSL接続が無効化される。
     */
    private void updateReverseConnectHandlerSslContext(SslContextFactory.Server sslContextFactoryServer) {
        try {
            // ServerからReverseConnectHandlerを取得
            if (this.server != null) {
                var handler = this.server.getHandler();
                if (handler instanceof ReverseConnectHandler reverseHandler) {
                    reverseHandler.setSslContextFactoryServer(sslContextFactoryServer);
                } else {
                    log.warn("Server handler is not ReverseConnectHandler: {}", 
                        handler != null ? handler.getClass().getName() : "null");
                }
            }
        } catch (Exception e) {
            log.error("Failed to update ReverseConnectHandler SSL context", e);
        }
    }
    
    /**
     * SSL証明書エラーを検知するSslHandshakeListenerを作成する。
     * 
     * @return SslHandshakeListenerインスタンス
     */
    private SslHandshakeListener createSslHandshakeListener() {
        return new SslHandshakeListener() {
            @Override
            public void handshakeFailed(Event event, Throwable failure) {
                // SSLHandshakeException以外は無視
                if (!(failure instanceof SSLHandshakeException e)) {
                    return;
                }
                
                // SSLハンドシェイク失敗時は、メッセージ内容に関わらず全てカウント対象
                // （JVM実装やバージョンによってメッセージが異なる可能性があるため）
                handleSslHandshakeError(event, e, sslCertErrorCount.incrementAndGet());
            }
        };
    }
    
    /**
     * SSLハンドシェイクエラーを処理する。
     * ブラウザとのSSL接続確立に失敗した場合に呼び出される。
     * 
     * @param event SSLハンドシェイクイベント
     * @param exception SSLハンドシェイク例外
     * @param errorCount エラー発生回数
     */
    private void handleSslHandshakeError(SslHandshakeListener.Event event, 
                                          SSLHandshakeException exception, 
                                          int errorCount) {
        String message = exception.getMessage();
        
        // 接続先情報を取得（CONNECT先のサーバーアドレス）
        String targetServerAddress = null;
        String clientAddress = "不明";
        
        try {
            if (event.getEndPoint() != null) {
                // 2. ConnectionのAttributesから接続先サーバーアドレスを取得
                var connection = event.getEndPoint().getConnection();
                if (connection instanceof ReverseConnectHandler.DownstreamConnection downstreamConnection) {
                    var attributes = downstreamConnection.getContext();
                    targetServerAddress = (String) attributes.get("targetServerAddress");
                }
                
                // 3. クライアントアドレス（ブラウザのアドレス）をEndPointから取得
                var remoteAddr = event.getEndPoint().getRemoteSocketAddress();
                if (remoteAddr != null) {
                    clientAddress = remoteAddr.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get connection info", e);
        }
        
        // ログ出力：接続先 > クライアントの順で表示
        String connectionInfo = targetServerAddress != null 
            ? targetServerAddress + " (from " + clientAddress + ")"
            : clientAddress;
        
        log.warn("SSLハンドシェイクエラー ({}回目): {} 接続先: {}", 
            errorCount, 
            message != null ? message : "不明なエラー",
            connectionInfo);
        
        // 閾値を超えたら、ダイアログを表示（1度だけ）
        if (errorCount >= SSL_ERROR_THRESHOLD && sslCertErrorDialogShown.compareAndSet(false, true)) {
            showSslCertificateErrorDialog(errorCount);
        }
    }
    
    /**
     * SSL証明書エラーカウンターをリセットする。
     * 証明書リロード時に呼び出される。
     */
    private void resetSslCertificateErrorCounter() {
        int previousCount = sslCertErrorCount.getAndSet(0);
        boolean wasDialogShown = sslCertErrorDialogShown.getAndSet(false);
        
        if (previousCount > 0 || wasDialogShown) {
            log.info("SSL証明書エラーカウンターをリセットしました " +
                "(以前のカウント: {}, ダイアログ表示済み: {})", previousCount, wasDialogShown);
        }
    }
    
    /**
     * SSL証明書エラーダイアログを表示する。
     * JavaFXスレッドで実行される。
     */
    private static void showSslCertificateErrorDialog(int errorCount) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(AlertType.WARNING);
                alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
                InternalFXMLLoader.setGlobal(alert.getDialogPane());
                alert.initOwner(Main.getPrimaryStage());
                
                alert.setTitle("SSL証明書エラー");
                alert.setHeaderText("ルート証明書がインストールされていません");
                alert.setContentText(
                    "ブラウザがサーバー証明書を信頼していません。\n" +
                    "（" + errorCount + "回のエラーを検知しました）\n\n" +
                    "以下の手順でルート証明書をインストールしてください：\n\n" +
                    "1. 証明書作成画面で「logbook-ca.crt」を作成\n" +
                    "2. ブラウザに logbook-ca.crt をインポート\n" +
                    "   （「信頼されたルート証明機関」として登録）\n" +
                    "3. ブラウザを再起動\n\n" +
                    "詳細は航海日誌のドキュメントを参照してください。");
                
                alert.showAndWait();
            } catch (Exception e) {
                log.warn("SSL証明書エラーダイアログの表示に失敗しました", e);
            }
        });
    }
}
