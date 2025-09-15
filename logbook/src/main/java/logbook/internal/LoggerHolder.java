package logbook.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ロギング
 *
 */
public class LoggerHolder {

    public static LoggerProxy get() {
        return new LoggerProxy();
    }

    public static class LoggerProxy {

        private String callerClass;

        private Logger logger;

        public LoggerProxy() {
            this.callerClass = getCallerClass(3);
            this.logger = LoggerFactory.getLogger(this.callerClass);
        }

        public boolean isDebugEnabled() {
            return this.logger.isDebugEnabled();
        }

        public void debug(String message) {
            this.logger.debug(message);
        }

        public void debug(String message, Object... params) {
            this.logger.debug(message, params);
        }

        public void debug(String message, Throwable t) {
            this.logger.debug(message, t);
        }

        public void info(String message) {
            this.logger.info(message);
        }

        public void info(String message, Object... params) {
            this.logger.info(message, params);
        }

        public void info(String message, Throwable t) {
            this.logger.info(message, t);
        }

        public void warn(String message) {
            this.logger.warn(message);
        }

        public void warn(String message, Object... params) {
            this.logger.warn(message, params);
        }

        public void warn(String message, Throwable t) {
            this.logger.warn(message, t);
        }

        public void error(String message) {
            this.logger.error(message);
        }

        public void error(String message, Object... params) {
            this.logger.error(message, params);
        }

        public void error(String message, Throwable t) {
            this.logger.error(message, t);
        }

    }

    static String getCallerClass(final int depth) {
        return getEquivalentStackTraceElement(depth + 1).getClassName();
    }

    private static StackTraceElement getEquivalentStackTraceElement(final int depth) {
        final StackTraceElement[] elements = new Throwable().getStackTrace();
        int i = 0;
        for (final StackTraceElement element : elements) {
            if (isValid(element)) {
                if (i == depth) {
                    return element;
                }
                ++i;
            }
        }
        return null;
    }

    private static boolean isValid(final StackTraceElement element) {
        if (element.isNativeMethod()) {
            return false;
        }
        final String cn = element.getClassName();
        if (cn.startsWith("sun.reflect.")) {
            return false;
        }
        final String mn = element.getMethodName();
        if (cn.startsWith("java.lang.reflect.") && (mn.equals("invoke") || mn.equals("newInstance"))) {
            return false;
        }
        if (cn.equals("java.lang.Class") && mn.equals("newInstance")) {
            return false;
        }
        if (cn.equals("java.lang.invoke.MethodHandle") && mn.startsWith("invoke")) {
            return false;
        }
        return true;
    }
}
