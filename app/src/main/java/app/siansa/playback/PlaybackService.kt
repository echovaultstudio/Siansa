package app.siansa.playback

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import app.siansa.playback.dlna.Dlna
import app.siansa.playback.dlna.DlnaDevice
import app.siansa.playback.dlna.DlnaPlayer
import app.siansa.playback.dlna.LanWatch
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.Tracks
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.siansa.AppGraph
import app.siansa.MainActivity
import app.siansa.SiansaApp
import app.siansa.data.PlayEvent
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.mix.ContinuousMode
import app.siansa.log.Level
import app.siansa.log.Trace
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.InterruptedIOException
import java.util.Collections

private const val FALLBACK_KBPS = 320
/** How often a server with a playback timeline (Emby) hears where the playing song is (issue #113). */
private const val TIMELINE_MS = 30_000L


/**
 * Background playback with a media notification, lock-screen controls, ReplayGain, transcoding and scrobbling.
 * While casting, the media session drives a [RemoteCastPlayer] instead of the phone's own player.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    /** [exo] as the rest of the app sees it: converted songs seek on Navidrome (issue #162). */
    private lateinit var phone: ServerSeekPlayer
    private var castPlayer: RemoteCastPlayer? = null
    private var dlnaPlayer: DlnaPlayer? = null
    /** Follows the Wi-Fi while music plays on a DLNA device, to bring it back the moment the phone leaves (issue #624). */
    private var lanWatch: LanWatch? = null
    // Where the music was on the TV as casting began to end, in case the TV forgets its queue while stopping.
    private var castSaved: CastSpot<MediaItem>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var graph: AppGraph
    private var events: Events? = null
    private var crossfader: Crossfader? = null
    private var cacheFactory: CacheDataSource.Factory? = null
    private var precacheJob: Job? = null
    private var precacheWriter: CacheWriter? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // The quality chosen when a song's stream first opens, so a network change can't switch format mid-song.
    private val chosenKbps: MutableMap<String, Int> = Collections.synchronizedMap(object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 200
    })

    override fun onCreate() {
        super.onCreate()
        graph = (application as SiansaApp).graph

        val built = PlayerFactory.build(this, graph, ::kbpsFor)
        exo = built.player
        val player = ServerSeekPlayer(
            exo,
            converted = { id -> graph.downloads.fileFor(id) == null && (chosenKbps[id] ?: Streaming.bitrate(this, graph.settings.current)) > 0 },
            durationMsOf = { id -> SongRegistry.get(id)?.durationSec?.takeIf { it > 0 }?.let { it * 1000L } },
        )
        phone = player
        cacheFactory = built.cacheFactory
        events = Events(player).also { player.addListener(it) }
        // Crossfades work on the ExoPlayer itself: only songs it can jump in (originals, downloads, the cache) fade.
        crossfader = Crossfader(
            main = exo,
            handler = handler,
            newTail = { PlayerFactory.build(this, graph, ::kbpsFor, handleAudioFocus = false).player },
            settings = { graph.settings.current },
            profiles = graph.profiles,
            levelOf = { song -> Levelling.songVolume(song, graph.settings.current, graph.profiles.cached(song.id)) },
            sleepLevel = { sleepLevel },
            allowed = { session?.player === phone && !exo.pauseAtEndOfMediaItems && graph.sleepTimer.state.value?.finishing != true },
            onLevel = { if (session?.player === phone) applyVolume(phone) },
            inDjRadio = {
                (graph.songRadio.seed.value != null && graph.songRadio.mode == ContinuousMode.DJ) ||
                    graph.settings.current.continuousPlay == ContinuousMode.DJ
            },
        ).also { it.start() }
        exo.addAnalyticsListener(PlaybackDiagnostics(exo, this))
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                AppLog.i("Audio", "Decoder $decoderName")
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(CoverBitmapLoader(this, graph.scope))
            .setCallback(object : MediaSession.Callback {
                // Items arrive from the app's controller without their URI, so it's restored here.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: Uri.parse(graph.source.streamUrl(item.mediaId))
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()
        // Android may refuse to start the service in the foreground; the player waits instead of the app crashing (issue #602).
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                AppLog.w("Player", "Android would not start playback in the foreground; paused instead")
                session?.player?.pause()
            }
        })

        // The sleep timer: check it while it runs, and put everything back when it's turned off.
        mainScope.launch {
            graph.sleepTimer.state.collect { timer ->
                handler.removeCallbacks(sleepTick)
                if (timer == null) endSleepFade(exo) else handler.post(sleepTick)
            }
        }

        // Casting: playback moves to the TV when a Cast session starts and back when it ends.
        Cast.whenReady(this) { cast ->
            if (session == null) return@whenReady
            val remote = RemoteCastPlayer.Builder(this).setMediaItemConverter(CastItems(graph.source)).build()
            castPlayer = remote
            remote.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    moveTo(remote)
                    // Only one place plays at a time: a TV picked through DLNA lets go.
                    Dlna.select(null)
                }
                override fun onCastSessionUnavailable() {
                    if (session?.player === remote) moveTo(phone)
                }
            })
            cast.sessionManager.addSessionManagerListener(object : SessionManagerListener<CastSession> {
                override fun onSessionEnding(castSession: CastSession) {
                    if (session?.player === remote) castSaved = spotOf(remote)
                }
                override fun onSessionStarting(castSession: CastSession) { castSaved = null }
                override fun onSessionStarted(castSession: CastSession, sessionId: String) {}
                override fun onSessionStartFailed(castSession: CastSession, error: Int) {}
                override fun onSessionEnded(castSession: CastSession, error: Int) {}
                override fun onSessionResuming(castSession: CastSession, sessionId: String) {}
                override fun onSessionResumed(castSession: CastSession, wasSuspended: Boolean) {}
                override fun onSessionResumeFailed(castSession: CastSession, error: Int) {}
                override fun onSessionSuspended(castSession: CastSession, reason: Int) {}
            }, CastSession::class.java)
            if (remote.isCastSessionAvailable) moveTo(remote)
        }

        // DLNA (TVs and speakers without Chromecast): follows the device picked in the app.
        scope.launch(Dispatchers.Main) {
            Dlna.selected.collect { device -> onDlnaDevice(device) }
        }
        // A TV or speaker given up on: a note on screen, so the change isn't a mystery (issue #624).
        scope.launch {
            Dlna.lostDevices.collect { name -> graph.player.notice("Lost the connection to $name. The music is paused on the phone.") }
        }

        // A new quality in Settings → Playback applies straight away: the current song is loaded again at it.
        scope.launch {
            graph.settings.state
                .map { it.transcodeWifiKbps to it.transcodeMobileKbps }
                .distinctUntilChanged()
                .drop(1)
                .collect { withContext(Dispatchers.Main) { onQualitySettingsChanged() } }
        }

        // Levelling changed in Settings (ReplayGain, or where loudness comes from): applies to this song too.
        scope.launch {
            graph.settings.state
                .map { Triple(it.replayGain, it.loudnessSource, it.loudnessTarget) }
                .distinctUntilChanged()
                .drop(1)
                .collect { withContext(Dispatchers.Main) { events?.relevel() } }
        }

        // Send any listens that were waiting from an earlier session.
        scope.launch { runCatching { graph.scrobbler.flush() } }
    }

    private fun kbpsFor(songId: String): Int = chosenKbps.getOrPut(songId) {
        // A file type the phone can't open is converted from the start (issue #354).
        SongRegistry.get(songId)?.suffix?.takeIf { Formats.mustConvert(it) }?.let { suffix ->
            val kbps = Streaming.bitrate(this, graph.settings.current).takeIf { it > 0 } ?: FALLBACK_KBPS
            AppLog.i("Stream", "Song $songId: Opus $kbps kbit/s, as the phone can't open .$suffix files")
            return@getOrPut kbps
        }
        // Otherwise only what's chosen in Settings → Playback: Siansa never switches quality on its own.
        Streaming.bitrate(this, graph.settings.current).also {
            AppLog.i("Stream", "Song $songId: " + if (it > 0) "Opus $it kbit/s" else "original")
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Tells the app's screens what the current song streams as (Opus kbit/s, or 0 for the original file). */
    private fun publishQuality() {
        val s = session ?: return
        val id = s.player.currentMediaItem?.mediaId
        val kbps = id?.let { chosenKbps[it] } ?: 0
        s.setSessionExtras(Bundle().apply {
            putString(StreamQuality.EXTRA_SONG_ID, id)
            putInt(StreamQuality.EXTRA_KBPS, kbps)
        })
    }

    /**
     * Wi-Fi or mobile quality changed in Settings. The next songs pick theirs afresh; the current one is
     * loaded again at the new quality, carrying on from where it was, when it streams at another one.
     */
    private fun onQualitySettingsChanged() {
        val player = session?.player ?: return
        val item = player.currentMediaItem
        val kbps = Streaming.bitrate(this, graph.settings.current)
        val playing = item?.let { chosenKbps[it.mediaId] }
        if (item == null || playing == null || playing == kbps || player.playbackState == Player.STATE_IDLE) {
            // Nothing is streaming at another quality: just forget the choices made for the next songs.
            AppLog.i("Stream", "Quality setting changed to ${if (kbps > 0) "Opus $kbps kbit/s" else "original"}; applies from the next song that loads")
            precacheWriter?.cancel()
            precacheJob?.cancel()
            precachedFor = null
            // A stopped song starts afresh too when it's played again, so only a loaded, playing one keeps its choice.
            if (player.playbackState == Player.STATE_IDLE) chosenKbps.clear() else chosenKbps.keys.retainAll(setOfNotNull(item?.mediaId))
            return
        }
        reloadAt(kbps)
    }

    private var reloads = 0

    /**
     * Loads the current song again at [kbps] (0 = the original file) from where it was, keeping it
     * playing or paused; the next songs are chosen afresh from the settings.
     */
    private fun reloadAt(kbps: Int) {
        val player = session?.player ?: return
        if (player !== phone) {
            AppLog.i("Stream", "Quality change ignored while casting; the TV gets its own stream")
            return
        }
        val item = player.currentMediaItem ?: return
        precacheWriter?.cancel()
        precacheJob?.cancel()
        precachedFor = null
        chosenKbps.clear()
        chosenKbps[item.mediaId] = kbps
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        AppLog.i("Stream", "Song ${item.mediaId}: loading again as ${if (kbps > 0) "Opus $kbps kbit/s" else "the original"} at $position ms, after a quality change in Settings")
        // The plain stream address, pointed at the chosen quality when it opens. The reload marker makes
        // the address differ from before, otherwise the player would carry on with the old stream.
        val uri = Uri.parse(Streaming.withReload(graph.source.streamUrl(item.mediaId), ++reloads))
        player.replaceMediaItem(index, item.buildUpon().setUri(uri).build())
        player.seekTo(index, position)
        publishQuality()
    }

    /**
     * Downloads the next song in the queue into the song cache while this one plays, so the change
     * is instant and survives a dropped connection. Only originals are cached.
     */
    private var precachedFor: String? = null

    private fun precacheNext(player: Player) {
        precacheWriter?.cancel()
        precacheJob?.cancel()
        precachedFor = player.currentMediaItem?.mediaId
        // How many songs to load ahead, as set for Wi-Fi or mobile data (issue #254); 0 loads none.
        val ahead = Preload.count(graph.settings.current, Streaming.isUnmetered(this))
        if (ahead <= 0) return
        val factory = cacheFactory ?: return
        val items = upcomingIndices(player, ahead).drop(1).map { player.getMediaItemAt(it) }.filter { item ->
            // Only originals are cached; downloaded songs are on the phone already.
            item.localConfiguration?.uri != null && kbpsFor(item.mediaId) <= 0 && graph.downloads.fileFor(item.mediaId) == null
        }
        if (items.isEmpty()) return
        // Only called once the current song has fully loaded, so they never share the connection; one at a time.
        AppLog.i("Cache", "Pre-caching the next ${items.size} of $ahead song(s): ${items.joinToString { it.mediaId }}")
        precacheJob = scope.launch {
            for (item in items) {
                val writer = CacheWriter(factory.createDataSourceForDownloading(), DataSpec(item.localConfiguration!!.uri), null, null)
                precacheWriter = writer
                try {
                    writer.cache()
                    AppLog.i("Cache", "Pre-cached song ${item.mediaId}")
                } catch (e: InterruptedIOException) {
                    return@launch // Cancelled because the queue moved on.
                } catch (e: Exception) {
                    AppLog.w("Cache", "Pre-caching ${item.mediaId} failed: ${e.message}")
                }
            }
        }
    }

    /**
     * With the song cache off, songs are held only while they play or wait to (loaded ahead): everything else is
     * removed from its store, so nothing is kept once played.
     */
    private fun dropPlayedSongs(player: Player) {
        val s = graph.settings.current
        if (songCacheOn(s)) return
        val keep = upcomingIndices(player, Preload.count(s, Streaming.isUnmetered(this))).map { player.getMediaItemAt(it).mediaId }.toSet()
        scope.launch {
            runCatching {
                val cache = graph.songCache
                val drop = songCacheKeysToDrop(cache.keys.toList(), keep)
                drop.forEach { key -> runCatching { cache.removeResource(key) } }
                if (drop.isNotEmpty()) AppLog.d("Cache", "Song cache off: removed ${drop.size} played song(s), kept ${keep.size}")
            }.onFailure { AppLog.w("Cache", "Removing played songs failed: ${it.message}") }
        }
    }

    // ---- Sleep timer: finish the song, fade out over its last seconds, then pause ----

    /** The ReplayGain volume of the current song, and how far the sleep timer has faded it (1 = not at all). */
    private var baseVolume = 1f
    private var sleepLevel = 1f

    private fun applyVolume(player: Player) {
        val fade = if (player === phone || player === exo) crossfader?.level ?: 1f else 1f
        player.volume = baseVolume * sleepLevel * fade
    }

    /** Time left in the current song, or -1 when its length isn't known yet. */
    private fun songLeftMs(player: Player): Long {
        // The server's length when the file's header is wrong (beta report 7552a06d), so fades and the timer still fit.
        val duration = songDurationMs(player.duration, player.currentMediaItem?.toSong()?.durationSec ?: 0)
        if (duration <= 0) return -1
        return (duration - player.currentPosition).coerceAtLeast(0)
    }

    /** Puts the volume back and lets the queue play on, after the timer has paused or been turned off. */
    private fun endSleepFade(player: ExoPlayer) {
        if (player.pauseAtEndOfMediaItems) player.pauseAtEndOfMediaItems = false
        if (sleepLevel != 1f) {
            sleepLevel = 1f
            applyVolume(player)
        }
    }

    /** Every second while the timer runs, ten times a second while the last song fades out. */
    private val sleepTick = object : Runnable {
        override fun run() {
            val player = session?.player ?: return
            // On a TV or speaker the phone can't fade its volume or stop it at the song's end: it pauses it instead.
            val onPhone = player === phone
            val timer = graph.sleepTimer
            val now = timer.now()
            if (timer.state.value?.isTimeUp(now) == true) {
                if (!player.playWhenReady) {
                    AppLog.i("Sleep", "Time is up while nothing is playing; timer off")
                    timer.done()
                    return
                }
                timer.timeUp(songLeftMs(player))
            }
            val state = timer.state.value ?: return
            if (state.finishing) {
                // The player itself pauses at the song's end, so the next song never starts.
                if (onPhone && state.cutAt == null && !exo.pauseAtEndOfMediaItems) exo.pauseAtEndOfMediaItems = true
                val left = state.cutAt?.let { it - now } ?: songLeftMs(player)
                if (onPhone) {
                    val level = sleepFade(left)
                    if (level != sleepLevel) {
                        sleepLevel = level
                        applyVolume(player)
                    }
                }
                if (sleepPauseNow(onPhone, state, left)) {
                    if (!onPhone) AppLog.i("Sleep", "Pausing the TV or speaker")
                    player.pause()
                    timer.done()
                    return
                }
            }
            handler.postDelayed(this, if (state.finishing) 100 else 1_000)
        }
    }

    /** Moves the music to the DLNA [device] picked in the app, or back to the phone when it's null. */
    private fun onDlnaDevice(device: DlnaDevice?) {
        val session = session ?: return
        val old = dlnaPlayer
        if (device != null) {
            if (old?.deviceId == device.id && session.player === old) return
            // Casting to a Chromecast stops first; its music comes here instead.
            if (session.player === castPlayer) Cast.disconnect()
            val player = DlnaPlayer(device, graph.source)
            dlnaPlayer = player
            moveTo(player)
            lanWatch?.stop()
            lanWatch = LanWatch(this) { Dlna.lost(device, "the phone left the Wi-Fi") }.also { it.start() }
        } else {
            lanWatch?.stop()
            lanWatch = null
            dlnaPlayer = null
            if (old != null && session.player === old) moveTo(phone)
        }
        if (old != null && old !== dlnaPlayer) old.release()
    }

    /**
     * Moves the queue, song and position between the phone and a Cast device. Music moving to a TV
     * keeps playing; music coming back to the phone waits paused, rather than suddenly playing out loud.
     */
    private fun moveTo(target: Player) {
        val session = session ?: return
        val from = session.player
        if (from === target) return
        val toCast = target !== phone
        val spot = when {
            from === castPlayer -> spotFromTv(from)
            // Hold to stop on a DLNA TV or speaker (issue #491): its queue was cleared, nothing comes back.
            from === dlnaPlayer && !toCast && StopCasting.requested -> { StopCasting.requested = false; CastSpot(emptyList(), 0, 0L) }
            else -> spotOf(from)
        }
        if (toCast) StopCasting.requested = false
        castSaved = null
        val (items, index, position) = spot
        val play = toCast && from.playWhenReady
        AppLog.i(
            "Cast",
            (if (toCast) "Playing on ${(target as? DlnaPlayer)?.name ?: Cast.deviceName() ?: "a Cast device"}" else "Cast ended; music back on the phone, paused") +
                " (${items.size} songs, song ${index + 1} at ${position / 1000}s)",
        )
        precacheWriter?.cancel()
        precacheJob?.cancel()
        crossfader?.cancel("the music moved to another device")
        events?.let { from.removeListener(it) }
        from.stop()
        if (from === phone) exo.clearMediaItems()
        target.repeatMode = from.repeatMode
        if (items.isNotEmpty()) {
            try {
                target.setMediaItems(items, index, position)
                target.playWhenReady = play
                target.prepare()
            } catch (e: RuntimeException) {
                // Better an empty queue than the app closing.
                AppLog.e("Cast", "Couldn't move the queue (${items.size} songs, song ${index + 1})", e)
                target.clearMediaItems()
            }
        }
        events?.attach(target)
        session.player = target
    }

    private fun spotOf(player: Player): CastSpot<MediaItem> {
        val (items, index) = queueInPlayOrder(player)
        return CastSpot(items, index, player.currentPosition.coerceAtLeast(0L))
    }

    /**
     * The TV's queue, made playable on the phone. Songs the TV hands back can lack their stream address,
     * or (when the TV has already let go) be blank; those are rebuilt or taken from [castSaved].
     */
    private fun spotFromTv(tv: Player): CastSpot<MediaItem> {
        val live = spotOf(tv)
        val stopped = StopCasting.requested
        StopCasting.requested = false
        val picked = CastHandBack.afterEnd(live, castSaved, stopped) { it.mediaId }
        if (stopped) AppLog.i("Cast", "Stopped with hold to stop: nothing comes back to the phone")
        if (picked !== live) AppLog.w("Cast", "The TV forgot the queue while stopping; using the one saved as casting ended")
        val spot = CastHandBack.withoutNameless(picked) { it.mediaId }
        return spot.copy(items = spot.items.map(::playableOnPhone))
    }

    private fun playableOnPhone(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        return SongRegistry.get(item.mediaId)?.toMediaItem(graph.source)
            ?: item.buildUpon().setUri(graph.source.streamUrl(item.mediaId)).build()
    }

    /**
     * The queue in the order it will play. Cast devices can't shuffle, so a shuffled queue moves over
     * already in its shuffled order.
     */
    private fun queueInPlayOrder(player: Player): Pair<List<MediaItem>, Int> {
        val count = player.mediaItemCount
        if (count == 0) return emptyList<MediaItem>() to 0
        val current = player.currentMediaItemIndex.coerceIn(0, count - 1)
        if (!player.shuffleModeEnabled) return List(count) { player.getMediaItemAt(it) } to current
        val timeline = player.currentTimeline
        val order = ArrayList<Int>(count)
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET && order.size < count) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        if (order.size != count) return List(count) { player.getMediaItemAt(it) } to current
        return order.map { player.getMediaItemAt(it) } to order.indexOf(current).coerceAtLeast(0)
    }

    /** The current song and the next [ahead] in the order they will play. */
    private fun upcomingIds(player: Player, ahead: Int): List<String> =
        upcomingIndices(player, ahead).map { player.getMediaItemAt(it).mediaId }

    /** The queue positions of the current song and the next [ahead], in the order they will play. */
    private fun upcomingIndices(player: Player, ahead: Int): List<Int> {
        val count = player.mediaItemCount
        if (count == 0) return emptyList()
        val timeline = player.currentTimeline
        var index = player.currentMediaItemIndex.coerceIn(0, count - 1)
        if (timeline.isEmpty) return listOf(index)
        val indices = ArrayList<Int>(ahead + 1)
        while (index != C.INDEX_UNSET && index < count && indices.size <= ahead) {
            indices += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return indices
    }

    /** The book session (issue #521), made when the first book controller connects. */
    private var books: BookSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (controllerInfo.connectionHints.getBoolean(BookSession.HINT_BOOKS, false)) {
            val b = books ?: BookSession(this, graph, pauseMusic = { session?.player?.pause() }).also {
                books = it
                addSession(it.session)
                // A book starting pauses the music (in BookSession); music starting pauses the book.
                session?.player?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) it.player.pause() }
                })
            }
            return b.session
        }
        return session
    }

    /**
     * Media3 builds the notification off the main thread and its foreground start is only guarded on one of two paths,
     * so a refusal while the song is only buffering in the background crashed the app (issue #602). The service is
     * already in the foreground then; the notification catches up when the sound is playing again.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val info = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        if (!mayStartForeground(startInForegroundRequired, session.player.isPlaying, info.importance)) {
            AppLog.d("Player", "Notification left as it is while waiting for data in the background")
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val book = books?.player
        val busy = (player != null && player.playWhenReady && player.mediaItemCount > 0) ||
            (book != null && book.playWhenReady && book.mediaItemCount > 0)
        if (!busy) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepTick)
        mainScope.cancel()
        // Closing the player session ends the wind down too, so the sleep sheet opens with it off (issue #330).
        graph.sleepTimer.setWindDown(false)
        graph.sleepTimer.cancel()
        events?.stopWatches()
        precacheWriter?.cancel()
        events?.finishCurrent()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        lanWatch?.stop()
        lanWatch = null
        dlnaPlayer?.release()
        dlnaPlayer = null
        crossfader?.release()
        crossfader = null
        exo.release()
        books?.release()
        books = null
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Tracks how long each song is actually heard, for scrobbling, and applies ReplayGain. Follows playback
     * to a Cast device and back; the phone-only parts (ReplayGain, pre-caching, watchdogs) rest meanwhile.
     */
    private inner class Events(private var player: Player) : Player.Listener {
        private var current: Song? = null
        // The song playing when playback moved between phone and TV: still the same listen.
        private var carriedOver: String? = null
        private val onPhone get() = player === phone

        fun attach(target: Player) {
            val now = SystemClock.elapsedRealtime()
            playingSince?.let { listenedMs += now - it }
            playingSince = null
            stopWatches()
            carriedOver = current?.id
            player = target
            target.addListener(this)
        }
        private var startedAtMs = 0L
        private var listenedMs = 0L
        private var playingSince: Long? = null

        fun finishCurrent() {
            val song = current ?: return
            playingSince?.let { listenedMs += SystemClock.elapsedRealtime() - it }
            playingSince = null
            graph.scrobbler.finished(song, startedAtMs, listenedMs)
            // The player has moved on by now: how long it was heard stands in for where it stopped.
            graph.scrobbler.stopped(song, listenedMs)
            handler.removeCallbacks(timelineTick)
            val started = startedAtMs
            val heard = listenedMs
            scope.launch { runCatching { graph.history.finish(song.id, started, heard, song.durationSec) } }
            current = null
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The same song swapped for another quality or format: keep counting it as one listen.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem?.mediaId != null && mediaItem.mediaId == current?.id) return
            val moved = carriedOver
            carriedOver = null
            if (moved != null && mediaItem?.mediaId == moved) return
            precacheWriter?.cancel()
            precacheJob?.cancel()
            precachedFor = null
            dropPlayedSongs(player)
            finishCurrent()
            val song = mediaItem?.toSong() ?: return
            current = song
            startedAtMs = System.currentTimeMillis()
            listenedMs = 0
            playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else null
            // A new playback operation: its buffering, requests and errors carry this trace id (issue #237).
            Trace.start("song")
            AppLog.i("Player", "Now playing ${song.id} \"${song.title}\"", event = "playback.track_started", fields = mapOf(
                "song_id" to song.id, "duration_s" to song.durationSec,
                "source" to if (graph.downloads.isDownloaded(song.id)) "offline" else "stream",
            ))
            focusRescued = false
            restarted = false
            resetProgressWatch()
            if (player.playWhenReady) armStartWatch()
            val level = Levelling.decide(song, graph.settings.current, graph.profiles.cached(song.id))
            baseVolume = level.volume
            AppLog.i("Loudness", "Song ${song.id} levelled by ${level.source.name.lowercase()}: ${"%+.1f".format(level.gainDb)} dB (${level.why})")
            if (onPhone) applyVolume(player)
            fetchProfiles(song)
            graph.scrobbler.nowPlaying(song)
            scope.launch {
                runCatching { graph.history.add(PlayEvent(song.id, song.title, song.artist, song.genre, System.currentTimeMillis())) }
            }
        }

        /** Levels the current song again, after a setting changed or its measured loudness arrived. */
        fun relevel(profile: TrackProfile? = null) {
            val song = current ?: return
            val level = Levelling.decide(song, graph.settings.current, profile ?: graph.profiles.cached(song.id))
            val volume = level.volume
            if (volume == baseVolume) return
            AppLog.i("Loudness", "Song ${song.id} now levelled by ${level.source.name.lowercase()}: ${"%+.1f".format(level.gainDb)} dB (${level.why})")
            baseVolume = volume
            if (onPhone) applyVolume(player)
        }

        /**
         * Fetches the measured loudness and fade shapes of this song and the next few, so they're here before
         * each song starts. The first song of a session may get its measurement a moment after it starts.
         */
        private fun fetchProfiles(song: Song) {
            if (!graph.profiles.active) return
            val ids = upcomingIds(player, Preload.profilesAhead(graph.settings.current, Streaming.isUnmetered(this@PlaybackService)))
            scope.launch {
                val found: Map<String, TrackProfile> = graph.profiles.fetch(ids)
                val profile = found[song.id] ?: return@launch
                withContext(Dispatchers.Main) { if (current?.id == song.id) relevel(profile) }
            }
        }

        // The current song is fully downloaded: now the next one can be fetched without slowing it down.
        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (isLoading || !onPhone) return
            val id = player.currentMediaItem?.mediaId ?: return
            val duration = player.duration
            if (precachedFor != id && duration > 0 && player.bufferedPosition >= duration - 1000) precacheNext(player)
        }

        /** Tells a server with a playback timeline (Emby) where the song is, every [TIMELINE_MS] while it plays. */
        private val timelineTick = object : Runnable {
            override fun run() {
                current?.let { graph.scrobbler.progress(it, player.currentPosition, paused = false) }
                if (player.isPlaying) handler.postDelayed(this, TIMELINE_MS)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onIsPlayingChanged2(isPlaying)
            current?.let { graph.scrobbler.progress(it, player.currentPosition, paused = !isPlaying) }
            handler.removeCallbacks(timelineTick)
            if (isPlaying) handler.postDelayed(timelineTick, TIMELINE_MS)
            val now = SystemClock.elapsedRealtime()
            if (isPlaying) {
                if (playingSince == null) playingSince = now
            } else {
                playingSince?.let { listenedMs += now - it }
                playingSince = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.e("Player", "Playback error ${error.errorCodeName} on ${current?.id ?: "?"}", error, event = "playback.error", fields = mapOf(
                "song_id" to current?.id, "code" to error.errorCodeName, "category" to errorCategory(error.errorCode),
            ))
            // A file the phone can't open or decode at all (a WMA, say) plays converted to Opus by Navidrome
            // instead of stopping (issue #354). Tracks the phone can open but not decode are handled in
            // onTracksChanged; this covers the ones that never get that far.
            if (!onPhone || !Formats.convertible(error.errorCode)) return
            val item = player.currentMediaItem ?: return
            if (chosenKbps[item.mediaId] == FALLBACK_KBPS) return
            if (graph.connection.current.offline) return
            AppLog.w("Audio", "The phone can't play ${item.mediaId} (${error.errorCodeName}); playing it as Opus $FALLBACK_KBPS kbit/s from Navidrome",
                event = "playback.recovery", fields = mapOf("song_id" to item.mediaId, "action" to "transcode_opus", "reason" to error.errorCodeName))
            chosenKbps[item.mediaId] = FALLBACK_KBPS
            val uri = item.localConfiguration?.uri ?: return
            player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setUri(Streaming.rewrite(uri, FALLBACK_KBPS)).build())
            player.prepare()
        }

        /**
         * Logs each song's format, and rescues songs whose audio the phone can't decode: instead of
         * silently skipping through them, the song is played again as Opus converted by Navidrome.
         */
        override fun onTracksChanged(tracks: Tracks) {
            publishQuality()
            if (!onPhone) return
            val item = player.currentMediaItem ?: return
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audio.isEmpty()) return
            val format = audio.first().getTrackFormat(0)
            val desc = listOfNotNull(
                format.containerMimeType,
                format.sampleMimeType,
                format.codecs,
                format.sampleRate.takeIf { it > 0 }?.let { "$it Hz" },
                format.channelCount.takeIf { it > 0 }?.let { "$it ch" },
                format.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbit/s" },
            ).joinToString(" · ")
            val playable = audio.any { g -> (0 until g.length).any { g.isTrackSupported(it) } }
            if (playable) {
                AppLog.i("Audio", "Format of ${item.mediaId}: $desc", event = "playback.format", fields = mapOf(
                    "song_id" to item.mediaId, "container" to format.containerMimeType, "codec" to (format.codecs ?: format.sampleMimeType),
                    "sample_rate" to format.sampleRate, "channels" to format.channelCount, "bitrate" to format.bitrate,
                ))
                return
            }
            if (chosenKbps[item.mediaId] == FALLBACK_KBPS) {
                AppLog.e("Audio", "Can't play ${item.mediaId} even as Opus: $desc", event = "playback.unsupported_format", fields = mapOf("song_id" to item.mediaId, "format" to desc))
                return
            }
            AppLog.w("Audio", "This phone can't decode ${item.mediaId} ($desc); playing it as Opus $FALLBACK_KBPS kbit/s from Navidrome",
                event = "playback.recovery", fields = mapOf("song_id" to item.mediaId, "action" to "transcode_opus", "reason" to "unsupported_format", "format" to desc))
            chosenKbps[item.mediaId] = FALLBACK_KBPS
            val uri = item.localConfiguration?.uri ?: return
            player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setUri(Streaming.rewrite(uri, FALLBACK_KBPS)).build())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                finishCurrent()
                // A wind down without a timer ends with its last calm song (issue #330).
                graph.sleepTimer.queueEnded()
                // The queue ran out while the sleep timer was finishing its song.
                if (graph.sleepTimer.state.value?.finishing == true) graph.sleepTimer.done()
            }
        }

        // ---- Start watchdog: a chosen song must actually start playing ----

        private var startRequestedAt = 0L
        private var startRescues = 0
        private var focusRescued = false

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            handler.removeCallbacks(progressWatch)
            watchedPosition = -1L
            if (playWhenReady && onPhone) handler.postDelayed(progressWatch, 1_000)
            // Paused at the end of the last song (or by hand while it fades out): the sleep timer is done.
            if (!playWhenReady && graph.sleepTimer.state.value?.finishing == true &&
                (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            ) {
                graph.sleepTimer.done()
            }
            if (playWhenReady) armStartWatch() else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                // Lost the audio moments after a song was chosen: almost always the app we took over
                // from answering late. Take it back once instead of sitting paused.
                val sinceStart = SystemClock.elapsedRealtime() - startRequestedAt
                if (sinceStart < 3_000 && !focusRescued) {
                    focusRescued = true
                    AppLog.w("Player", "Audio focus lost ${sinceStart}ms after the song was chosen; asking for it again",
                        event = "audio.focus_lost", fields = mapOf("since_start_ms" to sinceStart, "recovery" to "request_again"))
                    handler.postDelayed({ player.play() }, 300)
                }
            }
        }

        private fun armStartWatch() {
            if (!onPhone) return
            startRequestedAt = SystemClock.elapsedRealtime()
            startRescues = 0
            lastBuffered = -1L
            lastBytes = StreamStats.totalBytes()
            handler.removeCallbacks(startWatch)
            handler.postDelayed(startWatch, 2_500)
        }

        private var lastBuffered = -1L
        private var lastBytes = -1L
        private var restarted = false

        /**
         * 2.5 s after a song is chosen (or play is pressed) it should be audible. If it isn't:
         * - held back by audio focus: ask for the audio again;
         * - still waiting for data and nothing new has arrived since the last look: restart the
         *   download from the current position (a stalled connection otherwise waits for a timeout).
         * Checks again every 2.5 s until it plays, up to a few times, logging what it found.
         */
        private val startWatch = object : Runnable {
            override fun run() {
                if (!player.playWhenReady || player.isPlaying || player.mediaItemCount == 0) return
                val id = player.currentMediaItem?.mediaId ?: return
                val waited = SystemClock.elapsedRealtime() - startRequestedAt
                val buffered = player.bufferedPosition
                when {
                    player.playbackState == Player.STATE_READY && player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> {
                        AppLog.w("Player", "Song $id ready but held back (reason ${player.playbackSuppressionReason}) after ${waited}ms; asking for the audio again")
                        player.pause()
                        player.play()
                    }
                    player.playbackState == Player.STATE_IDLE -> {
                        AppLog.w("Player", "Song $id stopped before it started (${player.playerError?.errorCodeName ?: "no error"}) after ${waited}ms; preparing it again")
                        player.prepare()
                    }
                    // Only when nothing at all has arrived since the last look (a big FLAC can be arriving
                    // for a while before the player counts any of it as buffered), and once per song.
                    player.playbackState == Player.STATE_BUFFERING && lastBytes >= 0 && StreamStats.totalBytes() == lastBytes && !restarted -> {
                        restarted = true
                        AppLog.w(
                            "Player",
                            "Song $id waiting ${waited}ms and no data arrived in the last 2.5 s (${buffered / 1000.0}s loaded); restarting its download",
                        )
                        player.seekTo(player.currentMediaItemIndex, player.currentPosition)
                    }
                    else -> AppLog.i("Player", "Song $id not playing yet after ${waited}ms (state ${player.playbackState}, ${buffered / 1000.0}s loaded)")
                }
                lastBuffered = buffered
                lastBytes = StreamStats.totalBytes()
                if (++startRescues < 5) handler.postDelayed(this, 2_500)
            }
        }

        // ---- Progress watchdog: a song whose data is already here must keep moving ----

        private var watchedPosition = -1L
        private var stuckSince = 0L
        private var stuckRescues = 0

        fun stopWatches() {
            handler.removeCallbacks(startWatch)
            handler.removeCallbacks(progressWatch)
        }

        private fun resetProgressWatch() {
            watchedPosition = -1L
            stuckRescues = 0
            handler.removeCallbacks(progressWatch)
            if (player.playWhenReady && onPhone) handler.postDelayed(progressWatch, 1_000)
        }

        /**
         * Checks every second while play is on that the position moves. If it hasn't moved for 5 s
         * although the next seconds of the song are already loaded, the audio decoder has stopped
         * producing sound (seen with FLAC: it only returns empty buffers and the player flips between
         * playing and waiting forever). Nothing downloads more slowly here, so the slow-connection
         * rescues never act. Instead the rest of the song is played as Opus from Navidrome, like a
         * song the phone can't decode at all (restarting the FLAC decoder at the same spot didn't
         * help when tested). If it's already Opus, or sticks again, the decoder is restarted once by
         * seeking to where the song is, and then it's left alone, logging why.
         */
        private val progressWatch = object : Runnable {
            override fun run() {
                if (!player.playWhenReady || !onPhone) return
                handler.postDelayed(this, 1_000)
                val state = player.playbackState
                val now = SystemClock.elapsedRealtime()
                val position = player.currentPosition
                val buffered = player.bufferedPosition
                val duration = player.duration
                val dataHere = buffered - position >= 3_000 || (duration > 0 && buffered >= duration - 500 && position < duration - 2_000)
                val shouldMove = (state == Player.STATE_READY || state == Player.STATE_BUFFERING) &&
                    player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE && dataHere
                if (!shouldMove || watchedPosition < 0 || position > watchedPosition + 300 || position < watchedPosition) {
                    watchedPosition = position
                    stuckSince = now
                    return
                }
                if (now - stuckSince < 5_000) return
                watchedPosition = -1L
                val item = player.currentMediaItem ?: return
                val id = item.mediaId
                val where = "at ${position / 1000}s, ${(buffered - position) / 1000.0}s loaded ahead, state $state"
                val alreadyOpus = (chosenKbps[id] ?: 0) > 0
                when {
                    !alreadyOpus -> {
                        AppLog.w("Player", "Song $id stuck for 5 s with its data loaded ($where); playing the rest as Opus $FALLBACK_KBPS kbit/s from Navidrome",
                            event = "playback.recovery", fields = mapOf("song_id" to id, "action" to "transcode_opus", "reason" to "stuck", "position_ms" to position))
                        chosenKbps[id] = FALLBACK_KBPS
                        val uri = item.localConfiguration?.uri ?: return
                        val index = player.currentMediaItemIndex
                        player.replaceMediaItem(index, item.buildUpon().setUri(Streaming.rewrite(uri, FALLBACK_KBPS)).build())
                        player.seekTo(index, position)
                    }
                    stuckRescues < 2 -> {
                        AppLog.w("Player", "Song $id stuck for 5 s with its data loaded ($where); restarting the decoder",
                            event = "playback.recovery", fields = mapOf("song_id" to id, "action" to "restart_decoder", "reason" to "stuck", "attempt" to stuckRescues))
                        player.seekTo(player.currentMediaItemIndex, position)
                    }
                    else -> {
                        AppLog.e("Player", "Song $id still stuck after ${stuckRescues} rescues ($where); giving up on it",
                            event = "playback.recovery_failed", fields = mapOf("song_id" to id, "attempts" to stuckRescues))
                        handler.removeCallbacks(this)
                        return
                    }
                }
                stuckRescues++
            }
        }

        private fun onIsPlayingChanged2(isPlaying: Boolean) {
            if (!isPlaying || startRequestedAt == 0L) return
            val waited = SystemClock.elapsedRealtime() - startRequestedAt
            startRequestedAt = 0L
            lastBuffered = -1L
            handler.removeCallbacks(startWatch)
            // Time to first audio (issue #237): always recorded as an event, a warning when slow.
            AppLog.event(if (waited > 1500) Level.WARN else Level.INFO, "Player", "playback.first_audio",
                "Started playing ${waited}ms after it was chosen", mapOf("latency_ms" to waited, "song_id" to current?.id))
        }
    }
}

/** The kind of a playback error, for the diagnostic log (issue #237). */
internal fun errorCategory(code: Int): String = when (code) {
    in 2000..2999 -> "network"
    in 3000..3999 -> "format"
    in 4000..4999 -> "decoder"
    in 5000..5999 -> "audio_output"
    in 6000..6999 -> "drm"
    in 1000..1999 -> "player"
    else -> "other"
}
