package app.siansa.mix

import app.siansa.data.daysLabel

import app.siansa.data.fittingSelector

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import app.siansa.AppGraph
import app.siansa.data.AppJson
import app.siansa.data.LibraryRepository
import app.siansa.data.Mix
import app.siansa.data.Selector
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.ClapHit
import app.siansa.net.Steer
import app.siansa.situation.Situation
import app.siansa.net.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class MixException(message: String) : Exception(message)

/**
 * Whether a song the synced library doesn't have may be fetched from Navidrome, from the libraries the last
 * sync covered: never synced (null) or every library ("*") yes; only some libraries no, because the song is
 * then most likely in a library you left out.
 */
fun mayFetchUnsynced(syncedFolders: String?): Boolean = syncedFolders == null || syncedFolders == "*"

/**
 * Builds a daily mix for a selector on the phone:
 * 1. optionally asks OpenRouter for a title and a sound description (small prompt, never the library),
 * 2. runs that description through AudioMuse's text-to-audio search,
 * 3. picks a varied set, skipping recent repeats, and resolves the songs in Navidrome.
 * Without AudioMuse, or when its songs aren't in the music shown, the mix comes from the library by genre (beta #25).
 */
class MixGenerator(private val graph: AppGraph) {
    private val mutex = Mutex()

    private data class Plan(
        val title: String,
        val query: String,
        val description: String,
        val ai: Boolean,
        /** Concepts the AI chose to steer the search with (issue #208). */
        val steering: List<Steer> = emptyList(),
    )

    suspend fun currentSelector(): Selector? {
        val now = LocalDateTime.now()
        // The most fitting vibe for the hour and the day (issue #348).
        return fittingSelector(graph.selectors.all(), now.hour, now.dayOfWeek.value)
    }

    /** Today's mix for the selector covering this hour, built if it doesn't exist yet. */
    suspend fun ensureCurrent(force: Boolean = false): Mix? {
        val selector = currentSelector() ?: return null
        return ensure(selector, force)
    }

    suspend fun ensure(selector: Selector, force: Boolean): Mix = mutex.withLock {
        val date = LocalDate.now().toString()
        val id = "$date-${selector.id}"
        if (!force) {
            graph.mixes.get(id)?.let { return@withLock it }
        }
        val mix = generate(selector, date, id)
        graph.mixes.save(mix)
        mix
    }

    /**
     * Today's mix [index] of a home carousel (issue #253), made if it isn't yet. Songs already in the carousel's
     * other mixes today stay out and their artists weigh less, so each mix differs. The mix keeps the name set
     * in Vibes.
     */
    suspend fun ensureCarousel(carousel: app.siansa.data.Carousel, index: Int, force: Boolean = false): Mix = mutex.withLock {
        val selector = carousel.selectorFor(index)
        val date = LocalDate.now().toString()
        val id = "$date-${selector.id}"
        if (!force) graph.mixes.get(id)?.let { return@withLock it }
        val others = carousel.usable.indices.filter { it != index }
            .mapNotNull { graph.mixes.get("$date-${carousel.selectorFor(it).id}") }
        val taken = others.flatMapTo(HashSet()) { m -> m.songs.map { it.id } }
        val artists = others.flatMapTo(HashSet()) { m -> m.songs.map { it.artist.lowercase() } }
        val mix = generate(selector, date, id, exclude = taken, otherArtists = artists).copy(title = selector.name)
        AppLog.i("Mix", "Carousel \"${carousel.title}\" mix ${index + 1} \"${selector.name}\": ${mix.songs.size} songs, ${taken.size} left out for the other mixes")
        graph.mixes.save(mix)
        mix
    }

    /** A one-off sound search, used by the Search screen. */
    suspend fun soundSearch(query: String, limit: Int = 50, steering: List<Steer> = emptyList()): List<Song> {
        val hits = graph.audioMuse.textSearch(query, limit, steering)
        // By title and artist where AudioMuse's ids aren't this server's (an Emby user, beta report 810dc85c).
        return resolveHits(hits)
    }

