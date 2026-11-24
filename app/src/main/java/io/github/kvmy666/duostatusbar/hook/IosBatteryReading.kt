package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** One battery snapshot read from the sticky `ACTION_BATTERY_CHANGED` broadcast. */
internal data class IosBatteryReading(
    /** Charge percent, already divided by the broadcast's scale so it is always 0..100. */
    val level: Int,
    val plugged: Boolean,
    val status: Int
)

/**
 * The sticky battery broadcast, or null when it cannot be read.
 *
 * A null read must never be turned into 0 %: the caller keeps the previous snapshot instead, so a
 * provider that is momentarily unreadable cannot draw an empty battery (M7/FR-21).
 */
internal fun readStickyBattery(context: Context): IosBatteryReading? = try {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val raw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    if (intent == null || raw < 0) {
        null
    } else {
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        IosBatteryReading(
            level = raw * 100 / scale,
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
        )
    }
} catch (_: Throwable) {
    null
}
