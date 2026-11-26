package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R11/R14: the Honor/ColorOS signal cluster is a sibling of the icon strip, and hiding it must never
 * take the strip (or the element inside it) with it. Only visibility targets are resolved here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtraStockScopeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun rom(vararg ids: String) = RomAdapter(
        id = "test",
        label = "Test",
        systemUiPackage = "com.android.systemui",
        containerIds = listOf("system_icons"),
        batteryId = "battery",
        notes = "test",
        hideContainerIds = ids.toList()
    )

    private fun ids(vararg pairs: Pair<String, Int>): (String) -> Int {
        val map = pairs.toMap()
        return { name -> map[name] ?: 0 }
    }

    @Test
    fun `returns the sibling signal cluster`() {
        val root = FrameLayout(context)
        val cluster = FrameLayout(context).apply { id = 101 }
        root.addView(cluster)

        val targets = ExtraStockScope.targets(
            root,
            rom("signal_cluster_container"),
            ids("signal_cluster_container" to 101)
        )

        assertEquals(listOf(cluster), targets)
    }

    @Test
    fun `skips a cluster that is already GONE`() {
        val root = FrameLayout(context)
        val cluster = FrameLayout(context).apply {
            id = 101
            visibility = View.GONE
        }
        root.addView(cluster)

        val targets = ExtraStockScope.targets(
            root,
            rom("signal_cluster_container"),
            ids("signal_cluster_container" to 101)
        )

        assertTrue(targets.isEmpty())
    }

    @Test
    fun `excludes an ancestor of the strip`() {
        val root = FrameLayout(context)
        val wrapper = FrameLayout(context).apply { id = 101 }
        val strip = FrameLayout(context).apply { id = 102 }
        wrapper.addView(strip)
        root.addView(wrapper)

        val targets = ExtraStockScope.targets(
            root,
            rom("signal_cluster_container"),
            ids("signal_cluster_container" to 101),
            exclude = strip
        )

        assertTrue("a parent that contains the strip must not be hidden", targets.isEmpty())
    }

    @Test
    fun `skips an id that does not resolve`() {
        val root = FrameLayout(context)

        val targets = ExtraStockScope.targets(
            root,
            rom("signal_cluster_container"),
            ids(/* nothing resolves */)
        )

        assertTrue(targets.isEmpty())
    }
}
