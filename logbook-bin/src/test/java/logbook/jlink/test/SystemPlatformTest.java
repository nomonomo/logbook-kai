package logbook.jlink.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import logbook.internal.SystemPlatform;

/**
 * {@link logbook.internal.SystemPlatform} のテストクラス。
 * 
 * <p>このテストは logbook-bin モジュール（logbook.jlink）で実行されます。
 * logbook-bin は logbook モジュールに依存しているため、
 * logbook モジュールの公開されたクラス（logbook.internal.SystemPlatform）にアクセスできます。</p>
 * 
 * <p>注意: SystemPlatform.getBuildPlatform() は MANIFEST.MF から Build-Platform 属性を読み取ります。
 * モジュールシステムで実行する必要があります。</p>
 */
public class SystemPlatformTest {

    /**
     * {@link logbook.internal.SystemPlatform#getBuildPlatform()} のためのテスト・メソッド。
     * 
     * <p>ビルドプラットフォーム情報が正しく取得できることを確認します。</p>
     */
    @Test
    public void testGetBuildPlatform() {
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // プラットフォーム情報がnullでないことを確認
        assertNotNull(buildPlatform, "SystemPlatform.getBuildPlatform()はnullを返してはいけません");
        
        // プラットフォーム情報が空文字列でないことを確認
        assertTrue(!buildPlatform.isEmpty(), "SystemPlatform.getBuildPlatform()は空文字列を返してはいけません");
        
        // 有効なプラットフォーム値であることを確認
        assertTrue(
            buildPlatform.equals("win") || 
            buildPlatform.equals("mac") || 
            buildPlatform.equals("mac-aarch64") || 
            buildPlatform.equals("linux") || 
            buildPlatform.equals("unknown"),
            "SystemPlatform.getBuildPlatform()は有効なプラットフォーム値を返す必要があります。取得値: " + buildPlatform
        );
    }
    
    /**
     * プラットフォーム情報が一貫して取得できることを確認するテスト。
     * 
     * <p>複数回呼び出しても同じ値が返されることを確認します（キャッシュの動作確認）。</p>
     */
    @Test
    public void testGetBuildPlatformConsistency() {
        String platform1 = SystemPlatform.getBuildPlatform();
        String platform2 = SystemPlatform.getBuildPlatform();
        String platform3 = SystemPlatform.getBuildPlatform();
        
        // すべて同じ値が返されることを確認（キャッシュの動作確認）
        assertTrue(
            platform1.equals(platform2) && platform2.equals(platform3),
            "SystemPlatform.getBuildPlatform()は一貫した値を返す必要があります。platform1=" + platform1 + ", platform2=" + platform2 + ", platform3=" + platform3
        );
    }
    
