package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.R

/**
 * Single replaceable icon source for the four nav tabs. Production art lives in drawable-nodpi
 * (`nav_home`, `nav_custom`, `nav_report`, `nav_settings`); an owner-supplied set can drop in by
 * replacing [icons] without touching layout code. Reset with [reset] (used by tests).
 */
internal object NavIconSource {
    val defaultIcons: Map<DuoPage, Int> = mapOf(
        DuoPage.HOME to R.drawable.nav_home,
        DuoPage.CUSTOMIZATION to R.drawable.nav_custom,
        DuoPage.REPORT to R.drawable.nav_report,
        DuoPage.SETTINGS to R.drawable.nav_settings
    )
    @Volatile
    var icons: Map<DuoPage, Int> = defaultIcons

    fun iconFor(page: DuoPage): Int =
        icons[page] ?: defaultIcons[page] ?: R.drawable.nav_home

    fun reset() {
        icons = defaultIcons
    }
}
