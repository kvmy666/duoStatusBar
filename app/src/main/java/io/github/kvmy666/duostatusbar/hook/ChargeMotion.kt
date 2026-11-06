package io.github.kvmy666.duostatusbar.hook

/**
 * The renderer-neutral part of the charging motion.
 *
 * The bolt's flight and its arrival pulse live in Rive; these are the numbers the host must agree on
 * so the Canvas clone and the 20 % alert do not drift. Pure, so a unit test can pin the crossing rule.
 */
internal object ChargeMotion {
    /** ChargeIn is 96 frames at 60 fps; the settle pulse follows for 14 frames. */
    const val SETTLE_START_MS = 1_600L
    const val SETTLE_END_MS = 1_833L
    const val SETTLE_SCALE = 0.08f

    const val SHAKE_MS = 200L
    const val SHAKE_AMPLITUDE_DP = 3.5f

    /**
     * One shake when charging crosses into 20 %. A jump from 19 to 21 counts; a phone that starts
     * charging already at 20 does not (that would fire on every reboot/plug event).
     */
    fun shouldShake(previousLevel: Int, targetLevel: Int, charging: Boolean): Boolean =
        charging && previousLevel < 20 && targetLevel in 20..24
}
