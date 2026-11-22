package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.L
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * R3 A5.3: one compact, parseable self-diagnosis line per iOS surface per stock icon.
 *
 * The fixtures are the measured OPLUS anatomy used by [IosReplaceHostTest], kept small here so the
 * line content is the only thing under test: a replaced tree, a missing leaf, an unavailable cell
 * sample (the plan veto), the exact field format, and the dump append position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDiagnosticsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
        IosReadings.resetForTest()
        IosDiagnostics.clearForTest()
        L.clearRecentForTest()
    }

    @After
    fun tearDown() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
        IosDrawHookInstaller.releaseWhenEmpty()
        IosReadings.resetForTest()
        IosDiagnostics.clearForTest()
        L.clearRecentForTest()
    }

    private val nextNamedId = AtomicInteger(0x7f00_0000)

    private inner class NamedView(entry: String) : View(context) {
        private val fake: Resources

        init {
            id = nextNamedId.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake
    }

    private inner class NamedImage(entry: String) : ImageView(context) {
        private val fake: Resources

        init {
            id = nextNamedId.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake
    }

    private inner class NamedGroup(entry: String, customId: Int = 0) : FrameLayout(context) {
        private val fake: Resources

        init {
            id = if (customId != 0) customId else nextNamedId.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake
    }

    @Suppress("DEPRECATION")
    private class FakeResources(
        parent: Resources,
        private val names: Map<Int, String>
    ) : Resources(parent.assets, parent.displayMetrics, parent.configuration) {
        override fun getResourceEntryName(id: Int): String =
            names[id] ?: super.getResourceEntryName(id)
    }

    private class StatBatteryMeterView(context: Context) : FrameLayout(context)

    private class OplusModernStatusBarWifiView(context: Context) : FrameLayout(context)

    private class OplusModernStatusBarMobileView(context: Context, private val sub: Int) :
        FrameLayout(context) {
        fun getSubId(): Int = sub
    }

    /** The panel's opaque Compose icon: no View-side leaf, so the overlay host is the only renderer. */
    inner class SingleBindableStatusBarComposeIconView(
        entry: String,
        private val sub: Int
    ) : FrameLayout(context) {
        private val fake: Resources

        init {
            id = nextNamedId.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        fun getSubId(): Int = sub

        override fun getResources(): Resources = fake
    }

    /** The id-selected Compose battery (`battery_composable_view`), drawn by the overlay host. */
    private inner class ComposeView(entry: String) : FrameLayout(context) {
        private val fake: Resources

        init {
            id = nextNamedId.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake
    }

    private class ReplacedTree(
        val strip: LinearLayout,
        val batteryLeaf: NamedImage,
        val wifiLeaf: NamedImage,
        val mobileLeaf: NamedImage,
        val mobileDrawable: Drawable
    )

    private fun replacedTree(subId: Int = 5): ReplacedTree {
        val strip = LinearLayout(context)

        val battery = StatBatteryMeterView(context)
        val batteryLeaf = NamedImage("battery_icon_view").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        battery.addView(batteryLeaf, FrameLayout.LayoutParams(79, 48))

        val wifi = OplusModernStatusBarWifiView(context)
        val wifiGroup = NamedGroup("wifi_group")
        val wifiLeaf = NamedImage("wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        wifiGroup.addView(wifiLeaf, FrameLayout.LayoutParams(70, 51))
        wifi.addView(wifiGroup, FrameLayout.LayoutParams(73, 63))

        val mobile = OplusModernStatusBarMobileView(context, subId)
        val mobileGroup = NamedGroup("mobile_group")
        val mobileLeaf = NamedImage("mobile_signal")
        val mobileDrawable = ColorDrawable(Color.CYAN)
        mobileLeaf.setImageDrawable(mobileDrawable)
        mobileGroup.addView(mobileLeaf, FrameLayout.LayoutParams(60, 51))
        mobile.addView(mobileGroup, FrameLayout.LayoutParams(60, 63))

        strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        strip.addView(wifi, LinearLayout.LayoutParams(0, 0))
        strip.addView(mobile, LinearLayout.LayoutParams(0, 0))
        strip.layout(0, 0, 300, 63)
        battery.layout(0, 0, 90, 48)
        batteryLeaf.layout(0, 0, 79, 48)
        wifi.layout(0, 0, 0, 0)
        wifiGroup.layout(0, 0, 73, 63)
        wifiLeaf.layout(3, 6, 73, 57)
        mobile.layout(0, 0, 0, 0)
        mobileGroup.layout(0, 0, 60, 63)
        mobileLeaf.layout(2, 6, 62, 57)
        return ReplacedTree(strip, batteryLeaf, wifiLeaf, mobileLeaf, mobileDrawable)
    }

    private fun host(
        cellSource: (Context) -> CellReading = { oneSimReading(5) },
        batterySource: (Context) -> IosBatteryReading? = { IosBatteryReading(50, false, 0) }
    ): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        sizeOwner = IosSizeOwner(),
        isMainBar = true,
        surface = "main bar",
        batterySource = batterySource,
        cellSource = cellSource,
        wifiSource = { _, _, _, _, _ ->
            WifiSample(
                rssi = -55,
                calc5 = 4,
                stockBars = 4,
                dots = 2,
                on = true,
                active = true,
                validated = true,
                source = "active"
            )
        }
    )

    private fun sample(
        slot: Int,
        subId: Int,
        dots: Int,
        src: String = SignalMapping.SRC_PER_SUB
    ): CellSample = CellSample(
        slot = slot,
        subscriptionId = subId,
        raw = dots,
        count = 4,
        maxIndex = 4,
        rat = "LTE",
        dbm = -90,
        metric = "level",
        dots = dots,
        src = src,
        airplane = false
    )

    private fun oneSimReading(subId: Int, dots: Int = 3, src: String = SignalMapping.SRC_PER_SUB) =
        CellReading(
            samples = listOf(sample(0, subId, dots, src)),
            count = 4,
            defaultRaw = dots,
            defaultMaxIndex = 4,
            defaultDots = dots,
            defaultSub = subId,
            defaultSrc = src,
            airplane = false
        )

    private fun twoSimReading(first: Int, second: Int): CellReading = CellReading(
        samples = listOf(sample(0, first, 3), sample(1, second, 2)),
        count = 4,
        defaultRaw = 3,
        defaultMaxIndex = 4,
        defaultDots = 3,
        defaultSub = first,
        defaultSrc = SignalMapping.SRC_PER_SUB,
        airplane = false
    )

    /** Adds the second measured mobile view an OEM adds on a live 1→2 SIM transition. */
    private fun addMobile(strip: LinearLayout, subId: Int): OplusModernStatusBarMobileView {
        val mobile = OplusModernStatusBarMobileView(context, subId)
        val group = NamedGroup("mobile_group")
        val leaf = NamedImage("mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        group.addView(leaf, FrameLayout.LayoutParams(60, 51))
        mobile.addView(group, FrameLayout.LayoutParams(60, 63))
        strip.addView(mobile, LinearLayout.LayoutParams(0, 0))
        mobile.layout(0, 0, 0, 0)
        group.layout(0, 0, 60, 63)
        leaf.layout(2, 6, 62, 57)
        return mobile
    }

    private fun noCellReading(): CellReading = CellReading(
        samples = emptyList(),
        count = 4,
        defaultRaw = -1,
        defaultMaxIndex = -1,
        defaultDots = 0,
        defaultSub = -1,
        defaultSrc = SignalMapping.SRC_UNAVAILABLE,
        airplane = false
    )

    private class OverlayTree(val root: FrameLayout, val strip: LinearLayout)

    /** An all-overlay strip: a Compose battery plus two opaque Compose cellular icons. */
    private fun overlayTree(): OverlayTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = ComposeView("battery_composable_view")
        val first = SingleBindableStatusBarComposeIconView("mobile_combo", 0)
        val second = SingleBindableStatusBarComposeIconView("mobile_combo", 0)
        strip.addView(battery, LinearLayout.LayoutParams(55, 27))
        strip.addView(first, LinearLayout.LayoutParams(52, 49))
        strip.addView(second, LinearLayout.LayoutParams(52, 49))
        root.addView(strip, FrameLayout.LayoutParams(300, 60))
        root.layout(0, 0, 1000, 200)
        strip.layout(0, 0, 300, 60)
        battery.layout(0, 0, 55, 27)
        first.layout(60, 0, 112, 49)
        second.layout(120, 0, 172, 49)
        return OverlayTree(root, strip)
    }

    /** A real [IosIconHost], so the overlay report path (not the in-place host) is under test. */
    private fun overlayHost(surface: String, cellSource: (Context) -> CellReading): IosIconHost =
        IosIconHost(
            context = context,
            surface = surface,
            batterySource = { IosBatteryReading(50, false, 0) },
            cellSource = cellSource
        )

    private data class Parsed(
        val surface: String,
        val kind: String,
        val family: String,
        val mechanism: String,
        val state: String,
        val slot: List<Int>,
        val tint: String,
        val level: String,
        val reason: String,
        val subId: String?
    )

    private val linePattern = Regex(
        "^ios surface=(.+) (BATTERY|WIFI|CELL) family=(\\S+) mech=(\\S+) state=(\\S+) " +
            "slot=(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+) tint=(\\S+) level=(\\S+) reason=(\\S+)" +
            "(?: subId=(\\S+))?$"
    )

    private fun parse(line: String): Parsed {
        val match = linePattern.matchEntire(line)
        requireNotNull(match) { "line does not match the diagnostic format: $line" }
        val groups = match.groupValues
        return Parsed(
            surface = groups[1],
            kind = groups[2],
            family = groups[3],
            mechanism = groups[4],
            state = groups[5],
            slot = listOf(groups[6].toInt(), groups[7].toInt(), groups[8].toInt(), groups[9].toInt()),
            tint = groups[10],
            level = groups[11],
            reason = groups[12],
            subId = groups[13].ifEmpty { null }
        )
    }

    @Test
    fun `a replaced tree reports one line per icon with real bounds`() {
        val tree = replacedTree()
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        val lines = host.diagnosticLines()
        assertEquals(3, lines.size)
        assertEquals(listOf("BATTERY", "WIFI", "CELL"), lines.map { parse(it).kind })

        val battery = parse(lines[0])
        assertEquals("main bar", battery.surface)
        assertEquals("OPLUS_BATTERY", battery.family)
        assertEquals("IMAGE_DRAWABLE", battery.mechanism)
        assertEquals("replaced", battery.state)
        assertEquals(listOf(0, 0, 79, 48), battery.slot)
        assertEquals("battery:sticky:50", battery.level)
        assertEquals("-", battery.reason)

        val wifi = parse(lines[1])
        assertEquals("OPLUS_WIFI", wifi.family)
        // The plan asked for OUTER_DRAW; with no class loader the host applied the leaf swap, so the
        // report must say fallback rather than claim the requested mechanism.
        assertEquals("IMAGE_DRAWABLE", wifi.mechanism)
        assertEquals("fallback", wifi.state)
        assertEquals(listOf(3, 6, 73, 57), wifi.slot)
        assertEquals("wifi:active:2", wifi.level)

        val cell = parse(lines[2])
        assertEquals("OPLUS_MOBILE", cell.family)
        assertEquals("IMAGE_DRAWABLE", cell.mechanism)
        assertEquals("fallback", cell.state)
        assertEquals(listOf(2, 6, 62, 57), cell.slot)
        assertEquals("cell:per-sub:3:3", cell.level)
        assertEquals("-", cell.reason)
        assertEquals("5", cell.subId)
        assertNull("only CELL lines carry the additive subId field", battery.subId)
        assertNull("only CELL lines carry the additive subId field", wifi.subId)
    }

    @Test
    fun `a total attach failure is still reported through IosDiagnostics`() {
        // Every icon fails (the battery plan names a leaf this tree does not have), so no record is
        // created and the host is unregistered by teardown. The retained attempt must still be read.
        val strip = LinearLayout(context)
        val battery = StatBatteryMeterView(context)
        strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        strip.layout(0, 0, 200, 48)
        battery.layout(0, 0, 90, 48)

        val host = host()
        assertFalse(host.attachIn(strip, null))

        val lines = IosDiagnostics.lines()
        assertEquals(1, lines.size)
        val parsed = parse(lines[0])
        assertEquals("BATTERY", parsed.kind)
        assertEquals("stock", parsed.state)
        assertEquals("no-leaf", parsed.reason)
    }

    @Test
    fun `a SIM added after attach gains its own cell line through IosDiagnostics`() {
        val tree = replacedTree()
        var reading = oneSimReading(5)
        val host = host(cellSource = { reading })
        assertTrue(host.attachIn(tree.strip, null))
        assertEquals(1, IosDiagnostics.lines().count { " CELL " in it })

        val second = addMobile(tree.strip, 6)
        host.onStockIconAdded(second)
        reading = twoSimReading(5, 6)
        host.refresh()

        val cells = IosDiagnostics.lines().filter { " CELL " in it }
        assertEquals(2, cells.size)
        val levels = cells.map { parse(it).level }.toSet()
        assertEquals(setOf("cell:per-sub:3:3", "cell:per-sub:2:2"), levels)
    }

    @Test
    fun `clearAttempts removes a retained failure line`() {
        val line = "ios surface=main bar BATTERY family=OPLUS_BATTERY mech=IMAGE_DRAWABLE " +
            "state=stock slot=0,0,90,48 tint=barTint:FFFFFFFF level=- reason=no-leaf"
        IosDiagnostics.recordAttempt("main bar", listOf(line))
        assertEquals(listOf(line), IosDiagnostics.lines())

        // DuoHook.iosTeardown calls this when iOS mode leaves; no failure may outlive the mode.
        IosDiagnostics.clearAttempts()
        assertTrue(IosDiagnostics.lines().isEmpty())
    }

    @Test
    fun `clearAttempt drops only the named surface attempt`() {
        val main = "ios surface=main bar BATTERY family=OPLUS_BATTERY mech=IMAGE_DRAWABLE " +
            "state=stock slot=0,0,90,48 tint=barTint:FFFFFFFF level=- reason=no-leaf"
        val shade = "ios surface=shade header BATTERY family=OPLUS_BATTERY mech=IMAGE_DRAWABLE " +
            "state=stock slot=0,0,90,48 tint=barTint:FFFFFFFF level=- reason=no-leaf"
        IosDiagnostics.recordAttempt("main bar", listOf(main))
        IosDiagnostics.recordAttempt("shade header", listOf(shade))

        IosDiagnostics.clearAttempt("main bar")
        assertEquals(listOf(shade), IosDiagnostics.lines())
    }

    @Test
    fun `a retained attempt with the same text as a live source dedupes to one`() {
        val line = "ios surface=main bar BATTERY family=OPLUS_BATTERY mech=IMAGE_DRAWABLE " +
            "state=replaced slot=0,0,79,48 tint=barTint:FFFFFFFF level=battery:sticky:50 reason=-"
        val source = object : IosDiagnosticSource {
            override fun diagnosticLines(): List<String> = listOf(line)
        }
        IosDiagnostics.register(source)
        IosDiagnostics.recordAttempt("stale", listOf(line, line))

        assertEquals(1, IosDiagnostics.lines().count { it == line })
        IosDiagnostics.unregister(source)
    }

    @Test
    fun `a successful re-attach clears the retained failure attempt`() {
        // First attach fails (no drawing leaf), so the host retains the stock snapshot.
        val broken = LinearLayout(context)
        val battery = StatBatteryMeterView(context)
        broken.addView(battery, LinearLayout.LayoutParams(90, 48))
        broken.layout(0, 0, 200, 48)
        battery.layout(0, 0, 90, 48)

        val failing = host()
        assertFalse(failing.attachIn(broken, null))
        assertTrue(IosDiagnostics.lines().any { it.contains("reason=no-leaf") })

        // The same surface attaches for real; clearAttempt must drop the stale stock line.
        val tree = replacedTree()
        val attached = host()
        assertTrue(attached.attachIn(tree.strip, null))

        val lines = IosDiagnostics.lines()
        assertEquals(3, lines.size)
        assertTrue("the retained no-leaf attempt must not survive a real attach",
            lines.none { it.contains("reason=no-leaf") })
        assertEquals(1, lines.count { parse(it).kind == "BATTERY" })
    }

    @Test
    fun `overlay host names the duplicate subId0 cell reason`() {
        val tree = overlayTree()
        val host = overlayHost("overlay-dup") { twoSimReading(5, 6) }
        assertTrue(host.attachIn(tree.strip, tree.root))

        val cells = host.diagnosticLines().filter { " CELL " in it }
        assertEquals(2, cells.size)
        assertTrue(cells.all { it.contains("reason=carrier-duplicate-subId0") })
        host.teardown()
    }

    @Test
    fun `overlay host names no-active-sim when the reading has no samples`() {
        val tree = overlayTree()
        val host = overlayHost("overlay-empty") { noCellReading() }
        assertTrue(host.attachIn(tree.strip, tree.root))

        val cells = host.diagnosticLines().filter { " CELL " in it }
        assertEquals(2, cells.size)
        assertTrue(cells.all { it.contains("reason=no-active-sim") })
        host.teardown()
    }

    @Test
    fun `an overlay SIM added after attach gains its own cell line through IosDiagnostics`() {
        // A one-SIM all-overlay strip, exactly the path IosReplaceHost composes IosIconHost for.
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = ComposeView("battery_composable_view")
        val first = SingleBindableStatusBarComposeIconView("mobile_combo", 5)
        strip.addView(battery, LinearLayout.LayoutParams(55, 27))
        strip.addView(first, LinearLayout.LayoutParams(52, 49))
        root.addView(strip, FrameLayout.LayoutParams(300, 60))
        root.layout(0, 0, 1000, 200)
        strip.layout(0, 0, 300, 60)
        battery.layout(0, 0, 55, 27)
        first.layout(60, 0, 112, 49)

        var reading = oneSimReading(5)
        val host = host(cellSource = { reading })
        assertTrue(host.attachIn(strip, root))
        assertEquals(1, IosDiagnostics.lines().count { " CELL " in it })

        // The ROM adds the second SIM slot live; the reading and the tree both change.
        val second = SingleBindableStatusBarComposeIconView("mobile_combo", 6)
        strip.addView(second, LinearLayout.LayoutParams(52, 49))
        second.layout(120, 0, 172, 49)
        host.onStockIconAdded(second)
        reading = twoSimReading(5, 6)
        host.refresh()

        val cells = IosDiagnostics.lines().filter { " CELL " in it }
        assertEquals("the overlay host must rebuild its reports on the live 1→2 transition", 2, cells.size)
        assertEquals(setOf("5", "6"), cells.mapNotNull { parse(it).subId }.toSet())
        host.teardown()
    }

    @Test
    fun `a missing leaf stays stock and names no-leaf`() {
        val strip = LinearLayout(context)
        val battery = StatBatteryMeterView(context)
        strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        strip.layout(0, 0, 200, 48)
        battery.layout(0, 0, 90, 48)

        val host = host()
        assertFalse(host.attachIn(strip, null))

        val lines = host.diagnosticLines()
        assertEquals(1, lines.size)
        val parsed = parse(lines[0])
        assertEquals("BATTERY", parsed.kind)
        assertEquals("OPLUS_BATTERY", parsed.family)
        assertEquals("stock", parsed.state)
        assertEquals("no-leaf", parsed.reason)
    }

    @Test
    fun `an unavailable cell sample vetoes the plan and leaves the stock icon`() {
        val tree = replacedTree()
        val host = host(cellSource = { oneSimReading(5, src = SignalMapping.SRC_UNAVAILABLE) })
        assertTrue(host.attachIn(tree.strip, null))

        val lines = host.diagnosticLines()
        val cell = parse(lines.first { it.contains(" CELL ") })
        assertEquals("stock", cell.state)
        assertEquals("plan-unavailable", cell.reason)
        assertEquals("cell:unavailable:3:3", cell.level)
        assertSame(tree.mobileDrawable, tree.mobileLeaf.drawable)
        assertTrue(L.recentText().contains("cell plan=none reason=unavailable"))
    }

    @Test
    fun `the line carries every field in a stable order`() {
        val tree = replacedTree()
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        for (line in host.diagnosticLines()) {
            val parsed = parse(line)
            assertTrue(parsed.surface.isNotBlank())
            assertTrue(parsed.family.isNotBlank())
            assertTrue(parsed.mechanism.isNotBlank())
            assertTrue(parsed.state in setOf("replaced", "fallback", "stock"))
            assertTrue(parsed.tint.startsWith("barTint:") || parsed.tint.contains(':'))
            assertTrue(parsed.tint.substringAfter(':').matches(Regex("[0-9A-F]{8}")))
            assertTrue(parsed.level.isNotBlank())
            assertTrue(parsed.reason.isNotBlank())
            if (parsed.kind == "CELL") {
                assertTrue(
                    "CELL lines must carry subId=<n> or subId=-",
                    parsed.subId == "-" || parsed.subId?.toIntOrNull() != null
                )
            } else {
                assertNull("the subId field is additive to CELL lines only", parsed.subId)
            }
        }
    }

    @Test
    fun `two dual-SIM rows with identical text stay distinct by subId`() {
        val first = "ios surface=main bar CELL family=OPLUS_MOBILE mech=IMAGE_DRAWABLE " +
            "state=fallback slot=0,0,60,63 tint=barTint:FFFFFFFF " +
            "level=cell:per-sub:3:3 reason=- subId=5"
        val second = first.replace("subId=5", "subId=6")
        val source = object : IosDiagnosticSource {
            override fun diagnosticLines(): List<String> = listOf(first, second)
        }
        IosDiagnostics.register(source)

        assertEquals(listOf(first, second), IosDiagnostics.lines())
        IosDiagnostics.unregister(source)
    }

    @Test
    fun `Diag appends the ios lines once after iosMode and leaves prior lines unchanged`() {
        val iosLine = "ios surface=main bar BATTERY family=OPLUS_BATTERY mech=IMAGE_DRAWABLE " +
            "state=replaced slot=1,2,3,4 tint=imageTint:FFFFFFFF level=battery:sticky:50 reason=-"
        val baseline = Diag.collect(context, null, 0, null, null)
        val withLines = Diag.collect(
            context,
            null,
            0,
            null,
            null,
            iosLines = listOf(iosLine, iosLine)
        )

        val baseLines = baseline.lines()
        val lines = withLines.lines()
        val modeIndex = lines.indexOfFirst { it.contains("iosMode=") }
        assertTrue(modeIndex >= 0)

        assertEquals("  $iosLine", lines[modeIndex + 1])
        assertEquals(1, lines.count { it == "  $iosLine" })

        val without = lines.filterIndexed { index, _ -> index != modeIndex + 1 }
        assertEquals(baseLines, without)
    }
}
