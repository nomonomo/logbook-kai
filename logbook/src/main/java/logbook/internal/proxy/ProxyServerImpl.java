package logbook.internal.proxy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ConnectHandler;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextArea;
import logbook.Messages;
import logbook.bean.AppConfig;
import logbook.internal.LoggerHolder;
import logbook.internal.gui.InternalFXMLLoader;
import logbook.internal.gui.Main;
import logbook.proxy.ProxyServerSpi;

/**
 * プロキシサーバーです
 *
 */
public final class ProxyServerImpl implements ProxyServerSpi {

    /** Server */
    private Server server;

    @Override
    public void run() {
        try {
            this.server = new Server();

            boolean allowLocalOnly = AppConfig.get()
                    .isAllowOnlyFromLocalhost();

            // The HTTP configuration object.
            HttpConfiguration httpConfig = new HttpConfiguration();

            // SecureRequestCustomizer(false) -> sniHostCheck
            // Add the SecureRequestCustomizer because TLS is used.
            httpConfig.addCustomizer(new SecureRequestCustomizer(false));

            // The ConnectionFactory for HTTP/1.1.
            HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);

            ServerConnector connector = new ServerConnector(server, http11);            

            connector.setPort(AppConfig.get().getListenPort());
            if (allowLocalOnly) {
                connector.setHost("localhost");
            }
            this.server.addConnector(connector);

            // httpsをプロキシできるようにConnectHandlerを設定（暫定）
            ConnectHandler proxy = new ConnectHandler();
            this.server.setHandler(proxy);
            
            // httpはこっちのハンドラでプロキシ
            ServletContextHandler context = new ServletContextHandler("/", ServletContextHandler.SESSIONS);

            //ConnectHandler
            //└ServletConextHandler
            //  └ServletHolder
            //    └ReverseProxyServlet.class

            proxy.setHandler(context);
           
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
                        LoggerHolder.get().warn("Proxyサーバーのシャットダウンで例外", ex);
                    }
                }
            } catch (Exception e) {
                handleException(e);
            }
        } catch (Exception e) {
            LoggerHolder.get().error("Proxyサーバーの起動に失敗しました", e);
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
}
