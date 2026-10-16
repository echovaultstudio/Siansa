package app.musix.mix

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import app.musix.AppGraph
import app.musix.data.AppJson
import app.musix.data.Mix
import app.musix.data.Selector
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.net.ClapHit
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
 * Builds a daily mix for a selector on the phone:
 * 1. optionally asks OpenRouter for a title and a sound description (small prompt, never the library),
 * 2. runs that description through AudioMuse's text-to-audio search,
 * 3. picks a varied set, skipping recent repeats, and resolves the songs in Navidrome.
 */
class MixGenerator(private val graph: AppGraph) {
    private val mutex = Mutex()

    private data class Plan(val title: String, val query: String, val description: String, val ai: Boolean)

    suspend fun currentSelector(): Selector? {
        val hour = LocalDateTime.now().hour
        return graph.selectors.all().firstOrNull { it.enabled && it.covers(hour) }
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

    /** A one-off sound search, used by the Search screen. */
    suspend fun soundSearch(query: String, limit: Int = 50): List<Song> {
        val hits = graph.audioMuse.textSearch(query, limit)
        return resolve(hits.map { it.id })
    }

    private suspend fun generate(selector: Selector, date: String, id: String): Mix {
        val settings = graph.settings.current
        if (!settings.navidromeReady) throw MixException("Set up Navidrome in Settings first")
        if (!settings.audioMuseReady) throw MixException("Set up AudioMuse in Settings first")

        val plan = if (selector.useAi && settings.aiReady) {
            runCatching { aiPlan(selector) }
                .onFailure { AppLog.w("Mix", "AI plan rejected, using the vibe's own search: ${it.message}") }
                .getOrNull() ?: fallbackPlan(selector)
        } else {
            fallbackPlan(selector)
        }
        AppLog.i("Mix", "${selector.name}: \"${plan.title}\" · search \"${plan.query}\" · ai=${plan.ai}")

        val pool = LinkedHashMap<String, ClapHit>()
        graph.audioMuse.textSearch(plan.query, selector.length * 5).forEach { pool.putIfAbsent(it.id, it) }
        val base = selector.baseQuery.ifBlank { selector.guidance }
        if (pool.size < selector.length * 2 && base.isNotBlank() && base != plan.query) {
            graph.audioMuse.textSearch(base, selector.length * 4)
                .forEach { pool.putIfAbsent(it.id, it.copy(similarity = it.similarity * 0.9)) }
        }
        if (pool.isEmpty()) throw MixException("AudioMuse found no tracks for “${plan.query}”")

        val dayMs = 24L * 60 * 60 * 1000
        val taste = taste()
        val recent = graph.history.playedSince(System.currentTimeMillis() - selector.repeatDays * dayMs) +
            graph.mixes.recentSongIds(selector.id, selector.repeatDays, excludeId = id)
        val skipped = taste?.skipped.orEmpty()
        val fresh = pool.values.filter { it.id !in recent && it.id !in skipped }
        val candidates = if (fresh.size >= selector.length) fresh else pool.values.filter { it.id !in skipped }.ifEmpty { pool.values.toList() }

        // Mostly by match strength, nudged towards artists you play (and play at this time of day),
        // with a little shuffle so two days never come out identical.
        // Each track gets one score up front: a sort whose keys change mid-sort fails on Android.
        val hourArtists = taste?.hourArtists.orEmpty().map { it.lowercase() }.toSet()
        val ranked = candidates
            .map { hit ->
                val liked = taste?.affinity(hit.artist) ?: 0.0
                val habit = if (hit.artist.lowercase() in hourArtists) 0.12 else 0.0
                hit to hit.similarity * (0.8 + Random.nextDouble() * 0.2) * (1 + 0.3 * liked + habit)
            }
            .sortedByDescending { it.second }
            .map { it.first }
        val maxPerArtist = max(2, selector.length / 8)
        val perArtist = HashMap<String, Int>()
        val picked = ArrayList<ClapHit>()
        for (hit in ranked) {
            if (picked.size >= selector.length) break
            val key = hit.artist.lowercase()
            val count = perArtist[key] ?: 0
            if (count >= maxPerArtist) continue
            perArtist[key] = count + 1
            picked += hit
        }
        if (picked.size < selector.length) {
            for (hit in ranked) {
                if (picked.size >= selector.length) break
                if (hit !in picked) picked += hit
            }
        }

        val songs = resolve(picked.map { it.id })
        AppLog.i("Mix", "${pool.size} matches, ${fresh.size} not played recently, picked ${picked.size}, resolved ${songs.size}")
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

    private fun fallbackPlan(selector: Selector) = Plan(
        title = "${selector.name} mix",
        query = selector.baseQuery.ifBlank { selector.guidance },
        description = selector.guidance,
        ai = false,
    )

    private suspend fun aiPlan(selector: Selector): Plan {
        val now = LocalDateTime.now()
        val concepts = runCatching { graph.audioMuse.concepts() }.getOrDefault(emptyList()).take(80)
        val recent = graph.history.recent(20).map { play ->
            "${play.artist} – ${play.title}" + (play.genre?.let { " ($it)" } ?: "")
        }
        val previousTitles = graph.mixes.recentTitles(8)
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

        val user = buildString {
            appendLine("Time slot: ${selector.name} (${selector.hoursLabel})")
            appendLine("Guidance: ${selector.guidance}")
            if (selector.avoid.isNotEmpty()) appendLine("Forbidden words: ${selector.avoid.joinToString(", ")}")
            appendLine("Now: $day %02d:%02d".format(now.hour, now.minute))
            appendLine("Listening on: ${describeOutput(graph.app)}")
            if (recent.isNotEmpty()) appendLine("Recently played: ${recent.joinToString("; ")}")
            taste()?.let { t ->
                if (t.topArtists.isNotEmpty()) appendLine("Favourite artists lately: ${t.topArtists.take(10).joinToString(", ")}")
                if (t.hourArtists.isNotEmpty() || t.hourGenres.isNotEmpty()) {
                    appendLine("Usually played around this time: " + (t.hourGenres + t.hourArtists.take(5)).joinToString(", "))
                }
            }
            if (previousTitles.isNotEmpty()) appendLine("Previous mix titles: ${previousTitles.joinToString("; ")}")
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

        val text = "$title $query".lowercase()
        val broken = selector.avoid.map { it.trim().lowercase() }.filter { it.isNotEmpty() && text.contains(it) }
        if (broken.isNotEmpty()) throw MixException("The AI used a forbidden word: ${broken.first()}")

        return Plan(title, query, description.ifBlank { selector.guidance }, ai = true)
    }

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
     * New to you: songs that sound like your favourites (keeping their mood), leaning away from
     * what you already play a lot.
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
            seeds.map { seed -> async { runCatching { graph.audioMuse.similarTracks(seed, 30, sameMood = true) }.getOrDefault(emptyList()) } }
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
        val hits = graph.audioMuse.textSearch(plan.query, size * 3)
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

    private suspend fun aiMoodPlan(mood: String): Plan {
        val now = LocalDateTime.now()
        val concepts = runCatching { graph.audioMuse.concepts() }.getOrDefault(emptyList()).take(80)
        val recent = graph.history.recent(15).map { "${it.artist} – ${it.title}" }
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val user = buildString {
            appendLine("The listener describes their mood or wish: \"$mood\"")
            appendLine("Make a playlist that fits it. Title it from their words.")
            appendLine("Now: $day %02d:%02d".format(now.hour, now.minute))
            appendLine("Listening on: ${describeOutput(graph.app)}")
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
        return Plan(title, query, description.ifBlank { "Made from your mood" }, ai = true)
    }

    /** Songs for AudioMuse ids, from the synced library where possible. */
    suspend fun resolveSongs(ids: List<String>): List<Song> = resolve(ids)

    /** Songs from the synced library where possible, otherwise fetched from Navidrome. */
    private suspend fun resolve(ids: List<String>): List<Song> = coroutineScope {
        val local = graph.library.songs(ids).associateBy { it.id }
        val permits = Semaphore(6)
        ids.map { id ->
            async {
                local[id] ?: permits.withPermit { runCatching { graph.subsonic.song(id) }.getOrNull() }
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
            Reply with only a JSON object: {"title": string, "query": string, "description": string}.
            - title: 2 to 6 words, natural and specific, like "Mellow electronic for the drive". No emoji.
            - query: 6 to 16 words for the audio search: genres, mood, energy, tempo, instruments, vocals.
              No artist or song names. Prefer the listed sound concepts where they fit.
            - description: one short friendly sentence for the listener.
            Follow the time slot's guidance strictly and never use any forbidden word in the title or query.
            Use the listening device as context (a car's Bluetooth suggests driving).
            Stay close to the listener's recent taste and what they usually play at this time, but don't repeat a
            previous mix title. Favourite artists are only a hint for the sound: never name them.
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
