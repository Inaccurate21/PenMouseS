package pl.jojczak.penmouses.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamsungPenBatteryCaptureTest {
    @Test fun capturesOnVerifiedSamsungPenScreen() {
        assertEquals(100, SamsungPenBatteryCapture.extractFromTexts(
            listOf("Rysik S Pen", "100%", "Wyjęto", "Akcje gestem")
        ))
    }

    @Test fun ignoresUnrelatedPercentAndGenericSettingsPage() {
        assertNull(SamsungPenBatteryCapture.extractFromTexts(listOf("Bateria", "83%", "Wyjęto")))
        assertNull(SamsungPenBatteryCapture.extractFromTexts(listOf("Rysik S Pen", "50%")))
    }

    @Test fun neverGuessesWhenScreenContainsMultiplePercentages() {
        assertNull(SamsungPenBatteryCapture.extractFromTexts(
            listOf("Rysik S Pen", "Wyjęto", "100%", "83%")
        ))
    }
}
