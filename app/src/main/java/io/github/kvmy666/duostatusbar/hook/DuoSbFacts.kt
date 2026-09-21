package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.L

/**
 * Reports the facts that decide whether Rive can work here at all — read-only, no Rive object, no
 * native call, so it is safe to run in the stage-1 path where nothing can fault.
 *
 * The fact that matters is hardware acceleration. `RiveAnimationView` is a `TextureView`, and a
 * `TextureView` can only produce the `SurfaceTexture` → `Surface` that Rive draws into when its window is
 * hardware accelerated. If this window is not, Rive cannot render here no matter how its renderer type is
 * set, and the Canvas element is the only option — worth knowing before paying for the experiment.
 *
 * Both sides of that are logged: the runtime state (`View.isHardwareAccelerated`, which reads the real
 * attach info) and the declared intent (`FLAG_HARDWARE_ACCELERATED` on the window's layout params).
 */
internal object DuoSbFacts {

    private const val TAG = "DuoSB"
    private const val FLAG_HARDWARE_ACCELERATED = 0x01000000

    fun report(context: Context, root: View, target: ViewGroup?, slotWidth: Int) {
        try {
            val lp = root.layoutParams
            val flags = if (lp is WindowManager.LayoutParams) lp.flags else 0
            val accelerated = root.isHardwareAccelerated

            L.i("--- window facts ---")
            L.i("status bar window: ${root.javaClass.name}")
            L.i("hardware accelerated, runtime: $accelerated")
            L.i("FLAG_HARDWARE_ACCELERATED declared: ${flags and FLAG_HARDWARE_ACCELERATED != 0}")
            L.i("window attached=${root.isAttachedToWindow} size=${root.width}x${root.height}")
            L.i("density=${context.resources.displayMetrics.density}")
            L.i("slot: ${target?.javaClass?.simpleName} ${target?.width}x${target?.height} width=$slotWidth")
            val verdict = if (accelerated) {
                "verdict: a TextureView can get a Surface here - Rive is worth attempting (stage 2)"
            } else {
                "verdict: no hardware accelerated window, so no Surface for TextureView - " +
                    "Rive cannot render here, the Canvas element is the answer"
            }
            L.i(verdict)
        } catch (t: Throwable) {
            L.w("window facts unavailable: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
