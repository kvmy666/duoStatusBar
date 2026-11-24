package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.L
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REL210: the additive `ios v2 diag:` block (surfaces/slots, clock, classes, counters, settings) and
 * the one-shot failure gate. The block is on only while iOS mode is on, so an off dump stays
 * byte-identical to the historical one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosV2DiagTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosV2Log.resetForTest()
        IosSlotLayout.resetForTest()
        IosExtras.resetForTest()
        L.clearRecentForTest()
    }

    @After
    fun tearDown() {
        IosSlotLayout.restoreAll()
        IosSlotLayout.resetForTest()
        IosExtras.resetForTest()
        IosV2Log.resetForTest()
        L.clearRecentForTest()
    }

    /** A home tree with the real measured three-level chains for Wi-Fi, cell and battery. */
    private class Tree(context: Context) {
        val root = FrameLayout(context)
        val container = LinearLayout(context)

        val wifiOuter = FrameLayout(context)
        val wifiGroup = LinearLayout(context)
        val wifiCombo = FrameLayout(context)
        val wifiSignal = ImageView(context)

        val mobileOuter = FrameLayout(context)
        val mobileGroup = LinearLayout(context)
        val mobileCombo = FrameLayout(context)
        val signal = ImageView(context)
        val label = ImageView(context)

        val battery = FrameLayout(context)
        val batteryLeaf = ImageView(context)

        init {
            container.orientation = LinearLayout.HORIZONTAL
            root.addView(container)

            wifiOuter.addView(wifiGroup, FrameLayout.LayoutParams(70, 63))
            wifiGroup.addView(wifiCombo, LinearLayout.LayoutParams(70, 51))
            wifiCombo.addView(wifiSignal, FrameLayout.LayoutParams(60, 51))
            container.addView(wifiOuter, LinearLayout.LayoutParams(70, 63))

            mobileOuter.addView(mobileGroup, FrameLayout.LayoutParams(80, 63))
            mobileGroup.addView(mobileCombo, LinearLayout.LayoutParams(80, 63))
            mobileCombo.addView(signal, FrameLayout.LayoutParams(60, 51))
            mobileCombo.addView(label, FrameLayout.LayoutParams(20, 16))
            container.addView(mobileOuter, LinearLayout.LayoutParams(80, 63))

            battery.addView(batteryLeaf, FrameLayout.LayoutParams(82, 51))
            container.addView(battery, LinearLayout.LayoutParams(86, 63))
        }
    }

    private fun glyph(kind: IosIconKind, visual: IosVisual, w: Int, h: Int): IosGlyphDrawable =
        IosDrawables.create(
            kind,
            visual,
            w,
            h,
            relativeSize = false,
            captureColorFilter = true
        )

    private fun registerAll(tree: Tree) {
        IosSlotLayout.screenWidthOverrideForTest = 1272
        IosSlotLayout.hostInkOverrideForTest = 33.48f
        // Production activates the layout before a bind registers a slot (DuoHook).
        IosSlotLayout.activate(ModuleSettings.DEFAULT)

        tree.wifiSignal.setImageDrawable(
            glyph(IosIconKind.WIFI, IosVisual(part = IosPart.WIFI, wifiLevel = 3f), 60, 51)
        )
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))

        tree.signal.setImageDrawable(
            glyph(IosIconKind.CELL, IosVisual(part = IosPart.CELL, cellBars = 3f), 60, 51)
        )
        tree.label.visibility = View.VISIBLE
        tree.label.setImageDrawable(
            glyph(
                IosIconKind.WIFI,
                IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G")),
                20,
                16
            )
        )
        assertTrue(IosSlotLayout.registerMobile(tree.signal, tree.label))

        tree.batteryLeaf.setImageDrawable(
            glyph(IosIconKind.BATTERY, IosVisual(part = IosPart.BATTERY, batteryLevel = 0.5f), 82, 51)
        )
        assertTrue(IosSlotLayout.registerBattery(tree.battery, tree.batteryLeaf))
    }

    private fun iosSettings(): ModuleSettings = ModuleSettings.DEFAULT.copy(
        iosMode = true,
        iosBatteryScale = 120,
        iosBatteryOffset = 5,
        iosWifiScale = 90,
        iosWifiOffset = -3,
        iosCellScale = 110,
        iosCellOffset = 2,
        hideExtras = false,
        simDisplay = "dual",
        iosPercentInside = true
    )

    private fun blockLines(tree: Tree, settings: ModuleSettings): List<String> {
        val shade = FrameLayout(context)
        val dump = Diag.collect(
            context,
            tree.root,
            0,
            settings,
            null,
            extraRoots = listOf("shade window" to shade)
        )
        return dump.lines()
    }

    private fun lineAfter(lines: List<String>, prefix: String): String =
        lines.firstOrNull { it.trimStart().startsWith(prefix) } ?: ""

    // ----------------------------------------------------------------------------- the block

    @Test
    fun `block is present only when iOS mode is on`() {
        val tree = Tree(context)
        registerAll(tree)

        val on = blockLines(tree, iosSettings())
        assertEquals(1, on.count { it.trim() == "ios v2 diag:" })

        val off = Diag.collect(context, tree.root, 0, ModuleSettings.DEFAULT, null)
        assertFalse(off.lines().any { it.contains("ios v2 diag:") })
        // Two off dumps are byte-identical: nothing was added on the off path.
        assertEquals(off, Diag.collect(context, tree.root, 0, ModuleSettings.DEFAULT, null))
    }

    @Test
    fun `surface line carries outer leaf and label px and an empty surface says none`() {
        val tree = Tree(context)
        registerAll(tree)
        val lines = blockLines(tree, iosSettings())

        val home = lineAfter(lines, "slots home:")
        assertTrue("home line: $home", home.contains("wifi(outer="))
        assertTrue("home line: $home", home.contains("leaf="))
        assertTrue("home line: $home", home.contains("cell(outer="))
        assertTrue("home line: $home", home.contains("label="))
        assertTrue("home line: $home", home.contains("battery(outer="))

        // A supplied surface with no registered slot is still named, with slots=none.
        assertTrue(
            lines.any { it.trim().startsWith("slots shade_window:") && it.contains("slots=none") }
        )
    }

    @Test
    fun `clock line carries the last cap source and id state`() {
        val tree = Tree(context)
        registerAll(tree)

        val missing = lineAfter(blockLines(tree, iosSettings()), "clock cap=")
        assertTrue("clock line: $missing", missing.contains("cap=31px"))
        assertTrue("clock line: $missing", missing.contains("source=host"))
        assertTrue("clock line: $missing", missing.contains("id=missing"))

        IosSlotLayout.configure(0x7f0a0123, "com.android.systemui")
        val resolved = lineAfter(blockLines(tree, iosSettings()), "clock cap=")
        assertTrue("clock line: $resolved", resolved.contains("id=resolved"))
        assertTrue("clock line: $resolved", resolved.contains("0x7f0a0123"))
    }

    @Test
    fun `hook class fields and counters and settings are reported`() {
        val tree = Tree(context)
        registerAll(tree)

        IosV2Log.failureOnce("unit.site", IllegalStateException("boom-counter"))
        IosV2Log.retry("unit.retry", "no reading")
        val lines = blockLines(tree, iosSettings())

        val hooks = lineAfter(lines, "hooks engine=")
        assertTrue("hooks: $hooks", hooks.contains("engine=${IosResourceEngine.hookClassesForDiagnostics}"))
        assertTrue("hooks: $hooks", hooks.contains("battery=${IosBatteryV2.hookClassesForDiagnostics}"))

        val hooks2 = lineAfter(lines, "hooks2 stacked=")
        assertTrue("hooks2: $hooks2", hooks2.contains("stacked=${IosStackedSim.hookClassesForDiagnostics}"))
        assertTrue("hooks2: $hooks2", hooks2.contains("placement=${IosMobileTypePlacement.hookClassesForDiagnostics}"))
        assertTrue("hooks2: $hooks2", hooks2.contains("extras=${IosExtras.hookClassesForDiagnostics}"))
        assertTrue("hooks2: $hooks2", hooks2.contains("order=${IosIconOrder.hookClassesForDiagnostics}"))

        val counters = lineAfter(lines, "counters ")
        assertTrue("counters: $counters", counters.contains("failures=1"))
        assertTrue("counters: $counters", counters.contains("retries=1"))
        assertTrue("counters: $counters", counters.contains("firstFailure="))
        assertTrue("counters: $counters", counters.contains("orderPasses="))
        assertTrue("counters: $counters", counters.contains("extrasHidden="))
        assertTrue("counters: $counters", counters.contains("engineCalls="))
        assertTrue("counters: $counters", counters.contains("stackedSwaps="))

        val settings = lineAfter(lines, "settings ")
        assertTrue("settings: $settings", settings.contains("batteryScale=120"))
        assertTrue("settings: $settings", settings.contains("batteryOffset=5"))
        assertTrue("settings: $settings", settings.contains("wifiScale=90"))
        assertTrue("settings: $settings", settings.contains("wifiOffset=-3"))
        assertTrue("settings: $settings", settings.contains("cellScale=110"))
        assertTrue("settings: $settings", settings.contains("cellOffset=2"))
        assertTrue("settings: $settings", settings.contains("hideExtras=false"))
        assertTrue("settings: $settings", settings.contains("simDisplay=dual"))
        assertTrue("settings: $settings", settings.contains("percentInside=true"))
    }

    @Test
    fun `a keyguard root is named keyguard`() {
        val tree = Tree(context)
        registerAll(tree)
        val dump = Diag.collect(
            context,
            tree.root,
            0,
            iosSettings(),
            null,
            extraRoots = listOf("shade window" to KeyguardRootView(context))
        )
        assertTrue(
            dump.lines().any { it.trim().startsWith("slots keyguard:") && it.contains("slots=none") }
        )
    }

    private class KeyguardRootView(context: Context) : FrameLayout(context)

    // ---------------------------------------------------------------- swallowed failure -> once

    /** A group whose resources throw, to exercise a real guarded catch without Xposed. */
    private class ThrowingGroup(context: Context) : FrameLayout(context) {
        var fail = false

        override fun getResources(): Resources =
            if (fail) throw IllegalStateException("boom-resource") else super.getResources()
    }

    @Test
    fun `a swallowed failure logs exactly once with site and reason`() {
        val root = ThrowingGroup(context)
        root.fail = true
        IosExtras.apply(true)

        assertFalse(IosExtras.registerWifiFrom(root))
        assertFalse(IosExtras.registerWifiFrom(root))

        val matches = L.recentText().lines().filter {
            it.contains("ios v2 extras.identifier.wifi_signal failed:")
        }
        assertEquals("expected exactly one once-line, got: $matches", 1, matches.size)
        assertTrue(matches.single(), matches.single().contains("IllegalStateException"))
        assertTrue(matches.single(), matches.single().contains("boom-resource"))
        assertTrue("the counter still counts both occurrences", IosV2Log.failures >= 2)
    }
}
