package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context
import io.github.kvmy666.duostatusbar.L
import org.json.JSONArray
import org.json.JSONObject

/**
 * Measured ROM profiles loaded from `assets/rom-profiles.json` (FR-01).
 *
 * The built-in adapters in [RomDetection] stay the source of truth for anything we have not measured.
 * This only supplies profiles that were read off a real device, and [RomDetection.forThisRom] accepts
 * them only when `measured` is true — so a data edit can add a new device without touching the Kotlin,
 * but it can never silently override an unverified guess.
 *
 * The asset is bundled in the APK, so a profile reaches users only through an app update. There is no
 * remote override channel (see `docs/rom-support-design.md`); do not read this as server-delivered
 * configuration.
 *
 * Parsing is defensive: a missing or malformed asset yields an empty list, never an exception, and the
 * module falls back to the code defaults exactly as before.
 */
internal object RomProfiles {

    private const val ASSET = "rom-profiles.json"
    private const val MODULE_PACKAGE = "io.github.kvmy666.duostatusbar"

    @Volatile
    private var cache: List<RomAdapter>? = null

    /** Reads and caches the bundled profiles. Safe to call from any thread. */
    fun load(context: Context): List<RomAdapter> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val parsed = try {
                readAsset(context)
            } catch (t: Throwable) {
                L.w("rom profiles unreadable: ${t.javaClass.simpleName}: ${t.message}")
                emptyList()
            }
            cache = parsed
        }
        return cache ?: emptyList()
    }

    /**
     * Reads the module's own asset, not the host's.
     *
     * The module runs *inside System UI*, so its [Context] is SystemUI's, and SystemUI has no
     * `rom-profiles.json` — every report showed `FileNotFoundException: rom-profiles.json`, so the
     * measured profiles never loaded on any device. The module's APK is a different package, so the
     * asset is read through that package's context instead (the same route [DuoRiveView] uses for the
     * `.riv`). The direct open stays first so the in-app preview and tests, which already have the
     * asset, keep working.
     */
    private fun readAsset(context: Context): List<RomAdapter> {
        runCatching { context.assets.open(ASSET) }.getOrNull()?.let { stream ->
            return stream.bufferedReader().use { parse(it.readText()) }
        }
        val module = context.createPackageContext(MODULE_PACKAGE, 0)
        return module.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
    }

    /** Parses the JSON body. A blank id or an empty container list skips that entry. */
    fun parse(json: String): List<RomAdapter> = try {
        val array = JSONObject(json).optJSONArray("profiles") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val containerIds = o.optJSONArray("containerIds").toStringList()
                if (id.isBlank() || containerIds.isEmpty()) continue
                add(
                    RomAdapter(
                        id = id,
                        label = o.optString("label", id),
                        systemUiPackage = o.optString("systemUiPackage", "com.android.systemui"),
                        containerIds = containerIds,
                        batteryId = o.optString("batteryId", "battery"),
                        clockId = o.optString("clockId", "clock"),
                        notes = o.optString("notes"),
                        match = o.optJSONArray("match").toStringList(),
                        measured = o.optBoolean("measured", false),
                        apiMin = o.optInt("apiMin", 0),
                        apiMax = o.optInt("apiMax", Int.MAX_VALUE),
                        shadeContainerIds = o.optJSONArray("shadeContainerIds").toStringList(),
                        shadeBatteryId = o.optString("shadeBatteryId").trim(),
                        shadeStatusIconIds = o.optJSONArray("shadeStatusIconIds").toStringList(),
                        shadeMeasured = o.optBoolean("shadeMeasured", false),
                        coverHook = if (o.has("coverHook")) o.optBoolean("coverHook", true) else true,
                        panelExpansionKind = parsePanelExpansionKind(o),
                        panelHookMeasured = o.optBoolean("panelHookMeasured", false),
                        panelOwnerAvailable = o.optBoolean("panelOwnerAvailable", false)
                    )
                )
            }
        }
    } catch (t: Throwable) {
        L.w("rom profiles malformed: ${t.javaClass.simpleName}: ${t.message}")
        emptyList()
    }

    private val ALLOWED_PANEL_KINDS = setOf(
        "panel_type_5arg", "fzz_3arg", "zfz_3arg", "listener", "none", "unknown"
    )

    private fun parsePanelExpansionKind(o: JSONObject): String {
        if (!o.has("panelExpansionKind")) return "panel_type_5arg"
        val v = o.optString("panelExpansionKind").trim()
        return if (v in ALLOWED_PANEL_KINDS) v else "unknown"
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
    }
}
