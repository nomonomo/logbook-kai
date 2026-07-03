package logbook.internal.metrics;

import java.lang.management.ManagementFactory;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import logbook.bean.AppConfig;
import logbook.internal.proxy.ProxyHolder;
import logbook.internal.proxy.ProxyServerImpl;
import logbook.plugin.PluginContainer;
import logbook.proxy.ProxyServerSpi;

/**
 * アプリケーション実行状態 MXBean 実装です。
 */
public final class LogbookMetrics implements LogbookMetricsMXBean {

    private final ObjectName objectName;
    private final long startTimeMillis = ManagementFactory.getRuntimeMXBean().getStartTime();

    /**
     * メトリクス MXBean を生成します。
     *
     * @throws MalformedObjectNameException ObjectName の生成に失敗した場合
     */
    public LogbookMetrics() throws MalformedObjectNameException {
        this.objectName = new ObjectName("logbook:type=ApplicationMetrics");
    }

    /**
     * 登録用の ObjectName を返します。
     *
     * @return ObjectName
     */
    public ObjectName getObjectName() {
        return this.objectName;
    }

    @Override
    public long getUptimeSeconds() {
        long uptimeMillis = System.currentTimeMillis() - this.startTimeMillis;
        return Math.max(0L, uptimeMillis / 1000L);
    }

    @Override
    public int getListenPort() {
        try {
            return AppConfig.get().getListenPort();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public int getServerRunning() {
        try {
            ProxyServerSpi proxyServer = ProxyHolder.getProxyServerInstance();
            if (proxyServer instanceof ProxyServerImpl impl) {
                return impl.isServerRunning() ? 1 : 0;
            }
            Thread proxyThread = ProxyHolder.getInstance();
            return proxyThread != null && proxyThread.isAlive() ? 1 : 0;
        } catch (IllegalStateException | ExceptionInInitializerError | NoClassDefFoundError e) {
            return 0;
        }
    }

    @Override
    public int getPluginCount() {
        try {
            return PluginContainer.getInstance().getPlugins().size();
        } catch (IllegalStateException e) {
            return 0;
        }
    }
}
