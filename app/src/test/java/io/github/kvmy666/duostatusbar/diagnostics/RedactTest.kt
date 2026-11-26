package io.github.kvmy666.duostatusbar.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R15 privacy: every report line that can carry personal data must pass through [Redact]. These tests
 * plant a value in each category and also prove normal diagnostic lines (classes, versions, timestamps,
 * hex resource ids, pids, package ids) survive untouched.
 */
class RedactTest {

    @Test
    fun `ssid labels keep their label and redact the value`() {
        assertTrue(Redact.text("ssid=HomeNet").contains("ssid=[redacted]"))
        assertFalse(Redact.text("ssid=HomeNet").contains("HomeNet"))
        assertTrue(Redact.text("SSID: \"Home Net\"").contains("SSID: [redacted]"))
        assertFalse(Redact.text("SSID: \"Home Net\"").contains("Home Net"))
        assertTrue(Redact.text("wifi_ssid=Cafe").contains("wifi_ssid=[redacted]"))
        assertFalse(Redact.text("wifi_ssid=Cafe").contains("Cafe"))
    }

    @Test
    fun `carrier labels keep their label and redact the value`() {
        listOf("carrierName=Vodafone", "mCarrierName=Vodafone", "operator=Vodafone", "carrier=Vodafone")
            .forEach { input ->
                val out = Redact.text(input)
                assertTrue("$input -> $out", out.contains("[redacted]"))
                assertFalse("$input -> $out", out.contains("Vodafone"))
            }
    }

    @Test
    fun `international and local phone numbers are redacted`() {
        val intl = Redact.text("call +44 20 7946 0958 now")
        assertFalse("international number leaked: $intl", intl.contains("7946"))
        assertTrue(intl.contains("[redacted]"))

        val local = Redact.text("tel 020 7946 0958")
        assertFalse("local number leaked: $local", local.contains("7946"))
        assertTrue(local.contains("[redacted]"))

        val us = Redact.text("call 555-123-4567")
        assertFalse("US number leaked: $us", us.contains("123-4567"))
    }

    @Test
    fun `email addresses are redacted`() {
        val out = Redact.text("mail a.b+tag@example.com for help")
        assertFalse(out.contains("a.b+tag@example.com"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun `accounts keep their label and redact the value`() {
        assertTrue(Redact.text("account=jdoe").contains("account=[redacted]"))
        assertTrue(Redact.text("accountName=jdoe").contains("accountName=[redacted]"))
        assertTrue(Redact.text("user=jdoe").contains("user=[redacted]"))
        assertFalse(Redact.text("user=jdoe").contains("jdoe"))
    }

    @Test
    fun `imei is redacted both labelled and as a bare 15-digit value`() {
        assertTrue(Redact.text("imei=490154203237518").contains("imei=[redacted]"))
        assertFalse(Redact.text("imei=490154203237518").contains("490154203237518"))
        val bare = Redact.text("id 490154203237518 end")
        assertFalse("bare IMEI leaked: $bare", bare.contains("490154203237518"))
        assertTrue(bare.contains("[redacted]"))
    }

    @Test
    fun `serial numbers are redacted labelled and as the getprop key`() {
        assertTrue(Redact.text("serial=ABC123XYZ").contains("serial=[redacted]"))
        assertFalse(Redact.text("serial=ABC123XYZ").contains("ABC123XYZ"))
        assertTrue(Redact.text("serialno=ABC123XYZ").contains("serialno=[redacted]"))
        val prop = Redact.text("[ro.serialno]: [ABC123XYZ]")
        assertFalse("ro.serialno leaked: $prop", prop.contains("ABC123XYZ"))
        assertTrue(prop.contains("ro.serialno"))
    }

    @Test
    fun `android id is redacted labelled and bare`() {
        assertFalse(Redact.text("android_id=0123456789abcdef").contains("0123456789abcdef"))
        val bare = Redact.text("id 0123456789abcdef end")
        assertFalse("bare android id leaked: $bare", bare.contains("0123456789abcdef"))
    }

    @Test
    fun `bssid and mac addresses are redacted`() {
        assertFalse(Redact.text("bssid=aa:bb:cc:dd:ee:ff").contains("aa:bb:cc:dd:ee:ff"))
        val mac = Redact.text("mac 12:34:56:78:9a:bc")
        assertFalse("MAC leaked: $mac", mac.contains("12:34:56:78:9a:bc"))
    }

    @Test
    fun `notification text keys are redacted`() {
        assertTrue(Redact.text("android.text=Your code is 1234").contains("android.text=[redacted]"))
        assertFalse(Redact.text("android.text=Your code is 1234").contains("Your code is 1234"))
        assertTrue(Redact.text("tickerText=Alice: hi").contains("tickerText=[redacted]"))
        assertFalse(Redact.text("tickerText=Alice: hi").contains("Alice: hi"))
        assertTrue(Redact.text("notification text: Secret").contains("notification text: [redacted]"))
        assertFalse(Redact.text("notification text: Secret").contains("Secret"))
    }

    @Test
    fun `a view-tree text value is redacted while the resource id survives`() {
        val tree = "android.widget.TextView text='Alice sent a message' id=0x7f0a0f26"
        val out = Redact.text(tree)
        assertFalse("view-tree text leaked: $out", out.contains("Alice sent a message"))
        assertTrue(out.contains("text='[redacted]'"))
        assertTrue("resource id must survive", out.contains("0x7f0a0f26"))
    }

    @Test
    fun `normal report lines are not mangled`() {
        val line = "L 2026-11-26T17:23:09.123Z process=com.android.systemui pid=7525 " +
            "class=com.oplus.keyguard.personality.clocks version=16.0.10.600 " +
            "id=0x7f0a0f26 pkg=io.github.kvmy666.duostatusbar uid:pid:tid=0:7525:7530"
        assertEquals(line, Redact.text(line))
    }

    @Test
    fun `redaction is idempotent`() {
        val input = "ssid=HomeNet user=jdoe imei=490154203237518 text='Secret' " +
            "mail a@b.com call +44 20 7946 0958 mac 12:34:56:78:9a:bc id=0x7f0a0f26"
        val once = Redact.text(input)
        assertEquals("a second pass must not change the line", once, Redact.text(once))
        assertTrue(once.contains("0x7f0a0f26"))
    }

    @Test
    fun `line and text agree and text preserves line breaks`() {
        val two = "ssid=A\nuser=b"
        assertEquals("${Redact.line("ssid=A")}\n${Redact.line("user=b")}", Redact.text(two))
        assertEquals(2, Redact.text(two).lines().size)
    }
}
