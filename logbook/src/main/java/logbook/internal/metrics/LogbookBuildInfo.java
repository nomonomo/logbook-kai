package logbook.internal.metrics;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import logbook.internal.DevMode;
import logbook.internal.Version;

/**
 * ビルド識別情報 MXBean 実装です。
 */
public final class LogbookBuildInfo implements LogbookBuildInfoMXBean {

    private final ObjectName objectName;
    private final Version version = Version.getCurrent();

    /**
     * ビルド情報 MXBean を生成します。
     * 
     * @throws MalformedObjectNameException ObjectName の生成に失敗した場合
     */
    public LogbookBuildInfo() throws MalformedObjectNameException {
        this.objectName = new ObjectName("logbook:type=BuildInfo");
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
    public int getInfo() {
        return 1;
    }

    @Override
    public String getVersion() {
        return this.version.toBaseString();
    }

    @Override
    public String getBuildTimestamp() {
        return nullToEmpty(this.version.getBuildTimestamp());
    }

    @Override
    public int getDevMode() {
        return DevMode.isEnabled() ? 1 : 0;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