    private suspend fun generate(
        selector: Selector,
        date: String,
        id: String,
        exclude: Set<String> = emptySet(),
        otherArtists: Set<String> = emptySet(),
    ): Mix {
        val settings = graph.settings.current
        if (!settings.navidromeReady) throw MixException("Set up your server in Settings first")

        // The moment the mix is made in, as far as the Vibes panel allows (issue #247): read once, here.
        val situation = graph.situation.read(settings.situation)
        val plan = if (selector.useAi && settings.aiReady) {
            runCatching { aiPlan(selector, situation) }
                .onFailure { AppLog.w("Mix", "AI plan rejected, using the vibe's own search: ${it.message}") }
                .getOrNull() ?: fallbackPlan(selector, situation)
        } else {
            fallbackPlan(selector, situation)
        }
        AppLog.i("Mix", "${selector.name}: \"${plan.title}\" · search \"${plan.query}\" · steer ${plan.steering.joinToString { (if (it.more) "+" else "-") + it.term }.ifEmpty { "none" }} · ai=${plan.ai}")

        // Without AudioMuse, the mix is picked from the music shown by genre (beta #25).
        if (!settings.audioMuseReady) return fromLibrary(selector, plan, date, id, exclude, otherArtists, emptyList())

        val pool = LinkedHashMap<String, ClapHit>()
        graph.audioMuse.textSearch(plan.query, selector.length * 5, plan.steering).forEach { pool.putIfAbsent(it.id, it) }
        val base = selector.baseQuery.ifBlank { selector.guidance }
        if (pool.size < selector.length * 2 && base.isNotBlank() && base != plan.query) {
            graph.audioMuse.textSearch(base, selector.length * 4)
                .forEach { pool.putIfAbsent(it.id, it.copy(similarity = it.similarity * 0.9)) }
        }
        // Songs another mix of the same carousel has today stay out (issue #253).
        pool.keys.removeAll(exclude)
        if (pool.isEmpty()) {
            AppLog.i("Mix", "${selector.name}: AudioMuse found nothing; picking from the library")
            return fromLibrary(selector, plan, date, id, exclude, otherArtists, emptyList())
        }

        val dayMs = 24L * 60 * 60 * 1000
        val taste = taste()
        val recent = graph.history.playedSince(System.currentTimeMillis() - selector.repeatDays * dayMs) +
            graph.mixes.recentSongIds(selector.id, selector.repeatDays, excludeId = id)
        // Less of what you always hear (issue #236): your most played songs stay out, and part of the mix comes
        // from AudioMuse's alchemy, the vibe's sound blended away from your most played.
        val leaveOut = taste?.favouriteSongs.orEmpty().take(selector.leaveOutTopPlayed.coerceAtLeast(0)).toSet()
        val surprise = if (selector.surprise <= 0) emptyList() else runCatching {
            val anchors = pool.values.filter { it.id !in leaveOut }.take(15).shuffled().take(3).map { it.id }
            if (anchors.isEmpty()) emptyList()
            else graph.audioMuse.alchemy(anchors, avoid = leaveOut.take(5).toList(), count = selector.length * 3)
        }.onFailure { AppLog.w("Mix", "Alchemy for the surprise part failed: ${it.message}") }.getOrDefault(emptyList())
        val picker = MixPicker(
            length = selector.length,
            surprisePercent = selector.surprise,
            leaveOut = leaveOut + exclude,
            recent = recent,
            skipped = taste?.skipped.orEmpty(),
            familiar = { artist -> taste?.affinity(artist) ?: 0.0 },
            lastArtists = graph.mixes.lastArtists(selector.id, excludeId = id) + otherArtists,
        )
        val picked = picker.pick(pool.values.toList(), surprise)
        val fresh = picked.count { it.id !in recent }
        AppLog.i("Mix", "${selector.name}: ${pool.size} matches, ${surprise.size} from alchemy, left out ${leaveOut.size} most played, " +
            "picked ${picked.size} (${picked.count { s -> surprise.any { it.id == s.id } }} surprise, $fresh not heard lately)")

        val songs = resolveHits(picked)
        AppLog.i("Mix", "Resolved ${songs.size} of ${picked.size}")
        // The music shown is mostly not what AudioMuse analysed (only kids' library shown, beta #25): the rest
        // comes from the library, by genre.
        if (songs.size < selector.length / 2) {
            AppLog.i("Mix", "${selector.name}: only ${songs.size} of AudioMuse's songs are in the music shown; the rest from the library")
            return fromLibrary(selector, plan, date, id, exclude, otherArtists, songs)
        }
        return Mix(
            id = id,
            selectorId = selector.id,
            date = date,
            title = plan.title,
            description = plan.description,
            query = plan.query,
            songs = spreadArtists(songs),
            createdAt = System.currentTimeMillis(),
            aiNamed = plan.ai,
            scope = graph.mixScope(),
        )
    }

