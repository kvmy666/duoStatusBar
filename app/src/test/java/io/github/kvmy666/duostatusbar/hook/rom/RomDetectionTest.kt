package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ROM choice is a pure function of build identity, so it is pinned here rather than discovered on a
 * phone. What matters is not the label but the *ids*: a wrong container list means nothing is injected while
 * the stock bar stays untouched, which looks like "the module does nothing" and is expensive to debug.
 */
class RomDetectionTest {

    /**
     * The measured HyperOS 3 SystemUI APK path, taken verbatim from
     * `.pm/evidence/duo-report-20261113-202904/diagnostics-dump.txt:18`. A Xiaomi brand alone is not
     * HyperOS; this marker is what keeps the measured HyperOS ids on a real MIUI build.
     */
    private val hyperOsMarkers = RomMarkers.of(
        systemUiSourceDir = "/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk"
    )

    /** A successful read of a plain AOSP SystemUI: the only state that proves non-MIUI. */
    private val plainSystemUiMarkers = RomMarkers.of(
        systemUiSourceDir = "/system/priv-app/SystemUI/SystemUI.apk",
        systemUiApplicationClass = "com.android.systemui.SystemUIApplication"
    )

    @Test
    fun `oneplus is recognised as oxygenos and keeps the measured ids unverified`() {
        // CPH2747's SystemUI is /system_ext/priv-app/SystemUI/SystemUI.apk with
        // com.android.systemui.SystemUIApplication, identical to AOSP, so brand/SDK cannot prove
        // OxygenOS: the ids stay, `measured` does not (R18).
        val rom = RomDetection.forThisRom("OnePlus", "OnePlus", "CPH2747", "OnePlus/CPH2747/OP611FL1:16")
        assertEquals("oxygenos", rom.id)
        assertEquals("system_icons", rom.containerIds.first())
        assertEquals("battery", rom.batteryId)
        assertFalse("brand/SDK cannot prove OxygenOS", rom.measured)
        assertTrue(rom.notes.contains("brand-match (unverified)"))
    }

    @Test
    fun `oppo maps to the unverified coloros adapter`() {
        val rom = RomDetection.forThisRom("OPPO", "OPPO", "CPH2451", "OPPO/CPH2451:14")
        assertEquals("coloros", rom.id)
        assertTrue(rom.label.contains("ColorOS"))
        assertTrue("must admit it is unverified", rom.notes.contains("unverified"))
        assertEquals(
            "the vendor signal cluster is outside the system_icons include",
            listOf("signal_cluster"),
            rom.hideContainerIds
        )
        assertTrue(
            "the notes must name the vendor layout evidence",
            rom.notes.contains("oppo_status_bar.xml") && rom.notes.contains("notification_icon_area")
        )
        assertFalse("still not measured", rom.measured)
    }

    @Test
    fun `samsung maps to the unverified one ui adapter`() {
        val rom = RomDetection.forThisRom(
            "samsung", "samsung", "dm1q", "samsung/dm1q/dm1q:14/UP1A/eng:user"
        )
        assertEquals("samsung", rom.id)
        assertTrue(rom.label.contains("One UI"))
        assertTrue("must admit it is unverified", rom.notes.contains("unverified"))
    }

