package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R3 A4: one short-lived Wi-Fi/cellular snapshot shared by every iOS surface. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosReadingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var now = 1_000L
    private var wifiReads = 0
    private var cellReads = 0
    private var lastWifi: WifiSample? = null
    private var lastCell: CellReading? = null

    @Before
    fun setUp() {
        now = 1_000L
        wifiReads = 0
        cellReads = 0
        IosReadings.resetForTest(
            wifi = { _, _, _, _, _ ->
                wifiReads++
                WifiSample(-55, 4, 4, 2, true, true, true, "active")
                    .also { lastWifi = it }
            },
            cell = {
                cellReads++
                oneSim(3).also { lastCell = it }
            },
            clock = { now }
        )
    }

    @Test
    fun `a second call inside the TTL is the same snapshot`() {
        val firstWifi = IosReadings.wifi(context, 2, true, true, true)
        val secondWifi = IosReadings.wifi(context, 2, true, true, true)
        val firstCell = IosReadings.cell(context)
        val secondCell = IosReadings.cell(context)

        assertEquals(1, wifiReads)
        assertEquals(1, cellReads)
        assertSame(firstWifi, secondWifi)
        assertSame(firstCell, secondCell)
        assertSame(lastWifi, secondWifi)
        assertSame(lastCell, secondCell)
    }

    @Test
    fun `an expired TTL reads a fresh snapshot`() {
        val first = IosReadings.wifi(context, 2, true, true, true)
        now += IosReadings.TTL_MS
        val second = IosReadings.wifi(context, 2, true, true, true)

        assertEquals(2, wifiReads)
        assertNotSame(first, second)
    }

    @Test
    fun `invalidate drops both snapshots and bumps the generation`() {
        IosReadings.wifi(context, 2, true, true, true)
        IosReadings.cell(context)
        val generation = IosReadings.generationForTest()

        IosReadings.invalidate()
        assertEquals(generation + 1, IosReadings.generationForTest())
        IosReadings.wifi(context, 2, true, true, true)
        IosReadings.cell(context)

        assertEquals(2, wifiReads)
        assertEquals(2, cellReads)
    }

    @Test
    fun `two surfaces asking in the same burst share one snapshot`() {
        val surfaceA = IosReadings.wifi(context, 2, true, true, true)
        val cellA = IosReadings.cell(context)
        val surfaceB = IosReadings.wifi(context, 1, true, true, true)
        val cellB = IosReadings.cell(context)

        assertSame("both surfaces drew the same Wi-Fi sample", surfaceA, surfaceB)
        assertSame("both surfaces drew the same cellular reading", cellA, cellB)
        assertEquals(1, wifiReads)
        assertEquals(1, cellReads)
    }

    private fun oneSim(dots: Int): CellReading = CellReading(
        samples = listOf(
            CellSample(
                slot = 0,
                subscriptionId = 1,
                raw = dots,
                count = 4,
                maxIndex = 4,
                rat = "LTE",
                dbm = -90,
                metric = "level",
                dots = dots,
                src = SignalMapping.SRC_PER_SUB,
                airplane = false
            )
        ),
        count = 4,
        defaultRaw = dots,
        defaultMaxIndex = 4,
        defaultDots = dots,
        defaultSub = 1,
        defaultSrc = SignalMapping.SRC_PER_SUB,
        airplane = false
    )
}
