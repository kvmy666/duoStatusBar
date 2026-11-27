package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2: the pure per-piece coverage computation and the exact evidence line formats. These decide the
 * Samsung iOS-v2 question from a report: which family piece resolved, which stock names were tried,
 * and whether the bar was ever observed through `ResourcesImpl.loadDrawable`.
 */
class IosV2CoverageTest {

    private fun spec(
        name: String,
        kind: IosResourceKind,
        value: Int = 1,
        label: String = ""
    ): IosResourceSpec = IosResourceSpec(name = name, kind = kind, value = value, label = label)

    private fun sample(): List<IosResourceSpec> = listOf(
        spec("wifi_0", IosResourceKind.WIFI),
        spec("wifi_1", IosResourceKind.WIFI),
        spec("cell_0", IosResourceKind.CELL),
        spec("label_5g", IosResourceKind.LABEL, label = "5G"),
        spec("hide_badge", IosResourceKind.HIDDEN)
    )

    @Test
    fun `coverage counts every spec against the resolved set per piece`() {
        val specs = sample()
        val resolved = setOf(specs[0], specs[2])
        val coverage = computePieceCoverage(specs, resolved)

        assertEquals(listOf("wifi", "cell", "label", "hidden"), coverage.map { it.piece })
        assertEquals(IosPieceCoverage("wifi", 1, 2, listOf("wifi_1")), coverage[0])
        assertEquals(IosPieceCoverage("cell", 1, 1, emptyList()), coverage[1])
        assertEquals(IosPieceCoverage("label", 0, 1, listOf("label_5g")), coverage[2])
        assertEquals(IosPieceCoverage("hidden", 0, 1, listOf("hide_badge")), coverage[3])
    }

    @Test
    fun `a piece with no specs is omitted, not reported as a zero miss`() {
        val specs = listOf(spec("wifi_0", IosResourceKind.WIFI))
        val coverage = computePieceCoverage(specs, emptySet())
        assertEquals(listOf("wifi"), coverage.map { it.piece })
    }

    @Test
    fun `tried names are bounded to eight and keep spec order`() {
        val specs = (0 until 10).map { spec("w$it", IosResourceKind.WIFI) }
        val coverage = computePieceCoverage(specs, emptySet()).single()
        assertEquals(0, coverage.resolved)
        assertEquals(10, coverage.total)
        assertEquals((0 until 8).map { "w$it" }, coverage.tried)
    }

    @Test
    fun `dormant Motorola raw-id data formats as hex`() {
        // No live route builds this spec; the Motorola table is DORMANT (R13) measured data.
        val raw = IosResourceSpec(
            name = "",
            kind = IosResourceKind.WIFI,
            value = 1,
            packageName = "android",
            rawId = 0x01080965
        )
        val coverage = computePieceCoverage(listOf(raw), emptySet()).single()
        assertEquals(listOf("raw_0x01080965"), coverage.tried)
    }

    @Test
    fun `coverage line names the family piece counts and tried names`() {
        val line = coverageLine(
            "samsung",
            IosPieceCoverage("wifi", 1, 2, listOf("wifi_1"))
        )
        assertEquals(
            "ios v2 coverage family=samsung piece=wifi resolved=1/2 tried=wifi_1",
            line
        )
    }

    @Test
    fun `miss line names the family piece and tried names`() {
        val line = missLine("samsung", "label", listOf("label_5g", "label_4g"))
        assertEquals(
            "ios v2 miss family=samsung piece=label tried=label_5g,label_4g",
            line
        )
    }

    @Test
    fun `miss line says none when no names were tried`() {
        assertEquals(
            "ios v2 miss family=samsung piece=hidden tried=none",
            missLine("samsung", "hidden", emptyList())
        )
    }

    @Test
    fun `runtime miss line carries one count per piece`() {
        val line = runtimeMissLine("samsung", longArrayOf(1, 0, 2, 3))
        assertEquals(
            "ios v2 runtimeMisses: family=samsung wifi=1 cell=0 label=2 hidden=3",
            line
        )
    }

