package logbook.internal.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * {@link UiFonts} のテスト。
 */
class UiFontsTest {

    @Test
    void defaultFamily_windows() {
        assertEquals("Yu Gothic UI", UiFonts.defaultFamily("Windows 10"));
    }

    @Test
    void defaultFamily_mac() {
        assertEquals("Hiragino Maru Gothic ProN", UiFonts.defaultFamily("Mac OS X"));
    }

    @Test
    void defaultFamily_linux() {
        assertEquals("Yu Gothic UI", UiFonts.defaultFamily("Linux"));
    }

    @Test
    void defaultFamily_null() {
        assertEquals("Yu Gothic UI", UiFonts.defaultFamily(null));
    }

    @Test
    void defaultChoiceLabel_containsDefaultFamily() {
        assertEquals("既定（" + UiFonts.defaultFamily() + "）", UiFonts.defaultChoiceLabel());
    }

    @Test
    void canDisplayJapanese_unknownFamily_false() {
        assertFalse(UiFonts.canDisplayJapanese("NoSuchFont_XYZ_12345"));
    }

    @Test
    void canDisplayJapanese_nullAndBlank_false() {
        assertFalse(UiFonts.canDisplayJapanese(null));
        assertFalse(UiFonts.canDisplayJapanese(""));
        assertFalse(UiFonts.canDisplayJapanese("   "));
    }

    @Test
    void storedFamily_defaultAndBlank_empty() {
        assertEquals("", UiFonts.storedFamily(null));
        assertEquals("", UiFonts.storedFamily("  "));
        assertEquals("", UiFonts.storedFamily(UiFonts.defaultChoiceLabel()));
    }

    @Test
    void storedFamily_trimsValue() {
        assertEquals("Yu Gothic UI", UiFonts.storedFamily(" Yu Gothic UI "));
    }

    @Test
    void resolveFamily_blankUsesDefault() {
        assertEquals(UiFonts.defaultFamily(), UiFonts.resolveFamily(null));
        assertEquals(UiFonts.defaultFamily(), UiFonts.resolveFamily(""));
        assertEquals(UiFonts.defaultFamily(), UiFonts.resolveFamily("  "));
        assertEquals(UiFonts.defaultFamily(), UiFonts.resolveFamily(UiFonts.defaultChoiceLabel()));
    }

    @Test
    void resolveFamily_stripsQuotes() {
        assertEquals("Meiryo UI", UiFonts.resolveFamily("Meiryo \"UI"));
        assertEquals(UiFonts.defaultFamily(), UiFonts.resolveFamily("\"\""));
    }

    @Test
    void rootStyle_defaultOmitsFontSize() {
        assertEquals("-fx-font-family: \"Meiryo UI\";", UiFonts.rootStyle("Meiryo UI", "default"));
        assertEquals("-fx-font-family: \"Meiryo UI\";", UiFonts.rootStyle("Meiryo UI", null));
    }

    @Test
    void rootStyle_largeIncludesEm() {
        assertEquals("-fx-font-family: \"Yu Gothic UI\"; -fx-font-size: 1.2em;",
                UiFonts.rootStyle("Yu Gothic UI", "large1"));
        assertEquals("-fx-font-family: \"Yu Gothic UI\"; -fx-font-size: 1.3em;",
                UiFonts.rootStyle("Yu Gothic UI", "large2"));
    }
}
