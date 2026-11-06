package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The new cell/Wi-Fi evidence lines, tested as text.
 *
 * The whole point of these lines is that `raw`, `count`, `maxIndex` and `dots` are separate facts: the
 * old `cell raw` line printed mapped dots under a raw label. These tests pin the field names, their
 * order and the cases that would pass if the fields were aliases of each other.
 */
class CellDiagnosticTest {

    @Test
    fun `cell sample keeps raw and dots distinct`() {
        val sample = CellSample(
            slot = 0,
            subscriptionId = 2,
            raw = 4,
            count = 5,
            maxIndex = 4,
            rat = "Lte",
            dbm = -97,
            metric = "rsrp",
            dots = 4,
            src = "per-sub",
            airplane = false
        )
        assertEquals(
            "cell sample sub=2 slot=0 raw=4 count=5 maxIndex=4 rat=Lte dbm=-97 metric=rsrp " +
                "dots=4 src=per-sub airplane=false",
            SignalMapping.cellSampleLine(sample)
        )
    }

    @Test
    fun `cell sample proves count and maxIndex are not aliases`() {
        // The count-overreport case: raw 4 on a six-level count is maxIndex 5, so Duo draws three.
        val sample = CellSample(
            slot = 1,
            subscriptionId = 1,
            raw = 4,
            count = 6,
            maxIndex = 5,
            rat = "Nr",
            dbm = -102,
            metric = "rsrp",
            dots = 3,
            src = "per-sub",
            airplane = false
        )
        assertEquals(
            "cell sample sub=1 slot=1 raw=4 count=6 maxIndex=5 rat=Nr dbm=-102 metric=rsrp " +
                "dots=3 src=per-sub airplane=false",
            SignalMapping.cellSampleLine(sample)
        )
    }

    @Test
    fun `cell sample marks a failed per-subscription read unavailable`() {
        val sample = CellSample(
            slot = 0,
            subscriptionId = 2,
            raw = -1,
            count = 5,
            maxIndex = 4,
            rat = "-",
            dbm = -999,
            metric = "-",
            dots = 4,
            src = "unavailable",
            airplane = false
        )
        val line = SignalMapping.cellSampleLine(sample)
        assertTrue(line.contains("raw=-1"))
        assertTrue(line.contains("src=unavailable"))
        assertTrue(line.contains("dots=4"))
    }

    @Test
    fun `cell pick names the fallback and the default subscription`() {
        val pick = CellSelection(
            dots = 4,
            slot = -1,
            subscriptionId = -1,
            src = "fallback",
            fallback = true
        )
        assertEquals(
            "cell pick sim=sim2 slot=-1 sub=-1 dots=4 src=fallback fallback=true defaultSub=2",
            SignalMapping.cellPickLine("sim2", pick, defaultSub = 2)
        )
    }

    @Test
    fun `cell pick names the selected slot and subscription`() {
        val pick = CellSelection(
            dots = 1,
            slot = 1,
            subscriptionId = 1,
            src = "per-sub",
            fallback = false
        )
        assertEquals(
            "cell pick sim=sim2 slot=1 sub=1 dots=1 src=per-sub fallback=false defaultSub=2",
            SignalMapping.cellPickLine("sim2", pick, defaultSub = 2)
        )
    }

    @Test
    fun `wifi sample carries rssi, both bar scales and the source`() {
        val sample = WifiSample(
            rssi = -52,
            calc5 = 4,
            stockBars = 4,
            dots = 3,
            on = true,
            active = true,
            validated = true,
            source = "active"
        )
        assertEquals(
            "wifi sample rssi=-52 calc5=4 stockBars=4 dots=3 on=true active=true " +
                "validated=true src=active",
            WifiReader.sampleLine(sample)
        )
    }

    @Test
    fun `wifi sample keeps an invalid rssi visible instead of inventing a level`() {
        val sample = WifiSample(
            rssi = -127,
            calc5 = 0,
            stockBars = 0,
            dots = 0,
            on = true,
            active = false,
            validated = false,
            source = "off"
        )
        assertEquals(
            "wifi sample rssi=-127 calc5=0 stockBars=0 dots=0 on=true active=false " +
                "validated=false src=off",
            WifiReader.sampleLine(sample)
        )
    }
}
