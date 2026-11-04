package app.siansa.playback.gapless

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.siansa.data.cover
import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import app.siansa.playback.CastReceiver
import app.siansa.playback.dlna.LanWatch
import app.siansa.playback.engine.AudioEngine
import app.siansa.playback.toSong
import com.google.android.gms.cast.framework.CastSession
import java.net.InetAddress

/**
 * Gapless casting (issue #114): the phone keeps playing the queue on its own players and mixer, with every crossfade,
 * DJ blend and levelling as on the phone, and instead of its speaker sends the mix to Siansa's TV screen as one
 * endless lossless stream (FLAC, or WAV where a TV won't start FLAC) from a small web server on the phone, played by the
 * TV screen in an audio element of its own (not Google's Cast player). The TV only plays that stream and shows what
 * the phone tells it; its remote's keys come back here as commands. The phone's own controls, notification and lock
 * screen work as usual, as the phone is still the player.
 *
 * - **Stream:** 16-bit FLAC at the mixer's 44.1 kHz, frames sent uncompressed ("verbatim") so the stream has a steady
 *   1.4 Mbit/s: Chrome on the TV buffers by bytes, and a compressed stream that shrinks in quiet passages left it
 *   waiting for data the phone, keeping pace, wouldn't send yet (tried in Chromium, 0.52.0-dev).
 * - **Pace:** the TV is the clock. The mixer writes [LEAD_MS] ahead of what the TV says it plays ([TvClock], [Pacer]),
 *   so the TV has that much in hand for Wi-Fi hiccups, and the phone's position (the song changes, the progress bar)
 *   is what the TV plays.
 * - **Pause** is sent at once (the TV pauses with what it has in hand, and goes on from there). **A skip or a seek**
 *   starts a new stream from the new music, so the TV doesn't play out what it had in hand first: a short gap.
 *
 * Main thread only, apart from what the mixer's thread does through [GaplessOut].
 */
