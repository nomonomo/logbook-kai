package logbook.jlink.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import logbook.internal.Version;

/**
 * {@link logbook.internal.Version} のテストクラス。
 * 
 * <p>このテストは logbook-bin モジュール（logbook.jlink）で実行されます。
 * logbook-bin は logbook モジュールに依存しているため、
 * logbook モジュールの公開されたクラス（logbook.internal.Version）にアクセスできます。</p>
 * 
 * <p>注意: Version.getCurrent() は ModuleDescriptor に依存しているため、
 * モジュールシステムで実行する必要があります。</p>
 */
public class VersionTest {

    /**
     * {@link logbook.internal.Version#Version(int, int, int)} のためのテスト・メソッド。
     */
    @Test
    public void testVersionIntIntInt() {
        Version v = new Version(3, 4, 5);
        assertEquals(3, v.getMajor());
        assertEquals(4, v.getMinor());
        assertEquals(5, v.getRevision());
    }

    /**
     * {@link logbook.internal.Version#Version(java.lang.String)} のためのテスト・メソッド。
     */
    @Test
    public void testVersionString() {
        {
            Version v = new Version("5");
            assertEquals(5, v.getMajor());
            assertEquals(0, v.getMinor());
            assertEquals(0, v.getRevision());
        }
        {
            Version v = new Version("5.6");
            assertEquals(5, v.getMajor());
            assertEquals(6, v.getMinor());
            assertEquals(0, v.getRevision());
        }
        {
            Version v = new Version("5.6.7");
            assertEquals(5, v.getMajor());
            assertEquals(6, v.getMinor());
            assertEquals(7, v.getRevision());
        }
        {
            Version v = new Version("5.0.0");
            assertEquals(5, v.getMajor());
            assertEquals(0, v.getMinor());
            assertEquals(0, v.getRevision());
        }
    }

    /**
     * {@link logbook.internal.Version#compareTo(logbook.internal.Version)} のためのテスト・メソッド。
     */
    @Test
    public void testCompareTo() {
        {
            Version v1 = new Version("1.2.2");
            Version v2 = new Version("2.2.2");

            assertEquals(-1, v1.compareTo(v2));
        }
        {
            Version v1 = new Version("1.2.2");
            Version v2 = new Version("1.2.2");

            assertEquals(0, v1.compareTo(v2));
        }
        {
            Version v1 = new Version("2.2.2");
            Version v2 = new Version("1.2.2");

            assertEquals(1, v1.compareTo(v2));
        }
        {
            Version v1 = new Version("2.1.2");
            Version v2 = new Version("2.2.2");

            assertEquals(-1, v1.compareTo(v2));
        }
        {
            Version v1 = new Version("2.2.2");
            Version v2 = new Version("2.1.2");

            assertEquals(1, v1.compareTo(v2));
        }
        {
            Version v1 = new Version("2.2.1");
            Version v2 = new Version("2.2.2");

            assertEquals(-1, v1.compareTo(v2));
        }
        {
            Version v1 = new Version("2.2.2");
            Version v2 = new Version("2.2.1");

            assertEquals(1, v1.compareTo(v2));
        }
    }

    /**
     * {@link logbook.internal.Version#getCurrent()} のためのテスト・メソッド。
     * 
     * <p>このテストはモジュールシステムで実行されるため、
     * Version.getCurrent() は ModuleDescriptor からバージョン情報を取得できます。</p>
     */
    @Test
    public void testGetCurrent() {
        // モジュールシステムで実行されるため、UNKNOWN以外の値が返される可能性がある
        Version current = Version.getCurrent();
        
        // 少なくともVersionオブジェクトが返されることを確認
        assertEquals(Version.class, current.getClass(), "Version.getCurrent()はVersionオブジェクトを返していません");
        
        // UNKNOWNの場合、テストを失敗させる
        assertNotEquals(Version.UNKNOWN, current, "Version.getCurrent()はUNKNOWNを返してはいけません");
        
        // バージョン情報が取得できている場合、majorがInteger.MAX_VALUEでないことを確認
        assertEquals(false, current.getMajor() == Integer.MAX_VALUE, "majorがInteger.MAX_VALUEであってはいけません");
    }

}
