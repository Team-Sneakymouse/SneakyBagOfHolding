package com.sneakybagofholding.util

import java.util.concurrent.ThreadLocalRandom

/**
 * Matches vanilla client item/orb pickup pitch randomization
 * (`ClientPacketListener` local pickup sound).
 *
 * Pitch range: 0.6 … 3.4 (triangular distribution, mean 2.0).
 */
object PickupSounds {

    private const val PITCH_SPREAD = 0.7f
    private const val PITCH_CENTER = 1.0f
    private const val PITCH_SCALE = 2.0f

    fun randomItemPickupPitch(): Float {
        val random = ThreadLocalRandom.current()
        val delta = random.nextFloat() - random.nextFloat()
        return (delta * PITCH_SPREAD + PITCH_CENTER) * PITCH_SCALE
    }
}
