package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cellular mapping boundaries the owner field report named, plus the per-sample provenance seam.
 *
 * The 3/4 and 4/4 stock icons must never read as one iOS bar: raw 3 on a five-level source is three
 * dots and raw 2 is two, while only a genuinely unreadable per-SIM sample is allowed to fall back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CellReaderMappingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun sample(
        raw: Int,
        count: Int,
        dots: Int,
        src: String = SignalMapping.SRC_PER_SUB,
        slot: Int = 0,
        subId: Int = 11
    ) = CellSample(
        slot = slot,
        subscriptionId = subId,
        raw = raw,
        count = count,
        maxIndex = SignalMapping.cellMaxIndex(raw, count),
        rat = "Lte",
        dbm = -95,
        metric = "rsrp",
        dots = dots,
        src = src,
        airplane = false
    )

    @Test
    fun `five level raw boundaries map to one one two three four`() {
        assertEquals(1, SignalMapping.cellDots(0, 5))
        assertEquals(1, SignalMapping.cellDots(1, 5))
        assertEquals(2, SignalMapping.cellDots(2, 5))
        assertEquals(3, SignalMapping.cellDots(3, 5))
        assertEquals(4, SignalMapping.cellDots(4, 5))
    }

    @Test
    fun `four level raw boundaries map the full bar to four dots`() {
        assertEquals(1, SignalMapping.cellDots(0, 4))
        assertEquals(1, SignalMapping.cellDots(1, 4))
        assertEquals(3, SignalMapping.cellDots(2, 4))
        assertEquals(4, SignalMapping.cellDots(3, 4))
        // A caller that hands a bar count (0..count) instead of an index still lands full.
        assertEquals(4, SignalMapping.cellDots(4, 4))
    }

    @Test
    fun `three of four and four of four never collapse to one dot`() {
        // The owner's observed failure: stock 3/4 and 4/4 both drew one iOS bar. Raw 2 on a 5-level
        // source is the 3/4 case and raw 3 is the 4/4 case; both must stay above one.
        assertNotEquals(1, SignalMapping.cellDots(2, 5))
        assertNotEquals(1, SignalMapping.cellDots(3, 5))
        assertEquals(2, SignalMapping.cellDots(2, 5))
        assertEquals(3, SignalMapping.cellDots(3, 5))
    }

    @Test
    fun `a sample's dots must follow its own raw and count`() {
        val three = sample(raw = 3, count = 5, dots = 3)
        assertTrue(SignalMapping.cellSampleDotsProven(three))
        assertEquals(3, three.dots)

        val two = sample(raw = 2, count = 5, dots = 2)
        assertTrue(SignalMapping.cellSampleDotsProven(two))
        assertEquals(2, two.dots)

        // A carried-over/stale one-dot level is not this SIM's reading.
        val staleOne = sample(raw = 3, count = 5, dots = 1)
        assertFalse(SignalMapping.cellSampleDotsProven(staleOne))
        assertNotEquals(1, SignalMapping.cellDots(staleOne.raw, staleOne.count))
    }

    @Test
    fun `an unavailable sample is visible and exempt from the provenance check`() {
        val unavailable = sample(
            raw = -1,
            count = 5,
            dots = 1,
            src = SignalMapping.SRC_UNAVAILABLE
        )
        assertFalse(unavailable.available)
        assertTrue(SignalMapping.cellSampleDotsProven(unavailable))
        assertEquals(-1, unavailable.raw)
        assertEquals(SignalMapping.SRC_UNAVAILABLE, unavailable.src)
    }

    @Test
    fun `a failed per-sub read is unavailable, never per-slot`() {
        // Robolectric's shadow has no per-subscription manager mapping, so createForSubscriptionId
        // returns null: exactly the failed-read path the module must report honestly.
        val failed = CellReader.sampleFor(
            context = context,
            slot = 0,
            subId = 7,
            count = 5,
            fallbackDots = 1
        )
        assertEquals(SignalMapping.SRC_UNAVAILABLE, failed.src)
        assertFalse(failed.available)
        assertEquals(-1, failed.raw)
        assertEquals(1, failed.dots)
        assertNotEquals("per-slot", failed.src)
    }

    @Test
    fun `no production code path can label a sample per-slot`() {
        // The per-slot inference was unsound (getCellSignalStrengths is modem-ordered, not slot-ordered),
        // so neither the source constant nor a string literal may remain in production code.
        assertFalse(
            "SRC_PER_SLOT must not exist",
            SignalMapping::class.java.declaredFields.any { it.name == "SRC_PER_SLOT" }
        )
        assertFalse(classConstantText(CellReader::class.java).contains("per-slot"))
        assertFalse(classConstantText(SignalMapping::class.java).contains("per-slot"))
    }

    private fun classConstantText(cls: Class<*>): String {
        val bytes = cls.getResourceAsStream(cls.simpleName + ".class")!!.use { it.readBytes() }
        return String(bytes, Charsets.ISO_8859_1)
    }

    @Test
    fun `the one-four regression boundary stays two three three four`() {
        assertEquals(2, SignalMapping.cellDots(2, 5))
        assertEquals(3, SignalMapping.cellDots(3, 5))
        assertEquals(3, SignalMapping.cellDots(2, 4))
        assertEquals(4, SignalMapping.cellDots(3, 4))
    }
}