@OptIn(UnstableApi::class)
class CastGapless(
    private val context: Context,
    private val engine: AudioEngine,
    private val player: Player,
    private val source: () -> MusicSource,
    /** The TV can't play the phone's stream: the service hands it the queue instead (before this lets go of the mix). */
    private val onFallback: (String) -> Unit,
    /** Gapless casting ends (the phone left the Wi-Fi): the service pauses the music (before this lets go of the mix). */
    private val onEnd: (String) -> Unit,
    /** Gapless casting started (true) or ended: the phone's own output isn't used meanwhile. */
    private val onActive: (Boolean) -> Unit = {},
    /** A short note on the phone's screen. */
    private val notice: (String) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private var session: CastSession? = null
    private var server: LiveServer? = null
    private var ring: PcmRing? = null
    private var clock: TvClock? = null
    private var out: GaplessOut? = null
    private var host: String = ""
    private var stream = 0
    private var errors = 0
    private var lastJumpAt = 0L
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var lanWatch: LanWatch? = null
    private var tvName = "the TV"
    private var tvKey: String? = null
    private var tries: StreamTries? = null

    /** Whether the phone is casting gaplessly now. */
    val active: Boolean get() = session != null

    /**
     * Starts casting gaplessly to [castSession]'s TV. False (and nothing started) when it can't: Google's TV screen
     * (only Siansa's plays the phone's stream), or no Wi-Fi address. The caller then hands the TV the queue.
     */
    fun start(castSession: CastSession): Boolean {
        if (active) return true
        tvName = castSession.castDevice?.friendlyName ?: "the TV"
        val appId = runCatching { castSession.applicationMetadata?.applicationId }.getOrNull()
        if (appId == null || !CastReceiver.isOwn(appId)) {
            AppLog.w(TAG, "$tvName has Google's standard TV screen ($appId), which can't play the phone's stream: the TV plays the queue instead")
            return false
        }
        val tvIp = runCatching { castSession.castDevice?.inetAddress?.hostAddress }.getOrNull()
        val choice = LanAddress.pick(candidates(), tvIp)
        if (choice == null) {
            AppLog.w(TAG, "No Wi-Fi address for $tvName to reach the phone on (TV at ${tvIp ?: "?"}); the TV plays the queue instead")
            return false
        }
        val r = PcmRing(CHANNELS, AudioEngine.OUTPUT_RATE * RING_SECONDS)
        val c = TvClock(AudioEngine.OUTPUT_RATE)
        val s = LiveServer(r, AudioEngine.OUTPUT_RATE, LiveServer.newKey(), serverLog)
        try {
            s.start(InetAddress.getByName(choice.address))
        } catch (e: Exception) {
            AppLog.w(TAG, "Couldn't start the stream server on ${choice.address}: ${e.message}; the TV plays the queue instead")
            s.stop()
            return false
        }
        AppLog.i(TAG, "Casting gaplessly to $tvName (at ${tvIp ?: "?"}) from ${choice.address}, ${choice.why} (${choice.network.name}), " +
            "${AudioEngine.OUTPUT_RATE} Hz 16-bit, ${LEAD_MS} ms ahead of the TV")
        session = castSession
        ring = r
        clock = c
        server = s
        host = choice.address
        stream = 0
        errors = 0
        tvKey = castSession.castDevice?.deviceId
        // The format that played on this TV before (this app run), else FLAC.
        tries = StreamTries(tvKey?.let { workingFormat[it] } ?: StreamFormat.Flac)
        onActive(true)
        val o = GaplessOut(r, c, AudioEngine.OUTPUT_RATE, LEAD_MS, "$tvName (gapless cast)")
        out = o
        hold()
        runCatching { castSession.setMessageReceivedCallbacks(GaplessMessages.NAMESPACE) { _, _, message -> onMessage(message) } }
            .onFailure { AppLog.w(TAG, "Can't hear from $tvName: ${it.message}") }
        player.addListener(listener)
        lanWatch = LanWatch(context, TAG) {
            // The player stops first, so nothing plays out of the phone's speaker as the mix comes back to it.
            onEnd("the phone left the Wi-Fi")
            stop("the phone left the Wi-Fi")
        }.also { it.start() }
        engine.routeTo(o)
        newStream("start")
        main.postDelayed(tick, TICK_MS)
        return true
    }

    /** The Cast session came back after a moment away: listen to the TV again. */
    fun resumed(castSession: CastSession) {
        if (!active) return
        session = castSession
        runCatching { castSession.setMessageReceivedCallbacks(GaplessMessages.NAMESPACE) { _, _, message -> onMessage(message) } }
        AppLog.i(TAG, "Back in touch with $tvName; starting the stream again")
        newStream("back in touch")
    }

    /** Ends gapless casting: the mix goes back to the phone's own output. Says why in the log. */
    fun stop(why: String) {
        if (!active) return
        AppLog.i(TAG, "Gapless casting ended: $why (${server?.sentFrames?.let { it / AudioEngine.OUTPUT_RATE } ?: 0} s sent on the last stream, slowest write ${server?.slowestWriteMs ?: 0} ms)")
        main.removeCallbacks(tick)
        main.removeCallbacks(cut)
        main.removeCallbacks(connectTimeout)
        main.removeCallbacks(startCheck)
        main.removeCallbacks(startCheckAgain)
        player.removeListener(listener)
        // The TV screen stops the phone's stream (a queue the phone hands it stops it too).
        runCatching { session?.sendMessage(GaplessMessages.NAMESPACE, GaplessMessages.stop()) }
        runCatching { session?.removeMessageReceivedCallbacks(GaplessMessages.NAMESPACE) }
        out?.stopped = true
        engine.routeTo(null)
        server?.stop()
        lanWatch?.stop()
        lanWatch = null
        release()
        session = null
        server = null
        ring = null
        clock = null
        out = null
        tries = null
        onActive(false)
    }

    /**
     * The user skipped, went back or moved in the song (from the app, the notification, the lock screen or the TV's
     * remote). The TV starts a new stream from the new music a moment later, once the player has let go of the old.
     */
    fun userJumped() {
        if (!active) return
        lastJumpAt = System.currentTimeMillis()
        main.removeCallbacks(cut)
        main.postDelayed(cut, CUT_DELAY_MS)
    }

    /** The TV gets the queue instead: the service moves it there (stopping the phone's player), then this ends. */
    private fun fallBack(why: String) {
        onFallback(why)
        stop(why)
    }

    private val cut = Runnable { if (active) newStream("a skip") }

    /**
     * Asks the TV for a new stream from the mix written so far: what it had in hand is dropped. The TV screen plays it
     * in its own audio element, started by a message: Google's Cast player, given it as a LOAD, never got past 0 s on
     * a SHIELD (0.52.0-dev).
     */
    private fun newStream(why: String) {
        val s = session ?: return
        val srv = server ?: return
        val c = clock ?: return
        val r = ring ?: return
        val format = tries?.format ?: StreamFormat.Flac
        stream++
        // The first stream starts with the mixer's next block (nothing is in the ring yet); later ones where the new music does.
        val from = if (r.end == 0L && r.origin == 0L) engine.writtenMix else r.end
        srv.expect(stream)
        c.started(stream, from, System.nanoTime())
        val url = srv.url(host, stream, from, format)
        AppLog.i(TAG, "Stream $stream for $tvName ($why), ${format.label}, from mix frame $from")
        try {
            s.sendMessage(GaplessMessages.NAMESPACE, GaplessMessages.start(stream, url, format))
        } catch (e: Exception) {
            AppLog.w(TAG, "Couldn't ask $tvName for stream $stream: ${e.message}")
        }
        main.removeCallbacks(connectTimeout)
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        main.removeCallbacks(startCheck)
        main.postDelayed(startCheck, START_WAIT_MS)
        sendNow()
    }

    /** No word at all from the TV screen (an old page that doesn't know the phone's stream): the queue instead. */
    private val connectTimeout = Runnable {
        if (!active) return@Runnable
        val c = clock ?: return@Runnable
        if (c.hasReport()) return@Runnable
        val why = "$tvName didn't answer about the phone's stream within ${CONNECT_TIMEOUT_MS / 1000} s (its TV screen may be an old one)"
        AppLog.w(TAG, "$why; the TV plays the queue instead")
        fallBack(why)
    }

    /**
     * The TV hasn't got anywhere in the stream a while after it was asked for it, though the music plays on the phone:
     * the other format next ([StreamTries]), and after that the queue, so nobody sits in silence. (0.52.0-dev: the
     * stream was asked for again every 6 s, for ever, without a sound.)
     */
    private val startCheck: Runnable = Runnable {
        if (!active) return@Runnable
        val c = clock ?: return@Runnable
        if (c.started()) return@Runnable
        if (!player.playWhenReady) {
            // Paused before it started: looked at again once it plays.
            main.postDelayed(startCheckAgain, START_WAIT_MS)
            return@Runnable
        }
        noProgress("didn't get past 0 s of stream $stream within ${START_WAIT_MS / 1000} s")
    }

    private val startCheckAgain: Runnable = Runnable { main.post(startCheck) }

    /** Stream [stream] went nowhere (or failed) for [what]: try the other format, or give up for the queue. */
    private fun noProgress(what: String) {
        val t = tries ?: return
        val failed = t.format
        when (val next = t.failed()) {
            is StreamTries.Next.Retry -> {
                AppLog.w(TAG, "$tvName $what as ${failed.label}; trying ${next.format.label}")
                newStream("${next.format.label} instead of ${failed.label}")
            }
            StreamTries.Next.GiveUp -> {
                val why = "$tvName $what (tried ${t.triedLabels()})"
                AppLog.w(TAG, "$why: giving up gapless; the TV plays the queue instead")
                notice("$tvName couldn't play the sound from this phone, so it plays the queue itself.")
                fallBack(why)
            }
        }
    }

    private fun onMessage(text: String) {
        when (val m = GaplessMessages.parse(text)) {
            is GaplessMessages.FromTv.Clock -> {
                val c = clock ?: return
                val first = !c.hasReport() && m.stream == c.currentStream()
                val wasStarted = c.started()
                if (!c.report(m.stream, m.timeSec, m.moving, System.nanoTime())) return
                if (first) main.post { AppLog.i(TAG, "$tvName answers about stream ${m.stream}: ${engine.status()}") }
                if (!wasStarted && c.started()) main.post { streamStarted(m.stream) }
            }
            is GaplessMessages.FromTv.Command -> main.post { command(m) }
            is GaplessMessages.FromTv.Error -> main.post { error(m) }
            is GaplessMessages.FromTv.Other -> AppLog.d(TAG, "From the TV: ${text.take(200)}")
        }
    }

    /** The TV's remote, done here: the phone is the player. */
    private fun command(m: GaplessMessages.FromTv.Command) {
        if (!active) return
        AppLog.i(TAG, "$tvName's remote: ${m.action}" + (m.toMs?.let { " to ${it / 1000} s" } ?: ""))
        when (m.action) {
            "next" -> player.seekToNext()
            "prev" -> player.seekToPrevious()
            "play" -> player.play()
            "pause" -> player.pause()
            "toggle" -> if (player.playWhenReady) player.pause() else player.play()
            "seek" -> m.toMs?.let { player.seekTo(it) }
        }
    }

    /** Stream [n] got going on the TV: the format that works is kept for this TV, and said in the log. */
    private fun streamStarted(n: Int) {
        if (!active || n != stream) return
        val t = tries ?: return
        val first = t.worked()
        tvKey?.let { workingFormat[it] = t.format }
        if (first) AppLog.i(TAG, "$tvName plays the phone's stream as ${t.format.label}: ${engine.status()}")
    }

    private fun error(m: GaplessMessages.FromTv.Error) {
        if (!active || m.stream != stream) return
        errors++
        AppLog.w(TAG, "$tvName couldn't play stream ${m.stream}: ${m.code} ${m.message}")
        main.removeCallbacks(startCheck)
        when {
            clock?.started() != true -> noProgress("couldn't play stream ${m.stream} (error ${m.code})")
            errors > MAX_ERRORS -> {
                val why = "$tvName kept failing to play the phone's stream"
                notice("$tvName couldn't play the sound from this phone, so it plays the queue itself.")
                fallBack(why)
            }
            else -> newStream("after an error")
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A crossfade moves the player to the next song as the blend begins, which the TV hears a lead later; a
            // song that just ended, or a skip (a new stream), the TV hears now.
            val byFade = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && System.currentTimeMillis() - lastJumpAt > JUMP_WINDOW_MS
            main.removeCallbacks(nowLater)
            if (byFade) main.postDelayed(nowLater, engine.extraLeadMs) else sendNow()
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sendNow()
        override fun onPlaybackStateChanged(playbackState: Int) = sendNow()
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) sendNow()
        }
    }

    private val nowLater = Runnable { sendNow() }

    private val tick = object : Runnable {
        var n = 0
        override fun run() {
            if (!active) return
            sendNow()
            // How it goes, every half minute: the lead, the slowest write to the TV, restarts of the stream.
            if (++n % (STATUS_EVERY_MS / TICK_MS).toInt() == 0) {
                AppLog.d(TAG, "Stream $stream: ${engine.status()}, slowest write to the TV ${server?.slowestWriteMs ?: 0} ms, TV quiet for ${clock?.quietMs(System.nanoTime()) ?: 0} ms")
                server?.slowestWriteMs = 0
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    /** Tells the TV the song, where it is and whether it plays. */
    private fun sendNow() {
        val s = session ?: return
        val item = player.currentMediaItem
        val now = if (item == null) {
            GaplessMessages.Now("", "", "", null, 0, 0, false)
        } else {
            val song = item.toSong()
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (song.durationSec * 1000L)
            GaplessMessages.Now(
                title = song.title,
                artist = song.artist,
                album = song.album,
                cover = song.cover?.let { runCatching { source().coverUrl(it, TV_COVER) }.getOrNull() },
                positionMs = player.currentPosition,
                durationMs = duration,
                playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
            )
        }
        try {
            s.sendMessage(GaplessMessages.NAMESPACE, GaplessMessages.now(now))
        } catch (e: Exception) {
            AppLog.d(TAG, "Couldn't tell the TV what plays: ${e.message}")
        }
    }

    private val serverLog = object : LiveServer.Log {
        override fun info(text: String) = AppLog.i(TAG, text)
        override fun warn(text: String) = AppLog.w(TAG, text)
    }

    /** The phone's networks, for [LanAddress]. */
    private fun candidates(): List<LanAddress.Candidate<String>> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        return cm.allNetworks.mapNotNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            val link = cm.getLinkProperties(n) ?: return@mapNotNull null
            LanAddress.Candidate(
                id = n.toString(),
                name = link.interfaceName ?: n.toString(),
                wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                addresses = link.linkAddresses.map { a: LinkAddress -> (a.address.hostAddress ?: "") to a.prefixLength },
            )
        }.also { list ->
            AppLog.d(TAG, "Networks: " + list.joinToString("; ") { c ->
                "${c.name}" + listOfNotNull("Wi-Fi".takeIf { c.wifi }, "Ethernet".takeIf { c.ethernet }, "VPN".takeIf { c.vpn }).joinToString(",", " (", ")") +
                    " " + c.addresses.joinToString(",") { "${it.first}/${it.second}" }
            })
        }
    }

    /**
     * Keeps the phone awake and its Wi-Fi quick while casting gaplessly: the phone is the player, and its screen is
     * usually off. Let go when it ends.
     */
    @Suppress("DEPRECATION")
    private fun hold() {
        val app = context.applicationContext
        runCatching {
            wake = app.getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "siansa:gaplessCast")?.apply {
                setReferenceCounted(false)
                acquire(WAKE_MAX_MS)
            }
        }.onFailure { AppLog.w(TAG, "No wake lock: ${it.message}") }
        runCatching {
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifi = app.getSystemService(WifiManager::class.java)?.createWifiLock(mode, "siansa:gaplessCast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { AppLog.w(TAG, "No Wi-Fi lock: ${it.message}") }
    }

    private fun release() {
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        runCatching { wifi?.takeIf { it.isHeld }?.release() }
        wake = null
        wifi = null
    }

    companion object {
        const val TAG = "CastGapless"
        const val CHANNELS = 2
        /** How far ahead of the TV the mix is written: the TV's cushion against Wi-Fi hiccups. */
        const val LEAD_MS = 1_500L
        /** The mix kept for streams started again (after a skip): far more than the lead. */
        const val RING_SECONDS = 10
        const val TV_COVER = 800
        const val CUT_DELAY_MS = 80L
        const val JUMP_WINDOW_MS = 1_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        /** How long a stream may take to get going on the TV before the next format is tried. */
        const val START_WAIT_MS = 8_000L

        /** The format each TV (by Cast device id) played, for this app run. */
        private val workingFormat = java.util.concurrent.ConcurrentHashMap<String, StreamFormat>()
        const val TICK_MS = 5_000L
        const val STATUS_EVERY_MS = 30_000L
        const val MAX_ERRORS = 3
        /** The wake lock's own limit, in case it's never let go: Android warns about locks held with none. */
        const val WAKE_MAX_MS = 12 * 60 * 60 * 1000L
    }
}
