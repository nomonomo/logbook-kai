package logbook.internal.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link ConfigWindowSizer} のテスト。
 */
class ConfigWindowSizerTest {

    @Test
    void growWithin_prefBeatsOldSmallSave() {
        assertEquals(700, ConfigWindowSizer.growWithin(530, 700, 640, 1920));
    }

    @Test
    void growWithin_keepsLargerSavedSize() {
        assertEquals(1000, ConfigWindowSizer.growWithin(1000, 700, 640, 1920));
    }

    @Test
    void growWithin_capsToScreen() {
        assertEquals(800, ConfigWindowSizer.growWithin(1000, 700, 640, 800));
    }

    @Test
    void growWithin_nanCurrentUsesDesired() {
        assertEquals(700, ConfigWindowSizer.growWithin(Double.NaN, 700, 640, 1920));
    }

    @Test
    void clampPosition_shiftsWhenOverflowing() {
        assertEquals(280, ConfigWindowSizer.clampPosition(500, 800, 0, 1080));
    }

    @Test
    void clampPosition_keepsInside() {
        assertEquals(100, ConfigWindowSizer.clampPosition(100, 800, 0, 1080));
    }

    @Test
    void clampPosition_fullScreenSnapsToMin() {
        assertEquals(0, ConfigWindowSizer.clampPosition(10, 1200, 0, 1080));
    }
}