    /**
     * A mix picked from the music shown, by genre ([LibraryMix]): without AudioMuse, or to fill one whose songs are
     * mostly not shown. [first] (AudioMuse's songs that are shown) stay at the start.
     */
    private suspend fun fromLibrary(
        selector: Selector,
        plan: Plan,
        date: String,
        id: String,
        exclude: Set<String>,
        otherArtists: Set<String>,
        first: List<Song>,
    ): Mix {
        val dayMs = 24L * 60 * 60 * 1000
        val taste = taste()
        val recent = graph.history.playedSince(System.currentTimeMillis() - selector.repeatDays * dayMs) +
            graph.mixes.recentSongIds(selector.id, selector.repeatDays, excludeId = id)
        val leaveOut = taste?.favouriteSongs.orEmpty().take(selector.leaveOutTopPlayed.coerceAtLeast(0)).toSet()
        val candidates = graph.library.randomSongs(LIBRARY_CANDIDATES)
        val text = listOf(plan.query, plan.title, selector.baseQuery, selector.guidance, selector.name).joinToString(" ")
        val more = LibraryMix.pick(
            candidates,
            vibeText = text,
            hourGenres = taste?.hourGenres.orEmpty(),
            length = selector.length - first.size,
            avoid = recent + leaveOut + taste?.skipped.orEmpty(),
            exclude = exclude + first.map { it.id },
            otherArtists = otherArtists + graph.mixes.lastArtists(selector.id, excludeId = id),
        )
        val vibe = LibraryMix.words(text)
        AppLog.i(
            "Mix",
            "${selector.name}: ${more.size} songs from the library (${more.count { LibraryMix.fit(it, vibe, emptySet()) == 2 }} " +
                "by the vibe's genres) of ${candidates.size} looked at, after ${first.size} from AudioMuse",
        )
        val songs = first + more
        if (songs.isEmpty()) throw MixException("There's no music to make a mix from. Check the source filter on Home.")
        return Mix(
            id = id,
            selectorId = selector.id,
            date = date,
            title = plan.title,
            description = plan.description,
            query = plan.query,
            songs = spreadArtists(songs),
            createdAt = System.currentTimeMillis(),
            aiNamed = plan.ai,
            scope = graph.mixScope(),
        )
    }

    /**
     * The vibe's own search, without AI. With something read from the moment, its concepts nudge the search
     * (rain towards acoustic and calm, running towards energetic) and the description says why (issue #247).
     */
    private suspend fun fallbackPlan(selector: Selector, situation: Situation = Situation()): Plan {
        val nudges = if (situation.isEmpty) emptyList() else situation.nudges(runCatching { graph.audioMuse.concepts() }.getOrDefault(emptyList()))
        val label = situation.label()
        return Plan(
            title = "${selector.name} mix",
            query = selector.baseQuery.ifBlank { selector.guidance },
            description = if (label.isEmpty()) selector.guidance else "${selector.guidance} · $label",
            ai = false,
            steering = nudges,
        )
    }

    private suspend fun aiPlan(selector: Selector, situation: Situation = Situation()): Plan {
        val now = LocalDateTime.now()
        val concepts = runCatching { graph.audioMuse.concepts() }.getOrDefault(emptyList()).take(80)
        val recent = graph.history.recent(20).map { play ->
            "${play.artist} – ${play.title}" + (play.genre?.let { " ($it)" } ?: "")
        }
        val previousTitles = graph.mixes.recentTitles(8)
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

        val user = buildString {
            appendLine("Time slot: ${selector.name} (${selector.hoursLabel})")
            // The day, so titles fit it (issue #348); a vibe set for certain days says so.
            if (selector.days.isNotEmpty()) appendLine("This vibe is for: ${daysLabel(selector.days)}. Make the title fit the day.")
            appendLine("Guidance: ${selector.guidance}")
            if (selector.avoid.isNotEmpty()) appendLine("Forbidden words: ${selector.avoid.joinToString(", ")}")
            appendLine("Now: $day %02d:%02d".format(now.hour, now.minute))
            if (graph.settings.current.situation.device) appendLine("Listening on: ${describeOutput(graph.app)}")
            // Weather, movement, light, charging: what the Vibes panel allows (issue #247). Never a location.
            if (!situation.isEmpty) appendLine("Right now: ${situation.describe()}")
            if (recent.isNotEmpty()) appendLine("Recently played: ${recent.joinToString("; ")}")
            // Genres only, no favourite artists: the mix should surprise, not repeat them (issue #236).
            taste()?.let { t ->
                if (t.hourGenres.isNotEmpty()) appendLine("Genres usually played around this time: ${t.hourGenres.joinToString(", ")}")
            }
            if (previousTitles.isNotEmpty()) appendLine("Previous mix titles: ${previousTitles.joinToString("; ")}")
            if (concepts.isNotEmpty()) appendLine("Sound concepts the search understands well: ${concepts.joinToString(", ")}")
        }

        var obj = askPlan(user)
        // A title that says little or echoes an earlier one gets one more try, with the reason (issue #236).
        MixTitles.problem(obj.str("title").orEmpty(), previousTitles)?.let { why ->
            AppLog.i("Mix", "AI title \"${obj.str("title")}\" rejected: $why; asking once more")
            runCatching { askPlan(user + "\nYour title \"${obj.str("title")}\" was rejected: $why. Give a different, more specific title.") }
                .onSuccess { obj = it }
        }
        val title = obj.str("title")?.trim()?.trim('"')?.take(60).orEmpty()
        val query = obj.str("query")?.trim()?.take(300).orEmpty()
        val description = obj.str("description")?.trim()?.take(200).orEmpty()
        if (title.isEmpty() || query.length < 3) throw MixException("The AI's answer was incomplete")

        val text = "$title $query".lowercase()
        val broken = selector.avoid.map { it.trim().lowercase() }.filter { it.isNotEmpty() && text.contains(it) }
        if (broken.isNotEmpty()) throw MixException("The AI used a forbidden word: ${broken.first()}")

        return Plan(title, query, description.ifBlank { selector.guidance }, ai = true, steering = steeringOf(obj, concepts))
    }

