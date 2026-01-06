package logbook.internal.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Disabled;

/**
 * {@link logbook.internal.log.LogWriter} のテストクラス。
 */
@Disabled
public class LogWriterTest {

    /**
     * {@link logbook.internal.log.LogWriter#DEFAULT_CHARSET} のためのテスト・メソッド（Windows用）。
     * 
     * <p>Windowsの場合、MS932（またはwindows-31j）であることを確認します。</p>
     * 
     * <p>注意: Javaでは、MS932とwindows-31jは同じ文字セットですが、
     * Charset.name()はwindows-31jを返すことがあります。</p>
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testDefaultCharsetWindows() {
        // DEFAULT_CHARSETがnullでないことを確認
        assertNotNull(LogWriter.DEFAULT_CHARSET, "DEFAULT_CHARSETはnullであってはなりません");
        
        // Windowsの場合、MS932（またはwindows-31j）であることを確認
        // MS932とwindows-31jは同じ文字セットの別名
        Charset ms932 = Charset.forName("MS932");
        Charset windows31j = Charset.forName("windows-31j");
        
        // DEFAULT_CHARSETがMS932またはwindows-31jと等しいことを確認
        boolean isCompatible = LogWriter.DEFAULT_CHARSET.equals(ms932) 
            || LogWriter.DEFAULT_CHARSET.equals(windows31j);
        
        assertEquals(true, isCompatible, 
            "Windows環境ではDEFAULT_CHARSETはMS932（またはwindows-31j）である必要があります。実際の値: " + LogWriter.DEFAULT_CHARSET.name());
    }

    /**
     * {@link logbook.internal.log.LogWriter#DEFAULT_CHARSET} のためのテスト・メソッド（Windows以外用）。
     * 
     * <p>Windows以外の場合、UTF-8であることを確認します。</p>
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testDefaultCharsetNonWindows() {
        // DEFAULT_CHARSETがnullでないことを確認
        assertNotNull(LogWriter.DEFAULT_CHARSET, "DEFAULT_CHARSETはnullであってはなりません");
        
        // Windows以外の場合、UTF-8であることを確認
        Charset expected = StandardCharsets.UTF_8;
        assertEquals(expected, LogWriter.DEFAULT_CHARSET, 
            "Windows以外の環境ではDEFAULT_CHARSETはUTF-8である必要があります。実際の値: " + LogWriter.DEFAULT_CHARSET.name());
    }
}

