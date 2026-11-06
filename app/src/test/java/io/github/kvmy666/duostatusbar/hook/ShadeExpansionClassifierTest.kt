package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class ShadeExpansionClassifierTest {

    @Test
    fun `QS_PANEL maps to QS`() {
        assertEquals(ShadeOwner.QS, ShadeExpansionClassifier.ownerOf("QS_PANEL"))
    }

    @Test
    fun `NOTIFICATION_PANEL maps to NOTIFICATION`() {
        assertEquals(ShadeOwner.NOTIFICATION, ShadeExpansionClassifier.ownerOf("NOTIFICATION_PANEL"))
    }

    @Test
    fun `SHADE_PANEL maps to NONE (aggregate)`() {
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf("SHADE_PANEL"))
    }

    @Test
    fun `unknown maps to NONE`() {
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf("FOO"))
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf(""))
    }

    @Test
    fun `null maps to NONE`() {
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf(null))
    }

    @Test
    fun `non-token substring is false`() {
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf("XQS_PANEL"))
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf("QS_PANELX"))
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf("NOTIFICATION_PANELS"))
        assertEquals(ShadeOwner.NONE, ShadeExpansionClassifier.ownerOf("QS_PANEL_EXTRA"))
    }

    @Test
    fun `token delimited by punctuation still matches`() {
        assertEquals(ShadeOwner.QS, ShadeExpansionClassifier.ownerOf("com.example.PanelType.QS_PANEL"))
        assertEquals(ShadeOwner.NOTIFICATION, ShadeExpansionClassifier.ownerOf("PanelType{NOTIFICATION_PANEL}"))
    }
}