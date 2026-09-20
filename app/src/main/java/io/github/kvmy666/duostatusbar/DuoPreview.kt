package io.github.kvmy666.duostatusbar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView

/**
 * Renders the real `duo.riv` — the same asset the status bar will use — so the animation can be
 * judged on a real screen while Phase 3 (SystemUI integration) is still being written.
 *
 * The legacy View API is used deliberately: it is the stable, widely deployed entry point
 * (`RiveAnimationView`), it needs no worker/threading setup, and it is the same API the SystemUI
 * side will drive. Everything is wrapped so a Rive failure shows an empty box instead of a crash.
 */
@Composable
fun DuoPreview(
    modifier: Modifier = Modifier,
    pixelSize: Int = 220
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((pixelSize + 48).dp)
            .background(Color(0xFF101014)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .height(pixelSize.dp)
                .padding(8.dp),
            factory = { context ->
                RiveAnimationView(context).apply {
                    try {
                        setRiveResource(R.raw.duo)
                        autoplay = true
                    } catch (t: Throwable) {
                        Log.e("DuoSB", "Rive preview setup failed", t)
                    }
                }
            }
        )
    }
}
