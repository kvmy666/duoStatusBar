package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The iOS property table is a contract with `ios.riv`: the count, the exact names and their order.
 *
 * The Rive runtime itself is not mocked here; the point is that a renamed or dropped property is caught
 * off-device, before a status bar silently draws nothing.
 */
class IosBinderTest {

    private val expected = listOf(
        "part",
        "visible",
        "tint",
        "batteryLevel",
        "batteryState",
        "batteryFillColor",
        "boltOpacity",
        "wifiLevel",
        "cellMode",
        "cellRole",
        "cellBars",
        "cellDots"
    )

    @Test
    fun `property count is twelve and matches the list`() {
        assertEquals(12, IosBinder.IOS_PROPERTY_COUNT)
        assertEquals(IosBinder.IOS_PROPERTY_COUNT, IosBinder.PROPERTY_NAMES.size)
    }

    @Test
    fun `property names are unique and non blank`() {
        assertEquals(IosBinder.PROPERTY_NAMES.size, IosBinder.PROPERTY_NAMES.toSet().size)
        assertTrue(IosBinder.PROPERTY_NAMES.all { it.isNotBlank() })
    }

    @Test
    fun `property names match the contract in order`() {
        assertEquals(expected, IosBinder.PROPERTY_NAMES)
    }
}