    private suspend fun askPlan(user: String): kotlinx.serialization.json.JsonObject {
        val raw = graph.openRouter.complete(SYSTEM_PROMPT, user)
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw MixException("The AI didn't answer with JSON")
        return AppJson.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
    }

    private fun steeringOf(obj: kotlinx.serialization.json.JsonObject, concepts: List<String>): List<Steer> =
        parseSteering(obj["steer"], concepts)

    private suspend fun taste(): TasteProfile? =
        runCatching { graph.taste.profile() }.onFailure { AppLog.w("Taste", "Couldn't build the taste profile: ${it.message}") }.getOrNull()

    /** Mixes made from your listening rather than a time of day. */
    enum class Personal(val id: String, val title: String, val needsAudioMuse: Boolean) {
        SOUND("you-sound", "Your sound", true),
        REPEAT("you-repeat", "On repeat", false),
        DISCOVER("you-discover", "Discover for you", true),
    }

    /** Today's personal mix of this kind, built if it doesn't exist yet. */
    suspend fun ensurePersonal(kind: Personal, force: Boolean = false): Mix = mutex.withLock {
        val date = LocalDate.now().toString()
        val id = "$date-${kind.id}"
        if (!force) graph.mixes.get(id)?.let { return@withLock it }
        val settings = graph.settings.current
        if (!settings.navidromeReady) throw MixException("Set up your server in Settings first")
        val taste = taste()
        val skipped = taste?.skipped.orEmpty()
        val (songs, description) = when (kind) {
            Personal.SOUND -> soundMix(skipped)
            Personal.REPEAT -> repeatMix(taste, skipped)
            Personal.DISCOVER -> discoverMix(taste, skipped)
        }
        if (songs.isEmpty()) throw MixException("Not enough listening history yet. Play some more and try again later.")
        AppLog.i("Mix", "${kind.title}: ${songs.size} songs")
        val mix = Mix(
            id = id,
            selectorId = kind.id,
            date = date,
            title = kind.title,
            description = description,
            query = "",
            songs = songs,
            createdAt = System.currentTimeMillis(),
            aiNamed = false,
            scope = graph.mixScope(),
        )
        graph.mixes.save(mix)
        mix
    }