    @Test
    fun `xiaomi maps to the measured hyperos adapter`() {
        // The measured identity with a proven MIUI SystemUI marker keeps the HyperOS choice.
        val rom = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37, hyperOsMarkers
        )
        assertEquals("hyperos", rom.id)
        assertTrue("measured on HyperOS 3, not guessed", rom.measured)
        assertEquals("system_icons", rom.containerIds.first())
        assertTrue("measured on HyperOS 3, not guessed", rom.notes.contains("measured"))
        assertTrue("the wider strip is a fallback", rom.containerIds.contains("system_icon_area"))
    }

    @Test
    fun `anything else falls back to the aosp baseline`() {
        val rom = RomDetection.forThisRom("Google", "google", "husky", "Google/husky:16")
        assertEquals("aosp", rom.id)
        assertEquals("system_icons", rom.containerIds.first())
    }

    @Test
    fun `every adapter probes the aosp id first and names the systemui package`() {
        val roms = listOf(
            RomDetection.forThisRom("OnePlus", "OnePlus", "p", "d"),
            RomDetection.forThisRom("OPPO", "OPPO", "p", "d"),
            RomDetection.forThisRom("samsung", "samsung", "p", "d"),
            RomDetection.forThisRom("Xiaomi", "Redmi", "p", "d"),
            RomDetection.forThisRom("Google", "google", "p", "d")
        )
        for (rom in roms) {
            assertEquals(
                "AOSP spelling first, so a stock ROM needs no special case",
                "system_icons",
                rom.containerIds.first()
            )
            assertEquals("com.android.systemui", rom.systemUiPackage)
            assertTrue("container list must not be empty", rom.containerIds.isNotEmpty())
        }
    }

    @Test
    fun `shade fields default empty so old constructor calls keep compiling`() {
        val rom = RomAdapter(
            id = "legacy",
            label = "Legacy",
            systemUiPackage = "com.android.systemui",
            containerIds = listOf("system_icons"),
            batteryId = "battery",
            notes = "old call site without shade fields"
        )
        assertEquals(emptyList<String>(), rom.shadeContainerIds)
        assertEquals("", rom.shadeBatteryId)
        assertEquals(emptyList<String>(), rom.shadeStatusIconIds)
        assertFalse(rom.shadeMeasured)
        assertFalse(rom.panelOwnerAvailable)
    }

    @Test
    fun `vivo maps to the static originos adapter`() {
        val rom = RomDetection.forThisRom("vivo", "vivo", "V2309A", "OriginOS 6")
        assertEquals("vivo", rom.id)
        assertEquals(listOf("system_icons", "system_icon_area", "statusIcons"), rom.containerIds)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertEquals("brand-match (unverified): static APK OriginOS 6; runtime unverified", rom.notes)
        assertFalse("status and shade were not read off a device", rom.shadeMeasured)
        assertEquals("vivo", RomDetection.forThisRom("iQOO", "iQOO", "p", "d").id)
    }

    @Test
    fun `honor maps to the static magic os adapter`() {
        val rom = RomDetection.forThisRom("HONOR", "HONOR", "BVL-N49", "MagicOS 10")
        assertEquals("honor", rom.id)
        assertEquals(listOf("system_icons", "hw_system_icons", "system_icon_area"), rom.containerIds)
        assertEquals(
            listOf("hw_system_icons", "system_icon_area", "statusIcons"),
            rom.shadeContainerIds
        )
        assertEquals("battery", rom.shadeBatteryId)
        assertEquals(
            "brand-match (unverified): static APK MagicOS 11 old; runtime unverified; vendor " +
                "status_bar.xml puts FrameLayout signal_cluster_container under system_icon_area_fixed, " +
                "a sibling of hw_system_icons, so the strip pass never hides it",
            rom.notes
        )
        assertEquals(
            "the vendor signal cluster is a sibling of the strip",
            listOf("signal_cluster_container"),
            rom.hideContainerIds
        )
        assertFalse(rom.shadeMeasured)
    }

    @Test
    fun `motorola maps to the static myos adapter`() {
        val rom = RomDetection.forThisRom("motorola", "moto", "XT2301", "MyOS 15")
        assertEquals("motorola", rom.id)
        assertEquals(listOf("system_icons", "statusIcons"), rom.containerIds)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertFalse(rom.shadeMeasured)
        assertFalse("must admit the runtime is unverified", rom.notes.contains("measured"))
    }

    @Test
    fun `nothing maps to the static nothing os adapter`() {
        val rom = RomDetection.forThisRom("Nothing", "Nothing", "A065", "Nothing OS 4.0")
        assertEquals("nothing", rom.id)
        assertEquals(listOf("system_icons", "statusIcons"), rom.containerIds)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertFalse(rom.shadeMeasured)
    }

    @Test
    fun `existing adapters carry their static shade maps and keep their selection`() {
        val oxygen = RomDetection.forThisRom("OnePlus", "OnePlus", "p", "d")
        assertEquals("oxygenos", oxygen.id)
        assertEquals(
            listOf("quick_qs_status_icons", "shade_header_system_icons"),
            oxygen.shadeContainerIds
        )
        assertTrue("OnePlus shade was measured on the device", oxygen.shadeMeasured)

        val hyper = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37, hyperOsMarkers
        )
        assertEquals("hyperos", hyper.id)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), hyper.shadeContainerIds)
        assertFalse("HyperOS shade has no device dump yet", hyper.shadeMeasured)

        val samsung = RomDetection.forThisRom("samsung", "samsung", "p", "d")
        assertEquals("samsung", samsung.id)
        assertEquals(listOf("shade_header_system_icons"), samsung.shadeContainerIds)
        assertFalse(samsung.shadeMeasured)

        val color = RomDetection.forThisRom("OPPO", "OPPO", "p", "d")
        assertEquals("coloros", color.id)
        assertEquals(
            listOf("quick_qs_status_icons", "quick_status_bar_header_system_icons", "system_icons"),
            color.shadeContainerIds
        )
        assertFalse(color.shadeMeasured)
    }

    @Test
    fun `every factory carries the audited capability values`() {
        val cases = mapOf(
            "oxygenos" to Triple(true, "panel_type_5arg", true),
            "hyperos" to Triple(true, "fzz_3arg", false),
            "samsung" to Triple(true, "listener", false),
            "vivo" to Triple(true, "zfz_3arg", false),
            "motorola" to Triple(true, "listener", false),
            "nothing" to Triple(true, "fzz_3arg", false),
            "coloros" to Triple(false, "none", false),
            "honor" to Triple(false, "none", false),
            "aosp" to Triple(true, "panel_type_5arg", false)
        )
        val inputs = listOf(
            "OnePlus" to "OnePlus", "Xiaomi" to "Redmi", "samsung" to "samsung",
            "vivo" to "vivo", "motorola" to "moto", "Nothing" to "Nothing",
            "OPPO" to "OPPO", "HONOR" to "HONOR", "Google" to "google"
        )
        // map each to expected id
        val expectedIds = listOf("oxygenos","hyperos","samsung","vivo","motorola","nothing","coloros","honor","aosp")
        for ((i, input) in inputs.withIndex()) {
            // HyperOS now needs a proven SystemUI marker; the other brands ignore it. OxygenOS is
            // never measured from brand/SDK, so no SDK value can claim it (R18).
            val markers = if (expectedIds[i] == "hyperos") hyperOsMarkers else RomMarkers.UNKNOWN
            val rom = RomDetection.forThisRom(input.first, input.second, "p", "d", 36, markers)
            assertEquals(expectedIds[i], rom.id)
            val (cover, kind, measured) = cases[rom.id]!!
            assertEquals(cover, rom.coverHook)
            assertEquals(kind, rom.panelExpansionKind)
            assertEquals(measured, rom.panelHookMeasured)
            assertEquals(rom.id == "oxygenos", rom.panelOwnerAvailable)
        }
    }


    @Test
    fun `oneplus is never measured from sdk alone`() {
        val sdk33 = RomDetection.forThisRom("OnePlus", "OnePlus", "CPH2747", "OnePlus/CPH2747:16", 33)
        assertFalse("SDK 33 is not proof of OxygenOS", sdk33.measured)
        assertTrue(sdk33.notes.contains("brand-match (unverified)"))

        // SDK 36 is CPH2747's build, but its SystemUI is the AOSP one (see RomMarkers.provesFamily),
        // so the SDK cannot promote the brand match to measured (R18).
        val sdk36 = RomDetection.forThisRom("OnePlus", "OnePlus", "CPH2747", "OnePlus/CPH2747:16", 36)
        assertFalse("SDK 36 is not proof of OxygenOS", sdk36.measured)
        assertTrue(sdk36.notes.contains("brand-match (unverified)"))
    }

    @Test
    fun `hyperos is measured only with a proven marker`() {
        val proven = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37, hyperOsMarkers
        )
        assertEquals("hyperos", proven.id)
        assertTrue("the Miui marker proves the family", proven.measured)

        val unknown = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37, RomMarkers.UNKNOWN
        )
        assertEquals("hyperos", unknown.id)
        assertFalse("UNKNOWN is not a proven marker", unknown.measured)
        assertTrue(unknown.notes.contains("brand-match (unverified)"))
    }

    @Test
    fun `xiaomi sdk gating marks measured only on sdk 37`() {
        val sdk37 = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37, hyperOsMarkers
        )
        assertTrue("SDK 37 is the measured build", sdk37.measured)

        val sdk36 = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 36, hyperOsMarkers
        )
        assertFalse("SDK 36 is not the measured build", sdk36.measured)
    }

    @Test
    fun `a data profile whose api range excludes the sdk is rejected`() {
        val profile = RomAdapter(
            id = "hyperos",
            label = "HyperOS / MIUI (measured)",
            systemUiPackage = "com.android.systemui",
            containerIds = listOf("asset_only_container"),
            batteryId = "battery",
            notes = "measured",
            match = listOf("xiaomi"),
            measured = true,
            apiMin = 36,
            apiMax = 36
        )
        val rom = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016",
            listOf(profile), 33, hyperOsMarkers
        )
        assertFalse("profile out of range must not override", rom.measured)
        assertEquals("hyperos", rom.id)
        assertFalse("the out-of-range asset profile is not used", rom.containerIds.contains("asset_only_container"))
        // convenience overload, SDK first
        val rom2 = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 33, listOf(profile), hyperOsMarkers
        )
        assertFalse("profile out of range must not override", rom2.measured)
        assertEquals("hyperos", rom2.id)
        assertFalse("the out-of-range asset profile is not used", rom2.containerIds.contains("asset_only_container"))
    }

    @Test
    fun `unreadable markers keep the old hyperos selection`() {
        // UNKNOWN = the SystemUI package could not be inspected (PackageManager exception, no context,
        // package miss). That is not proof of AOSP: the pre-F-2 brand rule must keep Xiaomi on HyperOS.
        val aospa = RomDetection.forThisRom(
            "Xiaomi",
            "Xiaomi",
            "aospa_sweet",
            "sweet_global-user 13 TKQ1.221013.002 V14.0.9.0.TKFMIXM release-keys",
            37,
            RomMarkers.UNKNOWN
        )
        assertEquals("hyperos", aospa.id)

        // Evidence: .pm/evidence/duo-report-20261110-201707/diagnostics.txt:11-14 - Redmi Note 5 on
        // LineageOS. Without a successful read the old brand rule still reports HyperOS.
        val lineage = RomDetection.forThisRom(
            "Xiaomi", "Redmi", "lineage_whyred", "13", 33, RomMarkers.UNKNOWN
        )
        assertEquals("hyperos", lineage.id)
        assertFalse("SDK 33 is not the measured HyperOS build", lineage.measured)

        // Evidence: .pm/evidence/duo-report-20261118-090105/diagnostics.txt:11-14 - Redmi zircon.
        val poco = RomDetection.forThisRom(
            "Xiaomi", "POCO", "zorn", "zorn-user 13", 36, RomMarkers.UNKNOWN
        )
        assertEquals("hyperos", poco.id)
    }

    @Test
    fun `a read that proves a plain systemui switches xiaomi to aosp`() {
        // Evidence: .pm/evidence/duo-log-20261115-214854.txt:22-25 - Xiaomi sweet running AOSPA was
        // reported as `hyperos (measured=true)`. A successful read of a plain SystemUI proves non-MIUI.
        val aospa = RomDetection.forThisRom(
            "Xiaomi",
            "Xiaomi",
            "aospa_sweet",
            "sweet_global-user 13 TKQ1.221013.002 V14.0.9.0.TKFMIXM release-keys",
            37,
            plainSystemUiMarkers
        )
        assertEquals("aosp", aospa.id)
        assertFalse("a custom ROM is not the measured HyperOS build", aospa.measured)

        val lineage = RomDetection.forThisRom(
            "Xiaomi", "Redmi", "lineage_whyred", "13", 33, plainSystemUiMarkers
        )
        assertEquals("aosp", lineage.id)
        assertFalse(lineage.measured)

        val poco = RomDetection.forThisRom(
            "Xiaomi", "POCO", "zorn", "zorn-user 13", 36, plainSystemUiMarkers
        )
        assertEquals("aosp", poco.id)
        assertFalse(poco.measured)
    }

    @Test
    fun `hyperos marker in the systemui path selects hyperos measured on sdk 37`() {
        // The marker is the measured sourceDir from
        // .pm/evidence/duo-report-20261113-202904/diagnostics-dump.txt:18.
        val rom = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37, hyperOsMarkers
        )
        assertEquals("hyperos", rom.id)
        assertTrue("measured on HyperOS 3", rom.measured)
        assertEquals("system_icons", rom.containerIds.first())
        assertTrue(rom.notes.contains("measured"))
    }

    @Test
    fun `hyperos marker in the application class selects hyperos`() {
        // docs/rom-contract.md:24 lists `com.android.systemui.MiuiSystemUIApplication` as a frozen hook
        // target, so a real MIUI SystemUI may prove itself by its application class instead of the path.
        val byClass = RomMarkers.of(
            systemUiApplicationClass = "com.android.systemui.MiuiSystemUIApplication"
        ).hasHyperOsMarker
        assertTrue("the proven application class is a marker", byClass)

        val rom = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016", 37,
            RomMarkers.of(systemUiApplicationClass = "com.android.systemui.MiuiSystemUIApplication")
        )
        assertEquals("hyperos", rom.id)
        assertTrue("measured on HyperOS 3", rom.measured)

        // The class check is an exact simple-name match, so a look-alike must not count.
        assertFalse(
            "a look-alike class is not the proven marker",
            RomMarkers.of(
                systemUiApplicationClass = "com.android.systemui.MyMiuiSystemUIApplication"
            ).hasHyperOsMarker
        )
    }

    @Test
    fun `a measured asset profile needs a proven family marker`() {
        val assetHyper = RomAdapter(
            id = "hyperos",
            label = "HyperOS / MIUI",
            systemUiPackage = "com.android.systemui",
            containerIds = listOf("asset_only_container"),
            batteryId = "battery",
            notes = "measured",
            match = listOf("xiaomi", "redmi", "poco"),
            measured = true,
            apiMin = 37,
            apiMax = 37
        )
        val profiles = listOf(assetHyper)
        val display = "sweet_global-user 13 TKQ1.221013.002 V14.0.9.0.TKFMIXM release-keys"

        // UNKNOWN is not proof: the measured profile is rejected and the code hyperos adapter is used.
        val unknown = RomDetection.forThisRom(
            "Xiaomi", "Xiaomi", "aospa_sweet", display, profiles, 37, RomMarkers.UNKNOWN
        )
        assertEquals("hyperos", unknown.id)
        assertFalse("UNKNOWN cannot select a measured asset profile", unknown.measured)
        assertFalse("the unproven asset profile is not used", unknown.containerIds.contains("asset_only_container"))

        // A successful read of a plain SystemUI proves non-MIUI, so the profile is rejected and the
        // code path still switches the Xiaomi brand to AOSP (see RomMarkers.provesNonMiui).
        val provenNonMiui = RomDetection.forThisRom(
            "Xiaomi", "Xiaomi", "aospa_sweet", display, profiles, 37, plainSystemUiMarkers
        )
        assertEquals("aosp", provenNonMiui.id)
        assertFalse(provenNonMiui.measured)
        assertFalse("the unproven asset profile is not used", provenNonMiui.containerIds.contains("asset_only_container"))

        // The Miui marker proves the family, so the measured profile is accepted.
        val provenMiui = RomDetection.forThisRom(
            "Xiaomi", "Xiaomi", "aospa_sweet", display, profiles, 37, hyperOsMarkers
        )
        assertEquals("hyperos", provenMiui.id)
        assertTrue("the measured asset profile is accepted with the MIUI marker", provenMiui.measured)
        assertEquals("asset_only_container", provenMiui.containerIds.first())
    }

    @Test
    fun `oxygenos CPH2747 is unchanged with and without markers`() {
        val display = "OnePlus/CPH2747/OP611FL1:16"
        val without = RomDetection.forThisRom("OnePlus", "OnePlus", "CPH2747", display, 36)
        val withMarkers = RomDetection.forThisRom(
            "OnePlus", "OnePlus", "CPH2747", display, 36, hyperOsMarkers
        )

        assertEquals(without.id, withMarkers.id)
        assertEquals("oxygenos", withMarkers.id)
        assertEquals(without.measured, withMarkers.measured)
        assertFalse("brand/SDK cannot prove OxygenOS (R18)", withMarkers.measured)
        assertEquals(without.containerIds, withMarkers.containerIds)
        assertEquals(without.shadeContainerIds, withMarkers.shadeContainerIds)
        assertEquals(without.shadeBatteryId, withMarkers.shadeBatteryId)
        assertEquals(without.panelExpansionKind, withMarkers.panelExpansionKind)
        assertEquals(without.panelOwnerAvailable, withMarkers.panelOwnerAvailable)
        assertEquals("the marker must not leak into OxygenOS", without, withMarkers)
    }

    @Test
    fun `hasHyperOsMarker and provesNonMiui truth table`() {
        // UNKNOWN: nothing was read, so it is neither a marker nor proof of non-MIUI.
        assertFalse("UNKNOWN has no marker", RomMarkers.UNKNOWN.hasHyperOsMarker)
        assertFalse("UNKNOWN proves nothing", RomMarkers.UNKNOWN.provesNonMiui)

        // A successful read of the measured MiuiSystemUI APK: marker, not non-MIUI.
        assertTrue("the MIUI path is a marker", hyperOsMarkers.hasHyperOsMarker)
        assertFalse("a MIUI read is not non-MIUI", hyperOsMarkers.provesNonMiui)

        // A successful read of plain SystemUI: no marker, proves non-MIUI.
        assertFalse("a plain path is not a marker", plainSystemUiMarkers.hasHyperOsMarker)
        assertTrue("a plain read proves non-MIUI", plainSystemUiMarkers.provesNonMiui)

        assertTrue(
            "the APK path check ignores case",
            RomMarkers.of(
                systemUiSourceDir = "/system_ext/priv-app/miuisystemui/MiuiSystemUI.apk"
            ).hasHyperOsMarker
        )
        assertTrue(
            "the application class is the second proven marker",
            RomMarkers.of(
                systemUiApplicationClass = "com.android.systemui.MiuiSystemUIApplication"
            ).hasHyperOsMarker
        )
        assertFalse(
            "a look-alike class is not the proven marker",
            RomMarkers.of(
                systemUiApplicationClass = "com.android.systemui.MyMiuiSystemUIApplication"
            ).hasHyperOsMarker
        )

        // A PackageManager record with neither field proves nothing: it is UNKNOWN, not non-MIUI.
        val emptyRecord = RomMarkers.of()
        assertFalse("an empty record was not usefully read", emptyRecord.read)
        assertFalse("an empty record has no marker", emptyRecord.hasHyperOsMarker)
        assertFalse("an empty record proves nothing", emptyRecord.provesNonMiui)

        // Positive evidence of a plain SystemUI by the application class alone.
        assertTrue(
            "a plain application class proves non-MIUI",
            RomMarkers.of(
                systemUiApplicationClass = "com.android.systemui.SystemUIApplication"
            ).provesNonMiui
        )

        // Positive evidence of a plain SystemUI by the source path alone.
        assertTrue(
            "a plain APK path proves non-MIUI",
            RomMarkers.of(
                systemUiSourceDir = "/system/priv-app/SystemUI/SystemUI.apk"
            ).provesNonMiui
        )

        // Belt and braces: even a hand-written `read = true` with neither field is UNKNOWN, not proof.
        assertFalse(
            "read without any field is still not proof",
            RomMarkers(read = true).provesNonMiui
        )
    }

}
