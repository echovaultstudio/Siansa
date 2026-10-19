package app.musix.playback

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import app.musix.log.AppLog
import app.musix.log.Level
import java.io.IOException

/**
 * Records what the player is doing when sound stutters:
 * - audio underruns: the sound card ran out of audio (an audible gap), with how full the buffer was;
 * - buffering: when playback stops to wait for data, for how long, and how much was buffered;
 * - downloads: speed and size of each stream piece, and load errors (detailed logging);
 * - song cache: how much of a song came from the phone instead of the network (detailed logging).
 * Underruns, waits and errors are logged whenever logging is on; the rest only with detailed logging.
 */
@OptIn(UnstableApi::class)
class PlaybackDiagnostics(
    private val player: ExoPlayer,
    private val context: Context,
) : AnalyticsListener {
    private var bufferingSince = 0L
    private var underruns = 0

    private fun where(eventTime: AnalyticsListener.EventTime): String {
        val id = player.currentMediaItem?.mediaId ?: "?"
        val pos = eventTime.currentPlaybackPositionMs / 1000
        val ahead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0) / 1000.0
        val net = if (Streaming.isUnmetered(context)) "wifi" else "mobile"
        val speed = StreamStats.recentKbps().takeIf { it >= 0 }?.let { ", arriving at $it kbit/s" }.orEmpty()
        val needs = StreamStats.neededKbps(id)?.let { ", song needs ~$it kbit/s" }.orEmpty()
        val format = StreamStats.formatOf(id) ?: "original"
        return "song $id ($format) at ${pos}s, ${"%.1f".format(ahead)}s buffered ahead, $net$speed$needs"
    }

    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        underruns++
        AppLog.w(
            "Audio",
            "Underrun #$underruns (sound gap): audio buffer ${bufferSizeMs}ms, ${elapsedSinceLastFeedMs}ms since last fed · ${where(eventTime)}",
            event = "audio.underrun",
            fields = mapOf("count" to underruns, "buffer_ms" to bufferSizeMs, "since_fed_ms" to elapsedSinceLastFeedMs),
        )
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> {
                bufferingSince = SystemClock.elapsedRealtime()
                if (player.playWhenReady) AppLog.i("Audio", "Waiting for data · ${where(eventTime)}", event = "playback.buffering_started", fields = mapOf("position_ms" to eventTime.currentPlaybackPositionMs))
            }
            Player.STATE_READY -> if (bufferingSince != 0L) {
                val waited = SystemClock.elapsedRealtime() - bufferingSince
                bufferingSince = 0L
                if (player.playWhenReady) {
                    // Every buffering's length (issue #237), so rebuffering can be counted and measured.
                    AppLog.event(if (waited > 1500) Level.WARN else Level.INFO, "Audio", "playback.buffering_ended",
                        if (waited > 1500) "Waited ${waited}ms for data before playing · ${where(eventTime)}" else "Ready after ${waited}ms",
                        mapOf("duration_ms" to waited))
                }
            }
            else -> Unit
        }
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (!AppLog.detailOn) return
        val kb = loadEventInfo.bytesLoaded / 1024
        val ms = loadEventInfo.loadDurationMs.coerceAtLeast(1)
        val kbps = loadEventInfo.bytesLoaded * 8 / ms
        val id = loadEventInfo.uri.getQueryParameter("id") ?: "?"
        val format = loadEventInfo.uri.getQueryParameter("format") ?: "original"
        AppLog.d("Load", "Song $id ($format): ${kb} KB in ${ms}ms = ${kbps} kbit/s")
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        if (wasCanceled) return
        val id = loadEventInfo.uri.getQueryParameter("id") ?: "?"
        AppLog.w("Load", "Loading song $id failed after ${loadEventInfo.loadDurationMs}ms: ${error.javaClass.simpleName}: ${error.message} · ${where(eventTime)}",
            event = "stream.load_failed", fields = mapOf("song_id" to id, "duration_ms" to loadEventInfo.loadDurationMs, "error_type" to error.javaClass.simpleName))
    }

    // Why playback starts or stops: these explain a song that loads but doesn't play.
    override fun onPlayWhenReadyChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) {
        val why = when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "play/pause pressed or a new song chosen"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "another app took the audio (audio focus lost)"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "headphones or Bluetooth disconnected"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "a remote control (notification, car, watch)"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "end of song"
            else -> "reason $reason"
        }
        AppLog.i("Audio", (if (playWhenReady) "Play" else "Pause") + ": $why · ${where(eventTime)}",
            event = if (playWhenReady) "playback.play" else "playback.pause",
            fields = mapOf("reason" to why, "by_user" to (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)))
    }

    override fun onPlaybackSuppressionReasonChanged(eventTime: AnalyticsListener.EventTime, playbackSuppressionReason: Int) {
        val why = when (playbackSuppressionReason) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> return AppLog.i("Audio", "Playback no longer held back · ${where(eventTime)}")
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "another app has the audio for a moment (call, navigation, notification)"
            else -> "reason $playbackSuppressionReason"
        }
        AppLog.w("Audio", "Playback held back: $why · ${where(eventTime)}", event = "audio.interrupted", fields = mapOf("reason" to why))
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        AppLog.d("Audio", (if (isPlaying) "Sound playing" else "Sound stopped") + " · ${where(eventTime)}")
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        AppLog.d("Load", "Network estimate ${bitrateEstimate / 1000} kbit/s")
    }

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        AppLog.d(
            "Audio",
            "Format ${format.sampleMimeType} ${format.sampleRate} Hz ${format.channelCount} ch" +
                (if (format.bitrate > 0) " ${format.bitrate / 1000} kbit/s" else ""),
        )
    }

    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
        AppLog.e("Audio", "Audio output error · ${where(eventTime)}", audioSinkError, event = "audio.output_error")
    }

    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
        AppLog.e("Audio", "Audio decoder error · ${where(eventTime)}", audioCodecError, event = "playback.decoder_error")
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        AppLog.e("Audio", "Player error ${error.errorCodeName} · ${where(eventTime)}", error)
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) = Unit

    companion object {
        /** Reports how much of a song was read from the phone's song cache. */
        val cacheListener = object : CacheDataSource.EventListener {
            override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                AppLog.d("Cache", "Read ${cachedBytesRead / 1024} KB from the song cache (cache holds ${cacheSizeBytes / (1024 * 1024)} MB)")
            }

            override fun onCacheIgnored(reason: Int) {
                AppLog.d("Cache", "Song cache skipped (reason $reason)")
            }
        }
    }
}