    /**
     * Windows環境でのプラットフォーム情報が正しく取得できることを確認するテスト。
     * 
     * <p>MANIFEST.MFから取得できない場合のフォールバック動作を確認します。</p>
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testPlatformDetectionOnWindows() {
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // プラットフォーム情報が取得できていることを確認
        assertNotNull(buildPlatform, "プラットフォーム情報が取得できませんでした");
        assertTrue(!buildPlatform.isEmpty(), "プラットフォーム情報が空文字列です");
        
        // Windows環境では "win" が返される可能性が高い
        // （MANIFEST.MFにBuild-Platformが設定されている場合はその値が優先される）
        assertTrue(
            buildPlatform.equals("win") || buildPlatform.equals("unknown"),
            "Windows環境では 'win' または 'unknown' が返される必要があります。取得値: " + buildPlatform
        );
    }
    
    /**
     * Intel Mac環境でのプラットフォーム情報が正しく取得できることを確認するテスト。
     * 
     * <p>MANIFEST.MFから取得できない場合のフォールバック動作を確認します。</p>
     */
    @Test
    @EnabledOnOs(OS.MAC)
    public void testPlatformDetectionOnMac() {
        String buildPlatform = SystemPlatform.getBuildPlatform();
        String osArch = System.getProperty("os.arch").toLowerCase();
        
        // プラットフォーム情報が取得できていることを確認
        assertNotNull(buildPlatform, "プラットフォーム情報が取得できませんでした");
        assertTrue(!buildPlatform.isEmpty(), "プラットフォーム情報が空文字列です");
        
        // os.archでIntel MacとApple Silicon Macを区別
        if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            // Apple Silicon (M1/M2/M3) Mac
            assertTrue(
                buildPlatform.equals("mac-aarch64") || buildPlatform.equals("unknown"),
                "Apple Silicon Mac環境では 'mac-aarch64' または 'unknown' が返される必要があります。取得値: " + buildPlatform + ", os.arch: " + osArch
            );
        } else if (osArch.contains("x86_64")) {
            // Intel Mac
            assertTrue(
                buildPlatform.equals("mac") || buildPlatform.equals("unknown"),
                "Intel Mac環境では 'mac' または 'unknown' が返される必要があります。取得値: " + buildPlatform + ", os.arch: " + osArch
            );
        } else {
            // 不明なアーキテクチャ
            assertTrue(
                buildPlatform.equals("mac") || buildPlatform.equals("mac-aarch64") || buildPlatform.equals("unknown"),
                "Mac環境では 'mac', 'mac-aarch64', または 'unknown' が返される必要があります。取得値: " + buildPlatform + ", os.arch: " + osArch
            );
        }
    }
    
    /**
     * Linux環境でのプラットフォーム情報が正しく取得できることを確認するテスト。
     * 
     * <p>MANIFEST.MFから取得できない場合のフォールバック動作を確認します。</p>
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testPlatformDetectionOnLinux() {
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // プラットフォーム情報が取得できていることを確認
        assertNotNull(buildPlatform, "プラットフォーム情報が取得できませんでした");
        assertTrue(!buildPlatform.isEmpty(), "プラットフォーム情報が空文字列です");
        
        // Linux環境では "linux" が返される可能性が高い
        assertTrue(
            buildPlatform.equals("linux") || buildPlatform.equals("unknown"),
            "Linux環境では 'linux' または 'unknown' が返される必要があります。取得値: " + buildPlatform
        );
    }
    
    /**
     * プラットフォーム判定のロジックをテストします。
     * 
     * <p>os.archプロパティに基づいて、Intel MacとApple Silicon Macを正しく区別できることを確認します。</p>
     */
    @Test
    @EnabledOnOs(OS.MAC)
    public void testPlatformArchitectureDetection() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // os.archの値を確認
        assertNotNull(osArch, "os.archプロパティが取得できませんでした");
        assertTrue(!osArch.isEmpty(), "os.archプロパティが空文字列です");
        
        // os.archに基づいて適切なプラットフォームが判定されることを確認
        if (osArch.equals("aarch64") || osArch.equals("arm64")) {
            // Apple Silicon Mac
            assertEquals("mac-aarch64", buildPlatform, 
                "os.arch=" + osArch + " の場合、プラットフォームは 'mac-aarch64' である必要があります");
        } else if (osArch.equals("x86_64")) {
            // Intel Mac
            assertEquals("mac", buildPlatform, 
                "os.arch=" + osArch + " の場合、プラットフォームは 'mac' である必要があります");
        }
        
        // プラットフォーム情報が有効な値であることを確認
        assertTrue(
            buildPlatform.equals("mac") || buildPlatform.equals("mac-aarch64") || buildPlatform.equals("unknown"),
            "Mac環境では 'mac', 'mac-aarch64', または 'unknown' が返される必要があります。取得値: " + buildPlatform + ", os.arch: " + osArch
        );
    }
    
    /**
     * プラットフォーム判定のロジックをテストします（Windows環境）。
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testPlatformArchitectureDetectionOnWindows() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // os.archの値を確認
        assertNotNull(osArch, "os.archプロパティが取得できませんでした");
        assertTrue(!osArch.isEmpty(), "os.archプロパティが空文字列です");
        
        // Windows環境では "win" が返されることを確認
        assertEquals("win", buildPlatform, 
            "Windows環境ではプラットフォームは 'win' である必要があります。取得値: " + buildPlatform + ", os.arch: " + osArch);
    }
    
    /**
     * プラットフォーム判定のロジックをテストします（Linux環境）。
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testPlatformArchitectureDetectionOnLinux() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String buildPlatform = SystemPlatform.getBuildPlatform();
        
        // os.archの値を確認
        assertNotNull(osArch, "os.archプロパティが取得できませんでした");
        assertTrue(!osArch.isEmpty(), "os.archプロパティが空文字列です");
        
        // Linux環境では "linux" が返されることを確認
        assertEquals("linux", buildPlatform, 
            "Linux環境ではプラットフォームは 'linux' である必要があります。取得値: " + buildPlatform + ", os.arch: " + osArch);
    }
    
    /**
     * OS情報を取得するテスト。
     */
    @Test
    public void testGetOs() {
        SystemPlatform.OsType osType = SystemPlatform.getOs();
        
        // OS情報がnullでないことを確認
        assertNotNull(osType, "SystemPlatform.getOs()はnullを返してはいけません");
        
        // 有効なOS種別であることを確認
        assertTrue(
            osType == SystemPlatform.OsType.WINDOWS ||
            osType == SystemPlatform.OsType.MAC ||
            osType == SystemPlatform.OsType.LINUX ||
            osType == SystemPlatform.OsType.UNKNOWN,
            "SystemPlatform.getOs()は有効なOS種別を返す必要があります。取得値: " + osType
        );
    }
    
    /**
     * OS情報を文字列で取得するテスト。
     */
    @Test
    public void testGetOsString() {
        String osString = SystemPlatform.getOsString();
        
        // OS情報がnullでないことを確認
        assertNotNull(osString, "SystemPlatform.getOsString()はnullを返してはいけません");
        
        // 空文字列でないことを確認
        assertTrue(!osString.isEmpty(), "SystemPlatform.getOsString()は空文字列を返してはいけません");
        
        // 有効なOS文字列であることを確認
        assertTrue(
            osString.equals("Windows") ||
            osString.equals("Mac") ||
            osString.equals("Linux") ||
            osString.equals("Unknown"),
            "SystemPlatform.getOsString()は有効なOS文字列を返す必要があります。取得値: " + osString
        );
    }
    
    /**
     * アーキテクチャ情報を取得するテスト。
     */
    @Test
    public void testGetArch() {
        String arch = SystemPlatform.getArch();
        
        // アーキテクチャ情報がnullでないことを確認
        assertNotNull(arch, "SystemPlatform.getArch()はnullを返してはいけません");
        
        // 空文字列でないことを確認
        assertTrue(!arch.isEmpty(), "SystemPlatform.getArch()は空文字列を返してはいけません");
        
        // os.archプロパティと一致することを確認
        String osArch = System.getProperty("os.arch");
        assertEquals(osArch, arch, 
            "SystemPlatform.getArch()はos.archプロパティと一致する必要があります。取得値: " + arch + ", os.arch: " + osArch);
    }
    
    /**
     * Windows環境でのOS情報が正しく取得できることを確認するテスト。
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testGetOsOnWindows() {
        SystemPlatform.OsType osType = SystemPlatform.getOs();
        String osString = SystemPlatform.getOsString();
        
        // Windows環境では WINDOWS が返されることを確認
        assertEquals(SystemPlatform.OsType.WINDOWS, osType, 
            "Windows環境ではOS種別は WINDOWS である必要があります。取得値: " + osType);
        assertEquals("Windows", osString, 
            "Windows環境ではOS文字列は 'Windows' である必要があります。取得値: " + osString);
    }
    
    /**
     * Mac環境でのOS情報が正しく取得できることを確認するテスト。
     */
    @Test
    @EnabledOnOs(OS.MAC)
    public void testGetOsOnMac() {
        SystemPlatform.OsType osType = SystemPlatform.getOs();
        String osString = SystemPlatform.getOsString();
        
        // Mac環境では MAC が返されることを確認
        assertEquals(SystemPlatform.OsType.MAC, osType, 
            "Mac環境ではOS種別は MAC である必要があります。取得値: " + osType);
        assertEquals("Mac", osString, 
            "Mac環境ではOS文字列は 'Mac' である必要があります。取得値: " + osString);
    }
    
    /**
     * Linux環境でのOS情報が正しく取得できることを確認するテスト。
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testGetOsOnLinux() {
        SystemPlatform.OsType osType = SystemPlatform.getOs();
        String osString = SystemPlatform.getOsString();
        
        // Linux環境では LINUX が返されることを確認
        assertEquals(SystemPlatform.OsType.LINUX, osType, 
            "Linux環境ではOS種別は LINUX である必要があります。取得値: " + osType);
        assertEquals("Linux", osString, 
            "Linux環境ではOS文字列は 'Linux' である必要があります。取得値: " + osString);
    }
}

