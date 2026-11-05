package app.siansa.playback.engine

import kotlin.math.sqrt

/**
 * Surround sound folded into the mixer's two channels (issue #860). The engine mixes in stereo; a 5.1 or 7.1 song (a
 * Dolby or DTS file decoded on the device, or a multichannel FLAC) used to lose everything but its front left and
 * right, centre (most often the vocals) and surrounds included.
 *
 * Each side takes its front channel, the centre and its surrounds at −3 dB, the usual fold-down (ITU-R BS.775); the
 * LFE is left out, as most fold-downs do. Each side's weights add up to 1, so a song can't clip from it (the way FFmpeg
 * normalises its own downmix).
 *
 * Channels come in Android's order for their count (as Media3 and FFmpeg hand them over): 3 FL FR FC · 4 FL FR BL BR ·
 * 5 FL FR FC BL BR · 6 FL FR FC LFE BL BR · 7 FL FR FC LFE BL BR BC · 8 FL FR FC LFE BL BR SL SR.
 */
object Downmix {
    private val HALF = (1 / sqrt(2.0)).toFloat()

    /** What each input channel adds to the left and to the right, for [channels] channels. */
    class Weights(val left: FloatArray, val right: FloatArray)

    fun weights(channels: Int): Weights {
        val left = FloatArray(channels)
        val right = FloatArray(channels)
        when (channels) {
            1 -> { left[0] = 1f; right[0] = 1f }
            2 -> { left[0] = 1f; right[1] = 1f }
            else -> {
                left[0] = 1f
                right[1] = 1f
                for ((i, role) in roles(channels).withIndex()) {
                    when (role) {
                        Role.CENTRE, Role.BACK_CENTRE -> { left[i] = HALF; right[i] = HALF }
                        Role.LEFT_SURROUND -> left[i] = HALF
                        Role.RIGHT_SURROUND -> right[i] = HALF
                        else -> Unit
                    }
                }
            }
        }
        normalise(left)
        normalise(right)
        return Weights(left, right)
    }

    private enum class Role { FRONT, CENTRE, LFE, LEFT_SURROUND, RIGHT_SURROUND, BACK_CENTRE, OTHER }

    private fun roles(channels: Int): List<Role> = when (channels) {
        3 -> listOf(Role.FRONT, Role.FRONT, Role.CENTRE)
        4 -> listOf(Role.FRONT, Role.FRONT, Role.LEFT_SURROUND, Role.RIGHT_SURROUND)
        5 -> listOf(Role.FRONT, Role.FRONT, Role.CENTRE, Role.LEFT_SURROUND, Role.RIGHT_SURROUND)
        6 -> listOf(Role.FRONT, Role.FRONT, Role.CENTRE, Role.LFE, Role.LEFT_SURROUND, Role.RIGHT_SURROUND)
        7 -> listOf(Role.FRONT, Role.FRONT, Role.CENTRE, Role.LFE, Role.LEFT_SURROUND, Role.RIGHT_SURROUND, Role.BACK_CENTRE)
        8 -> listOf(Role.FRONT, Role.FRONT, Role.CENTRE, Role.LFE, Role.LEFT_SURROUND, Role.RIGHT_SURROUND, Role.LEFT_SURROUND, Role.RIGHT_SURROUND)
        else -> List(channels) { if (it < 2) Role.FRONT else Role.OTHER }
    }

    private fun normalise(w: FloatArray) {
        val sum = w.sum()
        if (sum > 0f) for (i in w.indices) w[i] /= sum
    }
}
