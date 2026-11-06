package io.github.kvmy666.duostatusbar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * A small demonstration of one ring design rendered from a prepared drawable asset.
 *
 * The resource is chosen by the caller (Classic always uses `classic_demo`, 2 SIM always uses
 * `design_dual`), so the demo never follows the selected design. The asset is static (PNG), so no
 * animation work is started or disposed here.
 */
@Composable
fun DesignDemo(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF101014)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}
