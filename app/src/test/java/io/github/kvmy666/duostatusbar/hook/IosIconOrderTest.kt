package io.github.kvmy666.duostatusbar.hook

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R3 A3: the pure permutation rules, tested without Android.
 *
 * The stock OxygenOS order is `wifi -> mobile -> battery`; iOS wants `mobile -> wifi -> battery`.
 * These tests pin the exact slot order and the insertion indices the view move uses, including the
 * idempotent and missing-slot cases, so the live hook cannot drift from the rule.
 */
class IosIconOrderTest {

    /** The measured `StatusBarIconList$Slot` shape: a name carried by `getName()`. */
    class FakeSlot(private val slotName: String) {
        fun getName(): String = slotName
        override fun toString(): String = "Slot($slotName)"
    }

    /**
     * The measured HyperOS 3 `StatusBarIconList$Slot` shape: only a private `mName` field, no
     * `getName()` (`reverse/smali/xiaomi/.../StatusBarIconList$Slot.smali`).
     */
    class HyperOsSlot(private val mName: String)

    /** A slot-like entry with no name accessor at all. */
    class OpaqueEntry

    /** The measured holder shape: `mSlots` is a live `ArrayList<Slot>`. */
    class SlotListHolder(@JvmField val mSlots: MutableList<Any>)

    @Before
    fun setUp() = IosIconOrder.clearForTest()

    @After
    fun tearDown() = IosIconOrder.clearForTest()

    @Test
    fun `stock wifi then mobile becomes mobile then wifi`() {
        assertEquals(
            listOf("mobile", "wifi"),
            IosIconOrder.permutedSlots(listOf("wifi", "mobile"))
        )
    }

    @Test
    fun `wifi moves immediately after the last mobile and keeps the other slots in order`() {
        assertEquals(
            listOf("mobile", "mobile", "vowifi", "volte", "mobile", "wifi"),
            IosIconOrder.permutedSlots(
                listOf("wifi", "mobile", "mobile", "vowifi", "volte", "mobile")
            )
        )
    }

    @Test
    fun `missing wifi or missing mobile leaves the list unchanged`() {
        val noWifi = listOf("mobile", "battery")
        val noMobile = listOf("wifi", "battery")
        assertEquals(noWifi, IosIconOrder.permutedSlots(noWifi))
        assertEquals(noMobile, IosIconOrder.permutedSlots(noMobile))
    }

    @Test
    fun `permutation is idempotent`() {
        val first = IosIconOrder.permutedSlots(listOf("wifi", "mobile", "mobile", "battery"))
        assertEquals(first, IosIconOrder.permutedSlots(first))
        assertEquals(listOf("mobile", "mobile", "wifi", "battery"), first)
    }

    @Test
    fun `an already iOS-ordered list is not moved again`() {
        val ios = listOf("mobile", "wifi", "battery")
        assertEquals(ios, IosIconOrder.permutedSlots(ios))
    }

    @Test
    fun `activation schedules only on the first activation or a slot-order change`() {
        assertTrue(IosIconOrder.shouldScheduleOnActivate(wasActive = false, changed = false))
        assertTrue(IosIconOrder.shouldScheduleOnActivate(wasActive = false, changed = true))
        assertTrue(IosIconOrder.shouldScheduleOnActivate(wasActive = true, changed = true))
        assertFalse(IosIconOrder.shouldScheduleOnActivate(wasActive = true, changed = false))
    }

    @Test
    fun `moved index is after the last mobile once wifi is removed`() {
        assertEquals(2, IosIconOrder.movedIndex(listOf("wifi", "mobile", "mobile", "battery")))
        assertEquals(1, IosIconOrder.movedIndex(listOf("mobile", "wifi", "battery")))
        assertNull(IosIconOrder.movedIndex(listOf("wifi", "battery")))
        assertNull(IosIconOrder.movedIndex(emptyList()))
    }

    @Test
    fun `restored index is before the first mobile once wifi is removed`() {
        assertEquals(0, IosIconOrder.restoredIndex(listOf("wifi", "mobile", "mobile", "battery")))
        assertEquals(0, IosIconOrder.restoredIndex(listOf("mobile", "wifi", "mobile", "battery")))
        assertEquals(1, IosIconOrder.restoredIndex(listOf("battery", "mobile", "wifi")))
        assertNull(IosIconOrder.restoredIndex(listOf("wifi", "battery")))
        assertNull(IosIconOrder.restoredIndex(emptyList()))
    }

    @Test
    fun `mixed-case slot tags match and a null tag is ignored`() {
        assertEquals(
            listOf("MOBILE", "WIFI"),
            IosIconOrder.permutedSlots(listOf("WIFI", "MOBILE"))
        )
        assertNull(IosIconOrder.movedIndex(listOf(null, "battery")))
        assertNull(IosIconOrder.restoredIndex(listOf(null, "battery")))
    }

    @Test
    fun `slot objects are permuted and restored by identity`() {
        val wifi = FakeSlot("wifi")
        val mobile = FakeSlot("mobile")
        val vowifi = FakeSlot("vowifi")
        val volte = FakeSlot("volte")
        val battery = FakeSlot("battery")
        val live = IosIconOrder.seedSlotEntriesForTest(
            listOf(wifi, mobile, vowifi, volte, battery)
        )

        IosIconOrder.activate()

        assertEquals(5, live.size)
        assertSame(mobile, live[0])
        assertSame(wifi, live[1])
        assertSame(vowifi, live[2])
        assertSame(volte, live[3])
        assertSame(battery, live[4])

        IosIconOrder.restore()

        assertSame(wifi, live[0])
        assertSame(mobile, live[1])
        assertSame(vowifi, live[2])
        assertSame(volte, live[3])
        assertSame(battery, live[4])
    }

