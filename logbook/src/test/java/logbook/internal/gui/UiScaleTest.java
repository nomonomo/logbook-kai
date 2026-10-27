package logbook.internal.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link UiScale} のテスト。
 */
class UiScaleTest {

    @Test
    void sizeFactor_defaultAndUnknown() {
        assertEquals(1.0, UiScale.sizeFactor(null));
        assertEquals(1.0, UiScale.sizeFactor(""));
        assertEquals(1.0, UiScale.sizeFactor("default"));
        assertEquals(1.0, UiScale.sizeFactor("other"));
    }

    @Test
    void sizeFactor_large() {
        assertEquals(1.2, UiScale.sizeFactor("large1"));
        assertEquals(1.3, UiScale.sizeFactor("large2"));
    }

    @Test
    void bannerWidth_byFontSize() {
        assertEquals(160, UiScale.bannerWidth(null));
        assertEquals(160, UiScale.bannerWidth("default"));
        assertEquals(200, UiScale.bannerWidth("large1"));
        assertEquals(240, UiScale.bannerWidth("large2"));
    }

    @Test
    void bannerHeight_byFontSize() {
        assertEquals(40, UiScale.bannerHeight(null));
        assertEquals(40, UiScale.bannerHeight("default"));
        assertEquals(50, UiScale.bannerHeight("large1"));
        assertEquals(60, UiScale.bannerHeight("large2"));
    }

    @Test
    void infoColumnWidth_byFontSize() {
        assertEquals(100.0, UiScale.infoColumnWidth("default"));
        assertEquals(120.0, UiScale.infoColumnWidth("large1"));
        assertEquals(130.0, UiScale.infoColumnWidth("large2"));
    }

    @Test
    void supplySize_byFontSize() {
        assertEquals(36.0, UiScale.supplyWidth("default"));
        assertEquals(12.0, UiScale.supplyHeight("default"));
        assertEquals(43.2, UiScale.supplyWidth("large1"), 1e-9);
        assertEquals(14.4, UiScale.supplyHeight("large1"), 1e-9);
        assertEquals(46.8, UiScale.supplyWidth("large2"), 1e-9);
        assertEquals(15.6, UiScale.supplyHeight("large2"), 1e-9);
    }

    @Test
    void scaledPx_fleetStatIcon() {
        assertEquals(24.0, UiScale.scaledPx(UiScale.FLEET_STAT_ICON_SIZE, "default"));
        assertEquals(28.8, UiScale.scaledPx(UiScale.FLEET_STAT_ICON_SIZE, "large1"), 1e-9);
        assertEquals(31.2, UiScale.scaledPx(UiScale.FLEET_STAT_ICON_SIZE, "large2"), 1e-9);
    }

    @Test
    void shipFilterWidth_byFontSize() {
        assertEquals(370.0, UiScale.scaledPx(UiScale.SHIP_TYPE_WRAP_WIDTH, "default"));
        assertEquals(444.0, UiScale.scaledPx(UiScale.SHIP_TYPE_WRAP_WIDTH, "large1"), 1e-9);
        assertEquals(481.0, UiScale.scaledPx(UiScale.SHIP_TYPE_WRAP_WIDTH, "large2"), 1e-9);
        assertEquals(200.0, UiScale.scaledPx(UiScale.SHIP_TEXT_FIELD_WIDTH, "default"));
        assertEquals(240.0, UiScale.scaledPx(UiScale.SHIP_TEXT_FIELD_WIDTH, "large1"), 1e-9);
        assertEquals(260.0, UiScale.scaledPx(UiScale.SHIP_TEXT_FIELD_WIDTH, "large2"), 1e-9);
    }
}
