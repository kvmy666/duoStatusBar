package io.github.kvmy666.duostatusbar.hook

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R6 item 5: the cached float-field reader. No Android or Xposed runtime is involved; the field is
 * plain JVM reflection so the draw path's per-frame lookup can be proven gone off the phone.
 */
class IosFloatFieldTest {

    @After
    fun tearDown() {
        IosFloatField.clearForTest()
    }

    private class Holder(@JvmField var translation: Float = 12.5f) {
        @JvmField
        var other: Float = -4f
    }

    private class WrongType(@JvmField val translation: String = "nope")

    private open class Base {
        @JvmField
        var inherited: Float = 3.25f
    }

    private class Child : Base()

    @Test
    fun `a resolved reader reads a declared float field and sees live changes`() {
        val holder = Holder()
        val reader = IosFloatField.reader(Holder::class.java, "translation")
        assertNotNull(reader)
        assertEquals(12.5f, reader!!.read(holder), 0f)
        holder.translation = 2f
        assertEquals("the cached Field still reads the live value", 2f, reader.read(holder), 0f)
    }

    @Test
    fun `a second field name on the same class is not aliased`() {
        val holder = Holder()
        val other = IosFloatField.reader(Holder::class.java, "other")
        assertNotNull(other)
        assertEquals(-4f, other!!.read(holder), 0f)
    }

    @Test
    fun `reads an inherited float field`() {
        val reader = IosFloatField.reader(Child::class.java, "inherited")
        assertNotNull(reader)
        assertEquals(3.25f, reader!!.read(Child()), 0f)
    }

    @Test
    fun `a missing field yields no reader`() {
        assertNull(IosFloatField.reader(Holder::class.java, "absent"))
        assertNull(IosFloatField.reader(Holder::class.java, ""))
    }

    @Test
    fun `a field of the wrong type yields no reader`() {
        assertNull(IosFloatField.reader(WrongType::class.java, "translation"))
    }
}