    @Test
    fun `a slot inserted at index 0 after capture survives restore exactly once`() {
        val wifi = FakeSlot("wifi")
        val mobile = FakeSlot("mobile")
        val battery = FakeSlot("battery")
        val live = IosIconOrder.seedSlotEntriesForTest(listOf(wifi, mobile, battery))
        // What StatusBarIconList.findOrInsertSlot does for a slot created after the snapshot.
        val dynamic = FakeSlot("dynamic_info")
        live.add(0, dynamic)

        IosIconOrder.activate()
        IosIconOrder.restore()

        assertEquals(4, live.size)
        assertSame(wifi, live[0])
        assertSame(mobile, live[1])
        assertSame(battery, live[2])
        assertSame(dynamic, live[3])
        assertEquals(1, live.count { it === dynamic })
    }

    @Test
    fun `restore merge is idempotent and keeps unknown entries after the stock ones`() {
        val wifi = FakeSlot("wifi")
        val mobile = FakeSlot("mobile")
        val battery = FakeSlot("battery")
        val dynamic = FakeSlot("dynamic_info")
        val stock = listOf<Any>(wifi, mobile, battery)
        val live = arrayListOf<Any>(mobile, dynamic, battery, wifi)

        IosIconOrder.restoreSlotsForTest(live, stock)
        assertEquals(listOf<Any>(wifi, mobile, battery, dynamic), live)

        IosIconOrder.restoreSlotsForTest(live, stock)
        assertEquals(listOf<Any>(wifi, mobile, battery, dynamic), live)
        assertSame(wifi, live[0])
        assertSame(mobile, live[1])
        assertSame(battery, live[2])
        assertSame(dynamic, live[3])
    }

    @Test
    fun `hyperos mName-only slots are read, permuted and restored by identity`() {
        val wifi = HyperOsSlot("wifi")
        val mobile = HyperOsSlot("mobile")
        val battery = HyperOsSlot("battery")
        val live = IosIconOrder.seedSlotEntriesForTest(listOf(wifi, mobile, battery))

        val slots = arrayListOf<Any>(HyperOsSlot("wifi"), HyperOsSlot("mobile"))
        assertSame(slots, IosIconOrder.readSlotsForTest(SlotListHolder(slots)))

        IosIconOrder.activate()

        assertEquals(3, live.size)
        assertSame(mobile, live[0])
        assertSame(wifi, live[1])
        assertSame(battery, live[2])

        IosIconOrder.restore()

        assertSame(wifi, live[0])
        assertSame(mobile, live[1])
        assertSame(battery, live[2])
    }

    @Test
    fun `a string slot list still permutes through activate`() {
        val live = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))

        IosIconOrder.activate()

        assertEquals(listOf("mobile", "wifi", "battery"), live)

        IosIconOrder.restore()

        assertEquals(listOf("wifi", "mobile", "battery"), live)
    }

    @Test
    fun `opaque entries with no name accessor are left untouched and do not crash`() {
        val first = OpaqueEntry()
        val second = OpaqueEntry()
        val live = IosIconOrder.seedSlotEntriesForTest(listOf(first, second))

        IosIconOrder.activate()

        assertEquals(2, live.size)
        assertSame(first, live[0])
        assertSame(second, live[1])

        IosIconOrder.restore()

        assertSame(first, live[0])
        assertSame(second, live[1])
    }

    @Test
    fun `release clears the process state, gates capture, and a later install works again`() {
        val live = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        IosIconOrder.markInstalledForTest()
        IosIconOrder.activate()
        assertEquals(listOf("mobile", "wifi"), live)
        assertTrue(IosIconOrder.isActive)
        assertEquals(1, IosIconOrder.slotListCountForTest())

        IosIconOrder.release()

        assertEquals("release restores the stock slot order first", listOf("wifi", "mobile"), live)
        assertFalse(IosIconOrder.isInstalledForTest())
        assertFalse(IosIconOrder.isActive)
        assertEquals(0, IosIconOrder.slotListCountForTest())
        assertEquals(0, IosIconOrder.unhookCountForTest())

        // A late getViewIndex/constructor callback after release must not adopt a list again.
        val holder = SlotListHolder(arrayListOf<Any>(FakeSlot("wifi"), FakeSlot("mobile")))
        IosIconOrder.captureSlotListForTest(holder)
        assertEquals(0, IosIconOrder.slotListCountForTest())

        // Idempotent.
        IosIconOrder.release()
        assertFalse(IosIconOrder.isInstalledForTest())

        // A later iOS session installs and activates again.
        val liveAgain = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        IosIconOrder.markInstalledForTest()
        IosIconOrder.activate()
        assertEquals(listOf("mobile", "wifi"), liveAgain)
        IosIconOrder.restore()
        assertEquals(listOf("wifi", "mobile"), liveAgain)
    }

    @Test
    fun `generic permutation seam resolves names through the caller`() {
        val wifi = FakeSlot("wifi")
        val mobile = FakeSlot("mobile")
        val target = IosIconOrder.permutedEntriesForTest(listOf(wifi, mobile)) {
            (it as FakeSlot).getName()
        }

        assertSame(mobile, target[0])
        assertSame(wifi, target[1])
    }

    @Test
    fun `readSlots accepts a live mutable list of slot objects`() {
        val slots = arrayListOf<Any>(FakeSlot("wifi"), FakeSlot("mobile"))
        val holder = SlotListHolder(slots)

        val read = IosIconOrder.readSlotsForTest(holder)

        assertSame(slots, read)
    }

    @Test
    fun `readSlots rejects a list whose entries cannot resolve a slot name`() {
        val holder = SlotListHolder(arrayListOf<Any>(OpaqueEntry()))

        assertNull(IosIconOrder.readSlotsForTest(holder))
    }
}