    /** AudioMuse's sonic fingerprint: the songs closest to the sound of what you play most. */
    private suspend fun soundMix(skipped: Set<String>): Pair<List<Song>, String> {
        val s = graph.settings.current
        if (!s.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        val recent = graph.history.playedSince(System.currentTimeMillis() - 2 * DAY_MS)
        val hits = graph.audioMuse.sonicFingerprint(150, s.fingerprintUser, s.fingerprintPassword)
            .filter { it.id !in skipped && it.id !in recent }
        val picked = capPerArtist(hits.map { it to it.similarity * (0.85 + Random.nextDouble() * 0.15) }, 3, 40)
        return spreadArtists(resolveHits(picked)) to "The sound of what you play most, found by AudioMuse"
    }

    /**
     * Your most-played songs of the last 30 days (issue #495): from the server (the songs of the albums played
     * lately, with their play counts), this phone, and Koito / ListenBrainz / Last.fm.
     */
    private suspend fun repeatMix(taste: TasteProfile?, skipped: Set<String>): Pair<List<Song>, String> {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val server = runCatching { graph.source.recentSongPlays(RECENT_ALBUMS) }
            .onFailure { AppLog.w("Mix", "On repeat: the server's play counts failed: ${it.message}") }
            .getOrDefault(emptyList())
        runCatching { graph.playCounts.record(server, today) }
        val before = graph.playCounts.countsOn(today - OnRepeat.WINDOW_DAYS)
        val fromServer = server.mapNotNull { p -> OnRepeat.serverScore(p, before[p.song.id], now)?.let { p.song.id to it } }.toMap()
        val fromPhone = graph.history.all()
            .filter { !it.skipped && now - it.at < OnRepeat.WINDOW_DAYS * DAY_MS }
            .groupingBy { it.id }.eachCount()
        val ids = OnRepeat.rank(fromServer, fromPhone, taste?.favouriteSongs.orEmpty(), skipped)
        AppLog.i(
            "Mix",
            "On repeat: ${fromServer.size} songs from the server (${server.count { before.containsKey(it.song.id) }} with a count from 30 days ago), " +
                "${fromPhone.size} from this phone, ${taste?.favouriteSongs?.size ?: 0} from history services",
        )
        if (ids.size < OnRepeat.MIN_SONGS) return emptyList<Song>() to ""
        val songs = resolve(ids)
        return songs to "Your most-played songs of the last 30 days"
    }

    /**
     * New to you: songs that sound like your favourites, leaning away from what you already play a lot.
     * Mood and duplicates follow AudioMuse's (or the Navidrome plugin's) own settings (issue #208).
     */
    private suspend fun discoverMix(taste: TasteProfile?, skipped: Set<String>): Pair<List<Song>, String> {
        val s = graph.settings.current
        if (!s.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        val heard = graph.history.all().map { it.id }.toSet()
        var seeds = taste?.favouriteSongs.orEmpty().shuffled().take(8)
        if (seeds.size < 3) {
            seeds = runCatching { graph.audioMuse.sonicFingerprint(20, s.fingerprintUser, s.fingerprintPassword) }
                .getOrDefault(emptyList()).shuffled().take(8).map { it.id }
        }
        if (seeds.isEmpty()) return emptyList<Song>() to ""
        val pool = LinkedHashMap<String, Pair<ClapHit, Double>>()
        coroutineScope {
            seeds.map { seed -> async { runCatching { graph.sonic.similar(seed, 30) }.getOrDefault(emptyList()) } }
                .awaitAll()
        }.flatten().forEach { hit ->
            if (hit.id in heard || hit.id in skipped || hit.id in seeds) return@forEach
            val familiar = taste?.affinity(hit.artist) ?: 0.0
            val score = hit.similarity * (1 - 0.6 * familiar) * (0.8 + Random.nextDouble() * 0.2)
            if ((pool[hit.id]?.second ?: -1.0) < score) pool[hit.id] = hit to score
        }
        val picked = capPerArtist(pool.values.toList(), 2, 40)
        return spreadArtists(resolveHits(picked)) to "New to you, close to the sound of your favourites"
    }

    private fun capPerArtist(scored: List<Pair<ClapHit, Double>>, perArtist: Int, size: Int): List<ClapHit> {
        val counts = HashMap<String, Int>()
        return scored.sortedByDescending { it.second }.map { it.first }.filter { hit ->
            val key = hit.artist.lowercase()
            val n = counts[key] ?: 0
            if (n >= perArtist) false else { counts[key] = n + 1; true }
        }.take(size)
    }

    /** A playlist made on the spot from a description of how the listener feels. */
    data class MoodResult(val title: String, val description: String, val query: String, val songs: List<Song>, val ai: Boolean)

    suspend fun moodPlaylist(mood: String, size: Int = 50): MoodResult {
        val text = mood.trim()
        if (text.length < 3) throw MixException("Describe your mood in a few words")
        if (!graph.settings.current.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        var plan = Plan(text.replaceFirstChar { it.uppercase() }, text, "From your description", ai = false)
        if (graph.settings.current.aiReady) {
            runCatching { aiMoodPlan(text) }
                .onSuccess { plan = it }
                .onFailure { AppLog.w("Mood", "AI couldn't interpret the mood, searching the words directly: ${it.message}") }
        }
        AppLog.i("Mood", "\"$text\" → \"${plan.title}\" · search \"${plan.query}\" · ai=${plan.ai}")
        val hits = graph.audioMuse.textSearch(plan.query, size * 3, plan.steering)
        if (hits.isEmpty()) throw MixException("AudioMuse found nothing for that mood")
        val ranked = hits
            .map { it to it.similarity * (0.85 + Random.nextDouble() * 0.15) }
            .sortedByDescending { it.second }
            .map { it.first }
        val perArtist = HashMap<String, Int>()
        val picked = ranked.filter { hit ->
            val n = perArtist[hit.artist.lowercase()] ?: 0
            if (n >= 3) false else { perArtist[hit.artist.lowercase()] = n + 1; true }
        }.take(size)
        val songs = spreadArtists(resolveHits(picked))
        return MoodResult(plan.title, plan.description, plan.query, songs, plan.ai)
    }

    /** What a vibe on Dscvr searches for: from the AI, or the words themselves (issue #339). */
    data class VibePlan(val title: String, val description: String, val query: String, val steering: List<Steer>, val ai: Boolean)

    /**
     * The plan for a vibe described in words ([text]): the AI's title, sound search and concepts when an AI is
     * set up, otherwise the words searched as they are. With no words, the sliders make the search ([tuning]).
     */
    suspend fun vibePlan(text: String, tuning: VibeTuning): VibePlan {
        if (!graph.settings.current.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        val words = text.trim()
        if (words.isEmpty()) {
            val query = sliderQuery(tuning) ?: throw MixException("Describe your aura, or move a slider")
            val summary = tuning.summary().ifEmpty { "Your vibe" }
            return VibePlan(summary.replaceFirstChar { it.uppercase() }, "Made from your sliders", query, emptyList(), ai = false)
        }
        if (words.length < 3) throw MixException("Describe your aura in a few words")
        var plan = Plan(words.replaceFirstChar { it.uppercase() }, words, "From your description", ai = false)
        if (graph.settings.current.aiReady) {
            runCatching { aiMoodPlan(words) }
                .onSuccess { plan = it }
                .onFailure { AppLog.w("Vibe", "AI couldn't interpret the vibe, searching the words directly: ${it.message}") }
        }
        AppLog.i("Vibe", "\"$words\" → \"${plan.title}\" · search \"${plan.query}\" · ai=${plan.ai}")
        return VibePlan(plan.title, plan.description, plan.query, plan.steering, plan.ai)
    }

    /** Sound concepts AudioMuse's search understands, for the sound dials; empty when it can't say. */
    suspend fun soundConcepts(): List<String> = runCatching { graph.audioMuse.concepts() }
        .onFailure { AppLog.w("Vibe", "Couldn't load sound concepts: ${it.message}") }
        .getOrDefault(emptyList())

    /**
     * The songs for a vibe: AudioMuse's sound search for [query] steered by the sound dials (or the AI's
     * concepts while no dial is set), re-ranked by the energy, tempo and familiar sliders (issue #339).
     */
    suspend fun vibeSongs(query: String, aiSteering: List<Steer>, tuning: VibeTuning, size: Int = 50): List<Song> {
        val dials = tuning.steering()
        // The dials start at the AI's concepts, so once any is shown they say everything; before that, the AI's.
        val steering = if (tuning.sounds.isNotEmpty()) dials else aiSteering
        val hits = graph.audioMuse.textSearch(query, size * 3, steering)
        if (hits.isEmpty()) throw MixException("AudioMuse found nothing for that vibe")
        val features = if (tuning.energy != 0 || tuning.tempo != 0) {
            runCatching { graph.audioMuse.features(hits.map { it.id }).associateBy { it.id } }
                .onFailure { AppLog.w("Vibe", "Couldn't load energy and tempo, leaving them out: ${it.message}") }
                .getOrDefault(emptyMap())
        } else emptyMap()
        val familiarity = if (tuning.familiar != 0) {
            graph.history.all().groupingBy { it.artist.lowercase() }.eachCount()
        } else emptyMap()
        val ranked = rankVibe(hits, features, familiarity, tuning) { Random.nextDouble() * 0.05 }
        val perArtist = HashMap<String, Int>()
        val picked = ranked.filter { hit ->
            val n = perArtist[hit.artist.lowercase()] ?: 0
            if (n >= 3) false else { perArtist[hit.artist.lowercase()] = n + 1; true }
        }.take(size)
        AppLog.i(
            "Vibe",
            "Search \"$query\" · ${tuning.summary().ifEmpty { "no sliders moved" }} · ${hits.size} found, " +
                "${features.size} with energy/tempo, ${picked.size} picked",
        )
        return spreadArtists(resolveHits(picked))
    }

    private suspend fun aiMoodPlan(mood: String): Plan {
        val now = LocalDateTime.now()
        val concepts = runCatching { graph.audioMuse.concepts() }.getOrDefault(emptyList()).take(80)
        val recent = graph.history.recent(15).map { "${it.artist} – ${it.title}" }
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        // The same moment the daily vibes use (issue #339): weather, movement, light and charging, as set there.
        val options = graph.settings.current.situation
        val situation = runCatching { graph.situation.read(options) }
            .onFailure { AppLog.w("Mood", "Couldn't read the moment: ${it.message}") }
            .getOrDefault(Situation())
        val user = buildString {
            appendLine("The listener describes their mood or wish: \"$mood\"")
            appendLine("Make a playlist that fits it. Title it from their words.")
            appendLine("Now: $day %02d:%02d".format(now.hour, now.minute))
            if (options.device) appendLine("Listening on: ${describeOutput(graph.app)}")
            if (!situation.isEmpty) appendLine("Right now: ${situation.describe()}")
            if (recent.isNotEmpty()) appendLine("Recently played: ${recent.joinToString("; ")}")
            if (concepts.isNotEmpty()) appendLine("Sound concepts the search understands well: ${concepts.joinToString(", ")}")
        }
        val raw = graph.openRouter.complete(SYSTEM_PROMPT, user)
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw MixException("The AI didn't answer with JSON")
        val obj = AppJson.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
        val title = obj.str("title")?.trim()?.trim('"')?.take(60).orEmpty()
        val query = obj.str("query")?.trim()?.take(300).orEmpty()
        val description = obj.str("description")?.trim()?.take(200).orEmpty()
        if (title.isEmpty() || query.length < 3) throw MixException("The AI's answer was incomplete")
        return Plan(title, query, description.ifBlank { "Made from your mood" }, ai = true, steering = steeringOf(obj, concepts))
    }

    /** How many of AudioMuse's [hits] are in the synced library, by id or by title and artist (beta testers #32). */
    suspend fun libraryMatch(hits: List<ClapHit>): LibraryMatch {
        val index = songIndex()
        val byId = graph.library.songs(hits.map { it.id }).map { it.id }.toSet()
        val byName = matchByName(hits.filter { it.id !in byId }, index)
        return LibraryMatch(hits.size, hits.count { it.id in byId }, byName.size, index.size)
    }

    /** Songs for AudioMuse ids, from the synced library where possible. */
    suspend fun resolveSongs(ids: List<String>): List<Song> = resolve(ids)

    private var cachedIndex: Pair<Int, SongIndex>? = null

    private suspend fun songIndex(): SongIndex {
        val version = graph.library.version.value
        cachedIndex?.takeIf { it.first == version }?.let { return it.second }
        val index = SongIndex(graph.library.songTitles().map { SongIndex.Entry(it.id, it.title, it.artist) })
        cachedIndex = version to index
        return index
    }

    /**
     * AudioMuse's tracks as songs of the connected server (issue #416). Its ids only fit the server it analysed;
     * when the app is on another one (AudioMuse on Navidrome, the app on Emby), a track is matched by its
     * title and artist in the synced library instead. Ids that match are used as they are.
     */
    suspend fun resolveHits(hits: List<ClapHit>): List<Song> {
        val local = graph.library.songs(hits.map { it.id }).associateBy { it.id }
        val missing = hits.filter { it.id !in local }
        val byName = HashMap<String, String>()
        if (missing.isNotEmpty()) {
            val index = songIndex()
            byName += matchByName(missing, index)
            if (byName.isNotEmpty()) AppLog.i("Mix", "Matched ${byName.size} of ${missing.size} tracks by title and artist")
        }
        val matched = graph.library.songs(byName.values.toList()).associateBy { it.id }
        val rest = missing.filter { it.id !in byName }.map { it.id }
        val fetched = if (rest.isEmpty()) emptyMap() else resolve(rest).associateBy { it.id }
        return hits.mapNotNull { hit -> local[hit.id] ?: byName[hit.id]?.let { matched[it] } ?: fetched[hit.id] }.distinctBy { it.id }
    }

    /**
     * Songs from the synced library where possible, otherwise fetched from Navidrome. AudioMuse knows every
     * library on the server, so when only some libraries are synced, songs outside them are left out.
     */
    private suspend fun resolve(ids: List<String>): List<Song> {
        val local = graph.library.songs(ids).associateBy { it.id }
        val fetchMissing = mayFetchUnsynced(graph.library.meta(LibraryRepository.META_FOLDERS))
        if (!fetchMissing) {
            val skipped = ids.distinct().count { it !in local }
            if (skipped > 0) AppLog.i("Mix", "Left out $skipped songs that aren't in the libraries you sync")
        }
        val missing = ids.filter { it !in local }
        val fetched = if (fetchMissing && missing.isNotEmpty()) {
            val result = fetchUnlessForeign(missing) { id -> runCatching { graph.source.song(id) }.getOrNull() }
            if (result.foreign) {
                AppLog.w(
                    "Mix",
                    "The server knows none of AudioMuse's songs (${missing.size} asked about): AudioMuse may have " +
                        "analysed another server; not asking for the rest",
                )
            }
            result.songs.associateBy { it.id }
        } else emptyMap()
        return ids.mapNotNull { local[it] ?: fetched[it] }.distinctBy { it.id }
    }

    /** Keeps the same artist from playing twice in a row where possible. */
    private fun spreadArtists(songs: List<Song>): List<Song> {
        val left = songs.toMutableList()
        val out = ArrayList<Song>(songs.size)
        while (left.isNotEmpty()) {
            val last = out.lastOrNull()?.artist
            val index = left.indexOfFirst { it.artist != last }.takeIf { it >= 0 } ?: 0
            out += left.removeAt(index)
        }
        return out
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        /** Albums played lately whose songs On repeat looks at (issue #495). */
        const val RECENT_ALBUMS = 40
        /** Songs of the music shown looked at for a mix from the library. */
        const val LIBRARY_CANDIDATES = 3000
        val SYSTEM_PROMPT = """
            You plan one playlist for a personal music player. A text-to-audio search model (CLAP) will find tracks
            in the listener's own library from your search text, so describe how the music should SOUND.
            Reply with only a JSON object: {"title": string, "query": string, "description": string, "steer": [...]}.
            - title: 2 to 5 words, specific and a little unexpected: a concrete image, place or moment, like
              "Neon puddles after midnight" or "Coffee before the storm". Never generic words such as unwind, chill,
              vibes, beats, mellow, relax, groove, journey or playlist. No emoji.
            - query: 6 to 16 words for the audio search: genres, mood, energy, tempo, instruments, vocals.
              No artist or song names. Prefer the listed sound concepts where they fit.
            - description: one short friendly sentence for the listener.
            - steer: optional, up to 3 of the listed sound concepts to push the search towards or away from, as
              {"term": concept, "direction": "more" or "less"}. Only concepts from the list; leave it empty if none fit.
            Follow the time slot's guidance strictly and never use any forbidden word in the title or query.
            Use the listening device as context (a car's Bluetooth suggests driving), and "Right now" when given:
            the weather, whether the listener is moving, the light and the phone's state should shape the sound
            (rain and dark rooms softer, sun and running brighter) and may inspire the title.
            Fit the time slot, but surprise the listener: don't just describe what they played recently, take the
            sound somewhere a little new each day. Never repeat or echo a previous mix title.
        """.trimIndent()

        fun describeOutput(context: Context): String {
            val audio = context.getSystemService(AudioManager::class.java) ?: return "unknown"
            val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }?.let {
                return "Bluetooth audio device \"${it.productName}\""
            }
            val wired = setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )
            if (outputs.any { it.type in wired }) return "wired headphones"
            return "phone speaker"
        }
    }
}

/**
 * The AI's "steer" list as concepts to steer the search with: only terms AudioMuse listed (so the search never
 * refuses one), at most 3, each once (issue #208).
 */
fun parseSteering(element: kotlinx.serialization.json.JsonElement?, concepts: List<String>): List<Steer> {
    val known = concepts.associateBy { it.lowercase() }
    val list = element as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return list.mapNotNull { e ->
        val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val term = o.str("term")?.trim()?.lowercase()?.let { known[it] } ?: return@mapNotNull null
        Steer(term, o.str("direction")?.lowercase() != "less")
    }.distinctBy { it.term }.take(3)
}

/** AudioMuse's tracks by their id → the id of the song with the same title and artist in the library (issue #416). */
internal fun matchByName(hits: List<ClapHit>, index: SongIndex): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (hit in hits) index.find(hit.artist, hit.title)?.let { out[hit.id] = it }
    return out
}
