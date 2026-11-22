package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The owner's Wi-Fi table, pinned once: RSSI -> calc5 -> stock bars -> iOS arcs.
 *
 * The stock scale is four bars and Duo draws three. The measured five-step column is fed as the
 * platform step ([WifiReader.resolve] takes it as a parameter, production uses
 * `WifiManager.calculateSignalLevel`): -75 dBm is calc5 2 (stock 3, two arcs), while the strong
 * -60/-62 dBm links are calc5 3 (stock 4, three arcs) and calc5 4 stays three arcs.
 */
class WifiMappingTest {

    @Test
    fun `calc fifth step 0 through 4 shifts and compresses to zero one two three three`() {
        assertEquals(0, SignalMapping.stockWifiBars(0))
        assertEquals(2, SignalMapping.stockWifiBars(1))
        assertEquals(3, SignalMapping.stockWifiBars(2))
        assertEquals(4, SignalMapping.stockWifiBars(3))
        assertEquals(4, SignalMapping.stockWifiBars(4))

        assertEquals(0, SignalMapping.wifiDotsForCalc5(0))
        assertEquals(1, SignalMapping.wifiDotsForCalc5(1))
        assertEquals(2, SignalMapping.wifiDotsForCalc5(2))
        assertEquals(3, SignalMapping.wifiDotsForCalc5(3))
        assertEquals(3, SignalMapping.wifiDotsForCalc5(4))
    }

    @Test
    fun `stock bars one and two are arc one, three is arc two, four is arc three`() {
        assertEquals(0, SignalMapping.wifiDotsForStockBars(0))
        assertEquals(1, SignalMapping.wifiDotsForStockBars(1))
        assertEquals(1, SignalMapping.wifiDotsForStockBars(2))
        assertEquals(2, SignalMapping.wifiDotsForStockBars(3))
        assertEquals(3, SignalMapping.wifiDotsForStockBars(4))
        // Existing callers keep the old name and the same pixels.
        assertEquals(3, DuoMapping.wifiBars(4))
        assertEquals(1, DuoMapping.wifiBars(2))
    }

    @Test
    fun `minus seventy five is two arcs while minus sixty and minus sixty two are three`() {
        // Measured platform values for the 5-step scale: rssi -> calc5.
        val measured = mapOf(-75 to 2, -60 to 3, -62 to 3)

        val medium = WifiReader.resolve(
            rssi = -75, on = true, active = true, validated = true, source = "active",
            currentLevel = 1, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { measured.getValue(it) }
        )
        assertEquals(-75, medium.rssi)
        assertEquals(2, medium.calc5)
        assertEquals(3, medium.stockBars)
        assertEquals(2, medium.dots)
        assertEquals("active", medium.source)

        val strong = WifiReader.resolve(
            rssi = -60, on = true, active = true, validated = true, source = "active",
            currentLevel = 0, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { measured.getValue(it) }
        )
        assertEquals(3, strong.calc5)
        assertEquals(4, strong.stockBars)
        assertEquals(3, strong.dots)

        val strong62 = WifiReader.resolve(
            rssi = -62, on = true, active = true, validated = true, source = "active",
            currentLevel = 0, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { measured.getValue(it) }
        )
        assertEquals(3, strong62.calc5)
        assertEquals(4, strong62.stockBars)
        assertEquals(3, strong62.dots)
    }

    @Test
    fun `the platform conversion sees the measured rssi`() {
        var seen = Int.MIN_VALUE
        val sample = WifiReader.resolve(
            rssi = -75, on = true, active = true, validated = true, source = "active",
            currentLevel = 1, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { seen = it; 2 }
        )
        assertEquals(-75, seen)
        assertEquals(2, sample.dots)
    }

    @Test
    fun `a successful read ignores the previous level`() {
        val sample = WifiReader.resolve(
            rssi = -75, on = true, active = true, validated = true, source = "active",
            currentLevel = 1, currentOn = false, currentActive = false, currentValidated = false,
            calc5Of = { 2 }
        )
        // The fresh RSSI wins over both the level and the liveness the caller carried in.
        assertEquals(2, sample.dots)
        assertEquals(true, sample.on)
        assertEquals(true, sample.active)
        assertEquals(true, sample.validated)
    }

    @Test
    fun `a failed read keeps the previous level and marks the sample stale`() {
        val sample = WifiReader.resolve(
            rssi = null, on = true, active = true, validated = true, source = "active",
            currentLevel = 2, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { 4 }
        )
        assertEquals(2, sample.dots)
        assertEquals(-127, sample.rssi)
        assertEquals(0, sample.calc5)
        assertEquals(0, sample.stockBars)
        assertEquals("stale", sample.source)
        // The last known liveness is kept too, so the glyph is not blanked by one failed query.
        assertEquals(true, sample.on)
        assertEquals(true, sample.active)
        assertEquals(true, sample.validated)
    }

    @Test
    fun `radio off clears the level and invalid rssi never invents one`() {
        val off = WifiReader.resolve(
            rssi = -60, on = false, active = false, validated = false, source = "active",
            currentLevel = 3, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { 3 }
        )
        assertEquals(0, off.dots)
        assertEquals("off", off.source)

        val invalid = WifiReader.resolve(
            rssi = -127, on = true, active = true, validated = false, source = "connectionInfo",
            currentLevel = 3, currentOn = true, currentActive = true, currentValidated = true,
            calc5Of = { 3 }
        )
        assertEquals(0, invalid.dots)
        assertEquals(0, invalid.stockBars)
        assertEquals("connectionInfo", invalid.source)
    }
}
