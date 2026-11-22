package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

/** R4A item 3: the iOS data-type label table and the active-network gate (R4B draws the glyph). */
class DataLabelReaderTest {

    private fun label(
        type: Int,
        nrConnected: Boolean = false,
        mmWave: Boolean = false,
        dataEnabled: Boolean = true
    ): String = SignalMapping.dataTypeLabel(type, nrConnected, mmWave, dataEnabled)

    @Test
    fun `NR is 5G and mmWave NR is 5G plus`() {
        assertEquals("5G", label(20))
        assertEquals("5G", label(type = 13, nrConnected = true))
        assertEquals("5G+", label(type = 13, nrConnected = true, mmWave = true))
        // mmWave alone is not a 5G+ claim.
        assertEquals("LTE", label(type = 13, mmWave = true))
    }

    @Test
    fun `LTE and the 4G family are named`() {
        assertEquals("LTE", label(13))
        assertEquals("4G", label(19))
        assertEquals("4G", label(15))
    }

    @Test
    fun `3G E and G families are named`() {
        // UMTS, HSDPA, HSUPA, HSPA, EVDO, EHRPD, TD-SCDMA.
        for (type in listOf(3, 8, 9, 10, 5, 6, 12, 14, 17)) {
            assertEquals("type=$type", "3G", label(type))
        }
        assertEquals("E", label(2))
        for (type in listOf(1, 16, 4, 7, 11)) {
            assertEquals("type=$type", "G", label(type))
        }
    }

    @Test
    fun `unknown type and data off are empty`() {
        assertEquals("", label(0))
        assertEquals("", label(99))
        assertEquals("", label(type = 20, dataEnabled = false))
        assertEquals("", label(type = 13, nrConnected = true, dataEnabled = false))
    }

    @Test
    fun `the data label is only surfaced when the active network is cellular`() {
        // mobileActive is the reader's transport gate; the mapping must not invent a label without it.
        assertEquals(IosWifiSlot.None, IosMapping.wifiSlotState(false, false, "5G"))
        assertEquals(IosWifiSlot.DataType("5G"), IosMapping.wifiSlotState(false, true, "5G"))
        assertEquals(IosWifiSlot.Wifi, IosMapping.wifiSlotState(true, true, "5G"))
    }
}