    @Test
    fun `delivery line states plainly when the bar was not seen through ResourcesImpl`() {
        val zero = deliveryLine(0, "ImageView:ColorDrawable")
        assertEquals(
            "ios v2 delivery: resourcesImpl calls=0; viewIcons=ImageView:ColorDrawable " +
                "(bar not observed through ResourcesImpl.loadDrawable; " +
                "setImageResource/shadow/Compose path is not observed)",
            zero
        )

        val seen = deliveryLine(42, "StatusBarIconView:IosGlyphDrawable")
        assertEquals(
            "ios v2 delivery: resourcesImpl calls=42; " +
                "viewIcons=StatusBarIconView:IosGlyphDrawable",
            seen
        )
    }

    @Test
    fun `delivery line names an empty inventory instead of a blank field`() {
        val line = deliveryLine(0, "")
        assertTrue("line: $line", line.contains("viewIcons=none "))
    }

    @Test
    fun `battery stacked and extras miss lines use the fixed measured tried names`() {
        // Samsung's batteryProbePlan is AOSP-only: the miss line must not name the OxygenOS classes.
        assertEquals(
            "ios v2 miss family=samsung piece=battery tried=BatteryMeterView",
            batteryMissLine("samsung")
        )
        // OxygenOS keeps the exact measured view/binder/view list (byte-identical to 2.1.x).
        assertEquals(
            "ios v2 miss family=oxygenos piece=battery " +
                "tried=StatBatteryMeterView,BatteryViewBinder,BatteryMeterView",
            batteryMissLine("oxygenos")
        )
        assertEquals(
            "ios v2 miss family=oxygenos piece=stacked tried=OplusStatusBarMobileViewBinder",
            stackedMissLine("oxygenos", "OplusStatusBarMobileViewBinder")
        )
        assertEquals(
            "ios v2 miss family=oxygenos piece=extras tried=OplusWifiSignalExImpl",
            extrasMissLine("oxygenos", "OplusWifiSignalExImpl")
        )
    }

    // ------------------------------------------------------------------ R18 miss gating

    @Test
    fun `a battery miss is reported only when the piece has no owner`() {
        // An installed owner's boot retry (firstFailure was a retry token) is not a miss.
        assertFalse(shouldReportBatteryMiss("oxygenos", installed = true))
        assertTrue(shouldReportBatteryMiss("oxygenos", installed = false))
        assertTrue(shouldReportBatteryMiss("samsung", installed = false))
        // A family the battery owner does not cover is not a battery miss.
        assertFalse(shouldReportBatteryMiss("aosp", installed = false))
        assertFalse(shouldReportBatteryMiss("", installed = false))
    }

    @Test
    fun `a stacked miss is reported only when the required Oplus binder is missing`() {
        // The owner dump: only the optional TMO/Sprint/BigType candidates are absent while the
        // measured OplusStatusBarMobileViewBinder owns the cluster. That is not a miss.
        val optionalOnly = "TMOStatusBarMobileViewBinder,SprintStatusBarMobileViewBinder," +
            "BigTypeStatusBarMobileViewBinder"
        assertFalse(shouldReportStackedMiss("oxygenos", optionalOnly))
        // The required binder is the owner: its absence is the real miss.
        assertTrue(shouldReportStackedMiss("oxygenos", "OplusStatusBarMobileViewBinder"))
        // A mixed absence string still carries the owner name.
        assertTrue(
            shouldReportStackedMiss(
                "oxygenos",
                "OplusStatusBarMobileViewBinder,TMOStatusBarMobileViewBinder"
            )
        )
        // Stacked is not an OxygenOS/ColorOS piece elsewhere.
        assertFalse(shouldReportStackedMiss("samsung", "OplusStatusBarMobileViewBinder"))
        assertFalse(shouldReportStackedMiss("", "OplusStatusBarMobileViewBinder"))
    }
}
