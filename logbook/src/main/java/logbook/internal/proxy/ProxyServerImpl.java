package logbook.internal.proxy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
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
import logbook.internal.ssl.CertificateService;
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

    private final CertificateService certificateService = new CertificateService();
    
    private record CertificatePathSelection(String certificatePath, boolean useRootCertificate) {}

    @Override
    public void run() {
        try {
            // JVMバージョン情報をログ出力
            logJvmInfo();
            
            // 航海日誌のバージョン情報をログ出力
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
           
            // proxy.pacファイルを返すservletを登録（/*より前に登録する必要がある）
            context.addServlet(ProxyPacServlet.class, "/proxy.pac");
            
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
        log.info("=========================================================");
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
        AppConfig config = AppConfig.get();
        CertificatePathSelection selection = selectCertificatePath(config);
        if (selection == null) {
            if (Boolean.TRUE.equals(config.getProxySslUseRootCertificate())) {
                log.warn("ルート証明書ファイルのパスが未設定または空です（使用証明書: ルート証明書）");
                showRootCertificateNotConfiguredAlert();
            } else {
                log.warn("サーバー証明書ファイルのパスが未設定または空です（使用証明書: サーバー証明書）");
                showServerCertificateNotConfiguredAlert();
            }
            return;
        }
        
        Path certFile = Paths.get(selection.certificatePath());
        if (!Files.exists(certFile)) {
            if (selection.useRootCertificate()) {
                log.warn("ルート証明書ファイルが見つかりません: {}", selection.certificatePath());
                showRootCertificateNotFoundAlert(selection.certificatePath());
            } else {
                log.warn("サーバー証明書ファイルが見つかりません: {}", selection.certificatePath());
                showServerCertificateNotFoundAlert(selection.certificatePath());
            }
            return;
        }
        
        if (selection.useRootCertificate()) {
            this.sslContextFactoryServer = createServerFactoryFromRootCertificate(selection.certificatePath());
            if (this.sslContextFactoryServer == null) {
                log.error("証明書の初期化に失敗しました（ルート証明書: {}）。SSL接続は利用できません。", selection.certificatePath());
            }
        } else {
            String serverCertificatePath = selection.certificatePath();
            this.sslContextFactoryServer = loadServerFactoryFromServerCertificate(serverCertificatePath);
            if (this.sslContextFactoryServer == null) {
                log.error("証明書の初期化に失敗しました（サーバー証明書: {}）。SSL接続は利用できません。", serverCertificatePath);
            } else {
                warnServerCertificateExpiryIfNeeded(serverCertificatePath, this.sslContextFactoryServer);
            }
        }
    }

    /**
     * サーバー証明書（従来方式）の有効期限を評価し、警告閾値以内ならダイアログを表示する。
     */
    private static void warnServerCertificateExpiryIfNeeded(String serverCertificatePath,
            SslContextFactory.Server serverFactory) {
        if (serverFactory == null) {
            return;
        }
        /** サーバー証明書（従来方式）の有効期限警告閾値（日） */
        int serverCertificateExpiryWarningDays = 30;

        SslCertificateUtil.getServerCertificateNotAfter(serverFactory).ifPresent(notAfter -> {
            if (SslCertificateUtil.isServerCertificateExpiringWithinDays(
                    notAfter, serverCertificateExpiryWarningDays)) {
                log.warn("サーバー証明書の有効期限が{}日以内です: {}（有効期限: {}）",
                        serverCertificateExpiryWarningDays,
                        serverCertificatePath,
                        notAfter);
                showServerCertificateExpiringSoonAlert(serverCertificatePath, notAfter);
            }
        });
    }

    private CertificatePathSelection selectCertificatePath(AppConfig config) {
        boolean useRoot = Boolean.TRUE.equals(config.getProxySslUseRootCertificate());
        if (useRoot) {
            String rootCertificateConfiguredPath = config.getRootCertificatePath();
            if (rootCertificateConfiguredPath != null && !rootCertificateConfiguredPath.isEmpty()) {
                return new CertificatePathSelection(rootCertificateConfiguredPath, true);
            }
            return null;
        }
        String serverCertificateConfiguredPath = config.getServerCertificatePath();
        if (serverCertificateConfiguredPath != null && !serverCertificateConfiguredPath.isEmpty()) {
            return new CertificatePathSelection(serverCertificateConfiguredPath, false);
        }
        return null;
    }
    
    /**
     * ルート証明書未設定の警告ダイアログを表示する。
     */
    private static void showRootCertificateNotConfiguredAlert() {
        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle("ルート証明書ファイルが設定されていません");
            alert.setHeaderText("ルート証明書ファイルが設定されていません");
            alert.setContentText(
                    "「証明書：使用証明書」でルート証明書を選んでいる場合、ルート証明書ファイル（P12形式）のパスが必要です。\n\n" +
                    "設定画面の「通信」タブでルート証明書ファイル（*.p12, *.pfx）を指定してください。");
            alert.showAndWait();
            
            // OK押下後、設定画面の通信タブを開く（別のrunLaterで実行）
            Platform.runLater(() -> openConfigCommunicationTab());
        };
        
        Platform.runLater(runnable);
    }

    /**
     * サーバー証明書未設定の警告ダイアログを表示する。
     */
    private static void showServerCertificateNotConfiguredAlert() {
        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle("サーバー証明書ファイルが設定されていません");
            alert.setHeaderText("サーバー証明書ファイルが設定されていません");
            alert.setContentText(
                    "「証明書：使用証明書」でサーバー証明書（従来方式）を選んでいる場合、サーバー証明書ファイル（P12形式）のパスが必要です。\n\n" +
                    "設定画面の「通信」タブでサーバー証明書ファイル（*.p12, *.pfx）を指定してください。");
            alert.showAndWait();

            Platform.runLater(() -> openConfigCommunicationTab());
        };

        Platform.runLater(runnable);
    }
    
    /**
     * ルート証明書ファイルが見つからない警告ダイアログを表示する。
     */
    private static void showRootCertificateNotFoundAlert(String certPath) {
        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle("ルート証明書ファイルが見つかりません");
            alert.setHeaderText("ルート証明書ファイルが見つかりません");
            alert.setContentText(
                    "設定で指定されたルート証明書ファイルが見つかりませんでした。\n" +
                    "ファイルパス: " + certPath + "\n\n" +
                    "設定画面の「通信」タブで証明書ファイルのパスを確認してください。");
            alert.showAndWait();
            
            // OK押下後、設定画面の通信タブを開く（別のrunLaterで実行）
            Platform.runLater(() -> openConfigCommunicationTab());
        };
        
        Platform.runLater(runnable);
    }

    /**
     * サーバー証明書の有効期限が近い場合の警告ダイアログを表示する。
     */
    private static void showServerCertificateExpiringSoonAlert(String certPath, Date notAfter) {
        String notAfterText = notAfter != null ? notAfter.toString() : "不明";

        Runnable runnable = () -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
            InternalFXMLLoader.setGlobal(alert.getDialogPane());
            alert.initOwner(Main.getPrimaryStage());
            alert.setTitle("サーバー証明書の有効期限が近づいています");
            alert.setHeaderText("サーバー証明書の有効期限が近づいています");
            alert.setContentText(
                    "サーバー証明書（従来方式）の有効期限が30日以内です。\n\n" +
                    "ファイル: " + certPath + "\n" +
                    "有効期限: " + notAfterText + "\n\n" +
                    "ルート証明書を使用する方式に変更すると、サーバー証明書の更新が不要になります。\n" +
                    "設定画面の「通信」タブで「使用証明書」を「ルート証明書」に変更し、\n" +
                    "ルート証明書ファイル（logbook-ca.p12）を指定してください。");
            alert.showAndWait();

            Platform.runLater(() -> openConfigCommunicationTab());
        };

        Platform.runLater(runnable);
    }

    /**
     * サーバー証明書ファイルが見つからない警告ダイアログを表示する。
     */
    private static void showServerCertificateNotFoundAlert(String certPath) {
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
        CertificatePathSelection selection = selectCertificatePath(config);
        if (selection == null) {
            if (Boolean.TRUE.equals(config.getProxySslUseRootCertificate())) {
                return ConfigReloadResult.failure(
                    "ルート証明書ファイルパスが設定されていないか空です",
                    "rootCertificatePath"
                );
            }
            return ConfigReloadResult.failure(
                "サーバー証明書ファイルパスが設定されていないか空です",
                "serverCertificatePath"
            );
        }
        
        Path certFile = Paths.get(selection.certificatePath());
        if (!Files.exists(certFile)) {
            if (selection.useRootCertificate()) {
                return ConfigReloadResult.failure(
                    "ルート証明書ファイルが見つかりません: " + selection.certificatePath(),
                    "rootCertificatePath"
                );
            }
            return ConfigReloadResult.failure(
                "サーバー証明書ファイルが見つかりません: " + selection.certificatePath(),
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
        this.sslContextFactoryServer = reloadCertificate(config, this.sslContextFactoryServer);
        
        if (this.sslContextFactoryServer == null) {
            return ConfigReloadResult.failure(
                "証明書の読み込みに失敗しました。ファイルが破損しているか、パスワードが間違っている可能性があります",
                selection.useRootCertificate() ? "rootCertificatePath" : "serverCertificatePath"
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
    public synchronized SslContextFactory.Server reloadCertificate(AppConfig config, SslContextFactory.Server sslContextFactoryServer) {
        // サーバー起動状態の確認
        if (!isServerRunning()) {
            log.warn("プロキシサーバーが起動していないため、証明書をリロードできません");
            return null;
        }
        CertificatePathSelection selection = selectCertificatePath(config);
        if (selection == null) {
            return null;
        }
        if (selection.useRootCertificate()) {
            return createServerFactoryFromRootCertificate(selection.certificatePath());
        }
        return loadServerFactoryFromServerCertificate(selection.certificatePath());
    }

    private SslContextFactory.Server createServerFactoryFromRootCertificate(String rootCertificatePath) {
        try {
            log.info("ルート証明書からサーバー証明書をメモリ生成します: {}", rootCertificatePath);
            return this.certificateService.createServerFactoryFromRootCertificate(rootCertificatePath);
        } catch (Exception e) {
            log.error("ルート証明書からのサーバー証明書メモリ生成に失敗しました: {}", rootCertificatePath, e);
            return null;
        }
    }

    private SslContextFactory.Server loadServerFactoryFromServerCertificate(String serverCertificatePath) {
        if (serverCertificatePath == null || serverCertificatePath.isEmpty()) {
            return null;
        }
        Path serverPath = Paths.get(serverCertificatePath);
        if (!Files.exists(serverPath)) {
            return null;
        }
        log.info("サーバー証明書ファイルを直接読み込みます: {}", serverCertificatePath);
        return SslCertificateUtil.tryLoadServerFactory(serverCertificatePath);
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
