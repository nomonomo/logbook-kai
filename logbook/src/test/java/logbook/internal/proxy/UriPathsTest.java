package logbook.internal.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link UriPaths} のテスト。
 */
class UriPathsTest {

    @Test
    void normalizeNullOrEmpty() {
        assertEquals("/", UriPaths.normalize(null));
        assertEquals("/", UriPaths.normalize(""));
    }

    @Test
    void normalizeStripsQueryAndFragment() {
        assertEquals("/kcsapi/api_port/port", UriPaths.normalize("/kcsapi/api_port/port?x=1"));
        assertEquals("/kcsapi/api_port/port", UriPaths.normalize("/kcsapi/api_port/port#anchor"));
        assertEquals("/kcsapi/foo", UriPaths.normalize("/kcsapi/foo?a=1#b"));
    }

    @Test
    void normalizeAbsoluteUrl() {
        assertEquals("/kcsapi/api_port/port", UriPaths.normalize("https://host/kcsapi/api_port/port?q=1"));
    }

    @Test
    void normalizePathOnly() {
        assertEquals("/kcsapi/api_port/port", UriPaths.normalize("/kcsapi/api_port/port"));
    }
}
