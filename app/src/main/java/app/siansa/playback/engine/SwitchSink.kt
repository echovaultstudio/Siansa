package app.siansa.playback.engine

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * A music player's sink with two ways out (the owner, 22 Nov: "Normal playback of any song gets played in its original
 * quality, frequency and bit depth" against "no quiet, stuttering or stops" in a DJ mix): the [mixed] one, through
 * Siansa's mixer at its one rate, where crossfades and DJ blends happen, and the [direct] one, an output of the
 * player's own at each song's own rate and precision (Untouched playback).
 *
 * Which one is asked ([wantDirect]) only when the player lets go of its sink ([reset], as when it stops): never while
 * anything sounds, so the change itself can't be heard as a fault. Everything the player sets up (its listener, clock,
 * volume, attributes) is told to both, so each is ready when its turn comes; what plays goes to the one in use.
 */
@OptIn(UnstableApi::class)
class SwitchSink(private val mixed: AudioSink, private val direct: AudioSink, private val wantDirect: () -> Boolean) : AudioSink {
    /** Whether the player's own output is the one in use now. */
    @Volatile var isDirect: Boolean = wantDirect()
        private set
    private val active: AudioSink get() = if (isDirect) direct else mixed

    override fun setListener(listener: AudioSink.Listener) {
        mixed.setListener(listener)
        direct.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) {
        mixed.setPlayerId(playerId)
        direct.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        mixed.setClock(clock)
        direct.setClock(clock)
    }

    override fun supportsFormat(format: Format): Boolean = active.supportsFormat(format)
    override fun getFormatSupport(format: Format): Int = active.getFormatSupport(format)
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = active.getFormatOffloadSupport(format)
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = active.getCurrentPositionUs(sourceEnded)
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) = active.configure(inputFormat, specifiedBufferSize, outputChannels)
    override fun play() = active.play()
    override fun handleDiscontinuity() = active.handleDiscontinuity()
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean =
        active.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    override fun playToEndOfStream() = active.playToEndOfStream()
    override fun isEnded(): Boolean = active.isEnded
    override fun hasPendingData(): Boolean = active.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        mixed.setPlaybackParameters(playbackParameters)
        direct.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters = active.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        mixed.setSkipSilenceEnabled(skipSilenceEnabled)
        direct.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = active.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        mixed.setAudioAttributes(audioAttributes)
        direct.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = active.audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {
        mixed.setAudioSessionId(audioSessionId)
        direct.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        mixed.setAuxEffectInfo(auxEffectInfo)
        direct.setAuxEffectInfo(auxEffectInfo)
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        mixed.setPreferredDevice(audioDeviceInfo)
        direct.setPreferredDevice(audioDeviceInfo)
    }

    override fun setVirtualDeviceId(virtualDeviceId: Int) {
        mixed.setVirtualDeviceId(virtualDeviceId)
        direct.setVirtualDeviceId(virtualDeviceId)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = active.setOutputStreamOffsetUs(outputStreamOffsetUs)
    override fun getAudioTrackBufferSizeUs(): Long = active.audioTrackBufferSizeUs
    override fun enableTunnelingV21() = active.enableTunnelingV21()
    override fun disableTunneling() = active.disableTunneling()

    override fun setOffloadMode(offloadMode: Int) {
        mixed.setOffloadMode(offloadMode)
        direct.setOffloadMode(offloadMode)
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = active.setOffloadDelayPadding(delayInFrames, paddingInFrames)

    // Each way out keeps the output it was built with: the mixer's for one, the phone's own for the other.
    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) = Unit

    override fun setVolume(volume: Float) {
        mixed.setVolume(volume)
        direct.setVolume(volume)
    }

    override fun pause() = active.pause()
    override fun flush() = active.flush()

    /** The player lets go of its sink: both are cleared, and the one wanted now is in use from here. */
    override fun reset() {
        mixed.reset()
        direct.reset()
        val want = wantDirect()
        if (want != isDirect) {
            isDirect = want
            app.siansa.log.AppLog.i("Engine", "Sound now goes " + if (want) "straight to the phone's output, each song at its own rate (Untouched playback)" else "through Siansa's mixer")
        }
    }

    override fun release() {
        mixed.release()
        direct.release()
    }
}
