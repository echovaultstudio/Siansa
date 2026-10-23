package app.musix.mix

import app.musix.data.daysLabel

import app.musix.data.fittingSelector

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import app.musix.AppGraph
import app.musix.data.AppJson
import app.musix.data.LibraryRepository
import app.musix.data.Mix
import app.musix.data.Selector
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.net.ClapHit
import app.musix.net.Steer
import app.musix.situation.Situation
import app.musix.net.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    suspend fun ensureCarousel(carousel: app.musix.data.Carousel, index: Int, force: Boolean = false): Mix = mutex.withLock {
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
        return resolve(hits.map { it.id })
    }

    private suspend fun generate(
        selector: Selector,
        date: String,
        id: String,
        exclude: Set<String> = emptySet(),
        otherArtists: Set<String> = emptySet(),
    ): Mix {
        val settings = graph.settings.current
        if (!settings.navidromeReady) throw MixException("Set up Navidrome in Settings first")
        if (!settings.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")

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

        val pool = LinkedHashMap<String, ClapHit>()
        graph.audioMuse.textSearch(plan.query, selector.length * 5, plan.steering).forEach { pool.putIfAbsent(it.id, it) }
        val base = selector.baseQuery.ifBlank { selector.guidance }
        if (pool.size < selector.length * 2 && base.isNotBlank() && base != plan.query) {
            graph.audioMuse.textSearch(base, selector.length * 4)
                .forEach { pool.putIfAbsent(it.id, it.copy(similarity = it.similarity * 0.9)) }
        }
        // Songs another mix of the same carousel has today stay out (issue #253).
        pool.keys.removeAll(exclude)
        if (pool.isEmpty()) throw MixException("AudioMuse found no tracks for “${plan.query}”")

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

        val songs = resolve(picked.map { it.id })
        AppLog.i("Mix", "Resolved ${songs.size} of ${picked.size}")
        if (songs.isEmpty()) {
            throw MixException("Navidrome didn't recognise AudioMuse's tracks. Check both use the same library.")
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
    enum class Personal(val id: String, val title: String) {
        SOUND("you-sound", "Your sound"),
        REPEAT("you-repeat", "On repeat"),
        DISCOVER("you-discover", "Discover for you"),
    }

    /** Today's personal mix of this kind, built if it doesn't exist yet. */
    suspend fun ensurePersonal(kind: Personal, force: Boolean = false): Mix = mutex.withLock {
        val date = LocalDate.now().toString()
        val id = "$date-${kind.id}"
        if (!force) graph.mixes.get(id)?.let { return@withLock it }
        val settings = graph.settings.current
        if (!settings.navidromeReady) throw MixException("Set up Navidrome in Settings first")
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
        )
        graph.mixes.save(mix)
        mix
    }

    /** AudioMuse's sonic fingerprint: the songs closest to the sound of what you play most. */
    private suspend fun soundMix(skipped: Set<String>): Pair<List<Song>, String> {
        val s = graph.settings.current
        if (!s.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")
        val recent = graph.history.playedSince(System.currentTimeMillis() - 2 * DAY_MS)
        val hits = graph.audioMuse.sonicFingerprint(150, s.navidromeUser, s.navidromePassword)
            .filter { it.id !in skipped && it.id !in recent }
        val picked = capPerArtist(hits.map { it to it.similarity * (0.85 + Random.nextDouble() * 0.15) }, 3, 40)
        return spreadArtists(resolve(picked.map { it.id })) to "The sound of what you play most, found by AudioMuse"
    }

    /** Your most-played songs lately, from Musix, Koito / ListenBrainz and Last.fm. */
    private suspend fun repeatMix(taste: TasteProfile?, skipped: Set<String>): Pair<List<Song>, String> {
        val ids = taste?.favouriteSongs.orEmpty().filter { it !in skipped }
        if (ids.size < 8) return emptyList<Song>() to ""
        val songs = resolve(ids.take(50))
        return songs to "Your most-played songs lately"
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
            seeds = runCatching { graph.audioMuse.sonicFingerprint(20, s.navidromeUser, s.navidromePassword) }
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
        return spreadArtists(resolve(picked.map { it.id })) to "New to you, close to the sound of your favourites"
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
        val songs = spreadArtists(resolve(picked.map { it.id }))
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
        return spreadArtists(resolve(picked.map { it.id }))
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

    /** Songs for AudioMuse ids, from the synced library where possible. */
    suspend fun resolveSongs(ids: List<String>): List<Song> = resolve(ids)

    /**
     * Songs from the synced library where possible, otherwise fetched from Navidrome. AudioMuse knows every
     * library on the server, so when only some libraries are synced, songs outside them are left out.
     */
    private suspend fun resolve(ids: List<String>): List<Song> = coroutineScope {
        val local = graph.library.songs(ids).associateBy { it.id }
        val fetchMissing = mayFetchUnsynced(graph.library.meta(LibraryRepository.META_FOLDERS))
        if (!fetchMissing) {
            val skipped = ids.distinct().count { it !in local }
            if (skipped > 0) AppLog.i("Mix", "Left out $skipped songs that aren't in the libraries you sync")
        }
        val permits = Semaphore(6)
        ids.map { id ->
            async {
                local[id] ?: if (fetchMissing) {
                    permits.withPermit { runCatching { graph.source.song(id) }.getOrNull() }
                } else {
                    null
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.id }
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
