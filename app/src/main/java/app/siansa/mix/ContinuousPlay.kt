package app.siansa.mix

import app.siansa.net.soundReady
import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * What happens when the queue is about to run out (issue #337), set in Settings › Playback › Keep playing.
 */
enum class ContinuousMode(val label: String, val hint: String) {
    OFF("Off", "The music stops when the queue ends."),
    STEADY("Steady", "Adds songs like the last one in the queue, so the music carries on in the same style."),
    DJ("DJ", "Adds songs like the last one and orders them for smooth changes: close tempo and keys that go together. Needs Siansa Beats.");

    companion object {
        fun parse(name: String?): ContinuousMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * Whether the queue should be topped up now: keep playing is on, only [left] songs or fewer come after the one
 * playing, and nothing else decides what plays next. A song radio tops itself up, repeat plays the queue again,
 * and the sleep timer's wind down (or its last song) is meant to end the music.
 */
fun continuousTopUpDue(
    mode: ContinuousMode,
    left: Int,
    hasMedia: Boolean,
    repeatOff: Boolean,
    radioOn: Boolean,
    windingDown: Boolean,
    sleepFinishing: Boolean,
): Boolean = mode != ContinuousMode.OFF && hasMedia && left <= ContinuousPlay.WHEN_LEFT && repeatOff &&
    !radioOn && !windingDown && !sleepFinishing

/**
 * The shape of a DJ mix (issue #649): what the energy does over the next songs. Shown in the queue while a DJ
 * radio plays; changing it reorders the songs not yet played.
 */
enum class MixShape(val label: String, val hint: String) {
    STEADY("Steady vibes", "Stays where the mix started: the same tempo and energy, song after song."),
    BUILD("Steady build", "The energy climbs song by song, from where it is now to the top in about eight songs."),
    EUPHORIC("Euphoric", "Climbs to a high in a few songs and stays there, the brightest songs first."),
    WIND_DOWN("Wind down", "The energy falls song by song, to the calmest in about eight songs.");

    /**
     * The energy (0..1) the song picked [index]th (0 the next) should have, from [from] (the energy where the mix is
     * now); null when the shape has no say (Steady). Unknown, [from] counts as the middle.
     *
     * A build or a wind down takes [SHAPE_SONGS] songs and a euphoric climb [PEAK_SONGS], however many songs are
     * queued (issue #798): spread over all of them (13 or 30 in a radio), the next song's target hardly moved, so
     * reshaping the mix kept the same next song and only later ones changed.
     */
    fun target(from: Double?, index: Int): Double? {
        val start = from ?: 0.5
        fun along(songs: Int) = ((index + 1).toDouble() / songs).coerceAtMost(1.0)
        return when (this) {
            STEADY -> null
            BUILD -> start + (1 - start) * along(SHAPE_SONGS)
            EUPHORIC -> start + (1 - start) * along(PEAK_SONGS)
            WIND_DOWN -> start * (1 - along(SHAPE_SONGS))
        }
    }

    companion object {
        fun parse(name: String?): MixShape = entries.firstOrNull { it.name == name } ?: STEADY

        /** A build reaches the top, and a wind down the calmest, over this many songs: about half an hour. */
        const val SHAPE_SONGS = 8
        /** A euphoric mix is at its high after this many songs. */
        const val PEAK_SONGS = 3
    }
}

/** The longest song a DJ mix takes, in seconds: a quarter of an hour. Longer ones are whole DJ sets or live recordings in one file. */
const val DJ_LONGEST_SEC = 15 * 60

/**
 * Whether a song of [durationSec] can go into a DJ mix (the owner, 12 Nov): not one longer than [DJ_LONGEST_SEC], such
 * as a continuous mix of an hour, which is a mix itself and would take the DJ mix over. A length that isn't known
 * (0) passes. The song a mix is started from is the listener's choice and isn't asked.
 */
fun fitsDjMix(durationSec: Int): Boolean = durationSec <= DJ_LONGEST_SEC

/** The songs of an album or playlist that its DJ mix takes: those that fit ([fitsDjMix]), or all of them when none does. */
fun djMixSongs(list: List<app.siansa.data.Song>): List<app.siansa.data.Song> = list.filter { fitsDjMix(it.durationSec) }.ifEmpty { list }

/** A song DJ mode may pick, with what it knows of its sound (null when AudioMuse hasn't analysed it). */
data class DjTrack(
    val id: String,
    val artist: String = "",
    /** How alike it is to the seed, 0..1 (1 the most alike). */
    val likeness: Double = 1.0,
    val tempo: Double? = null,
    val key: String? = null,
    val scale: String? = null,
    /** How energetic, 0..1 against the other songs on offer (AudioMuse's energy by rank); null when unknown. */
    val energy: Double? = null,
    /** How bright: AudioMuse's happy and party moods, 0..1; null when unknown. */
    val lift: Double? = null,
    /** AudioMuse's energy as it is, on its own scale: two songs compare by it whatever else is on offer. */
    val level: Double? = null,
    /** The kinds of music it is by its sound ([soundFamilies]): each family and how much of it (Siansa Beats' genres). */
    val families: Map<String, Double> = emptyMap(),
)

/**
 * A key on the Camelot wheel DJs mix by: 1..12 with A for minor and B for major ("8A" is A minor, "8B" C major).
 * Neighbours on the wheel go together. Null for a key that isn't known.
 */
data class Camelot(val number: Int, val minor: Boolean)

private val PITCHES = mapOf(
    "C" to 0, "B#" to 0, "C#" to 1, "DB" to 1, "D" to 2, "D#" to 3, "EB" to 3, "E" to 4, "FB" to 4,
    "F" to 5, "E#" to 5, "F#" to 6, "GB" to 6, "G" to 7, "G#" to 8, "AB" to 8, "A" to 9, "A#" to 10, "BB" to 10,
    "B" to 11, "CB" to 11,
)

fun camelot(key: String?, scale: String?): Camelot? {
    val raw = key?.trim()?.replace("♯", "#")?.replace("♭", "b")?.takeIf { it.isNotEmpty() } ?: return null
    val letter = raw[0].uppercaseChar().takeIf { it in 'A'..'G' } ?: return null
    val accidental = raw.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
    val pitch = PITCHES["$letter${accidental?.uppercaseChar() ?: ""}"] ?: return null
    // Without a scale, "Am" or "A minor" in the key itself, as some taggers write it.
    val rest = raw.drop(if (accidental != null) 2 else 1).trim().lowercase()
    val minor = scale?.trim()?.lowercase()?.startsWith("min") ?: (rest == "m" || rest.startsWith("min"))
    // A minor key sits at the number of its relative major, three semitones up.
    val major = if (minor) (pitch + 3) % 12 else pitch
    return Camelot(((major * 7) % 12 + 7) % 12 + 1, minor)
}

/**
 * How well two keys mix, 0..1: the same key best, then wheel neighbours; 0.5 when either key is unknown. With [lift],
 * a step up from [a] to [b] that DJs use to raise a mix (a semitone or a whole tone up, same mode) counts as fair.
 */
fun keyMatch(a: Camelot?, b: Camelot?, lift: Boolean = false): Double {
    if (a == null || b == null) return 0.5
    val steps = abs(a.number - b.number).let { minOf(it, 12 - it) }
    val up = (b.number - a.number + 12) % 12
    return when {
        steps == 0 && a.minor == b.minor -> 1.0
        steps == 1 && a.minor == b.minor -> 0.9
        steps == 0 -> 0.85 // relative major or minor
        lift && a.minor == b.minor && (up == 2 || up == 7) -> 0.6 // a whole tone or a semitone up
        steps == 2 && a.minor == b.minor -> 0.5
        steps == 1 -> 0.4
        else -> 0.1
    }
}

/** How far apart two tempos are, as a share of [a] (0.06 is 6 %); with [halfDouble], half and double time count as the same. */
fun tempoOff(a: Double, b: Double, halfDouble: Boolean = true): Double =
    (if (halfDouble) listOf(b, b * 2, b / 2) else listOf(b)).minOf { abs(it / a - 1) }

/**
 * How close two tempos are, 0..1: 1 for the same, falling to 0 at [TEMPO_SPREAD] apart. Half and double time count
 * as the same tempo (a 70 BPM song mixes into a 140 BPM one) unless [halfDouble] is off. 0.5 when either is unknown.
 */
fun tempoMatch(a: Double?, b: Double?, halfDouble: Boolean = true): Double {
    if (a == null || b == null || a <= 0 || b <= 0) return 0.5
    return (1 - tempoOff(a, b, halfDouble) / TEMPO_SPREAD).coerceIn(0.0, 1.0)
}

/**
 * The rails of a Steady vibes mix (the owner, 12 Nov): how far a song may be from where the mix started. Without
 * them a steady mix had no say over energy, compared tempo with the last song only (half and double time counting
 * as the same), and so drifted: from a mellow 133 BPM song to drum and bass at 174 in two songs, and stayed there.
 *
 * Tempo alone wasn't enough (the owner's next mix, the same day): from a melodic house song it played minimal techno
 * at the same tempo, and of the wider lot of songs it picks from, some of the least alike, as only those fit the
 * tempo. So a song must also sound alike enough, be of the start's kind of music, and near its energy on AudioMuse's
 * own scale (by rank among the songs on offer, nearly every song was "near").
 *
 * Surprises loosens them a place at a time, as a DJ taking more risks:
 *
 * |                     | None        | A few       | Some                 | Lots          |
 * |---------------------|-------------|-------------|----------------------|---------------|
 * | Tempo, of the start | within 6 %  | within 10 % | within 15 %, or half or double time | anywhere |
 * | Energy, of the start| within 25 % | within 45 % | within 80 %          | free          |
 * | How alike it sounds | the most alike half | 0.35 | 0.2                 | any           |
 * | Kind of music       | the start's | shares one with the start | any  | any           |
 * | Held to the start   | strongly    | loosely     | a little             | not at all    |
 * | Keys                | count most  | a lift up counts | as before       | count least   |
 *
 * - [tempo]: how far a song's tempo may be from the start's (a share of it); null for anywhere.
 * - [halfDouble]: whether half and double time count as the same tempo.
 * - [energy]: how far its energy ([DjTrack.level]) may be from the start's, as a share ([energyOff]); null for free.
 * - [hold]: how much the start counts next to the last song (0..1): its tempo in the tempo score, and its energy.
 * - [keyWeight], [lift]: how much the keys count, and whether a step up counts as fair ([keyMatch]).
 * - [likeness]: how alike a song must sound at least ([DjTrack.likeness], 0..1).
 * - [family]: whether it must be of the start's kind of music ([Family]).
 */
data class SteadyRails(
    val tempo: Double?,
    val halfDouble: Boolean,
    val energy: Double?,
    val hold: Double,
    val keyWeight: Double,
    val lift: Boolean,
    val likeness: Double = 0.0,
    val family: Family = Family.ANY,
) {
    /** How a song's kind of music ([DjTrack.families]) must go with the start's. */
    enum class Family {
        /** What it mainly is ([mainFamily]) must be a kind the start is too: techno doesn't follow house. */
        SAME,
        /** As [SAME], unless it shares any kind with the start: a house song with some techno in it may follow techno. */
        SHARED,
        ANY,
    }

    /** Whether [to] may follow in a mix that started with [start]. A song whose tempo isn't known only where tempo is free. */
    fun allows(start: DjTrack, to: DjTrack): Boolean = off(start, to) == 0.0

    /** Whether [to]'s kind of music goes with [start]'s. Either unknown (no Siansa Beats genres): it does. */
    fun sameKind(start: DjTrack, to: DjTrack): Boolean {
        if (family == Family.ANY || start.families.isEmpty()) return true
        val main = mainFamily(to.families) ?: return true
        if (main in start.families) return true
        return family == Family.SHARED && to.families.keys.any { it in start.families }
    }

    /**
     * How far outside the rails [to] is (0 inside them): what it's over by in tempo and in energy, what it's short of
     * in likeness, and a whole 1 for another kind of music. When too few songs fit, the nearest outside come in, so
     * the music carries on with the least jump, and another kind of music comes last.
     */
    fun off(start: DjTrack, to: DjTrack): Double {
        var over = 0.0
        val from = start.tempo?.takeIf { it > 0 }
        if (tempo != null && from != null) {
            val there = to.tempo?.takeIf { it > 0 }
            over += if (there == null) 1.0 else (tempoOff(from, there, halfDouble) - tempo).coerceAtLeast(0.0)
        }
        if (energy != null) energyOff(start.level, to.level)?.let { over += (it - energy).coerceAtLeast(0.0) }
        over += (likeness - to.likeness).coerceAtLeast(0.0)
        if (!sameKind(start, to)) over += 1.0
        return over
    }

    /** In a line of the log: "tempo within 6 % of the start, energy within 25 %, at least 0.50 alike, the start's kind of music". */
    fun inWords(): String = listOfNotNull(
        tempo?.let { "tempo within ${(it * 100).toInt()} % of the start" + if (halfDouble) " (or half or double time)" else "" },
        energy?.let { "energy within ${(it * 100).toInt()} %" },
        likeness.takeIf { it > 0 }?.let { "at least %.2f alike".format(it) },
        when (family) {
            Family.SAME -> "the start's kind of music"
            Family.SHARED -> "a kind of music shared with the start"
            Family.ANY -> null
        },
    ).joinToString().ifEmpty { "no rails" }

    companion object {
        /** The rails at a Surprises setting (0..1), by the four places the queue shows (None, A few, Some, Lots). */
        fun of(surprise: Double): SteadyRails = when {
            surprise < 0.15 -> SteadyRails(0.06, false, 0.25, 1.0, 0.30, false, 0.5, Family.SAME)
            surprise < 0.40 -> SteadyRails(0.10, false, 0.45, 0.6, 0.25, true, 0.35, Family.SHARED)
            surprise < 0.70 -> SteadyRails(0.15, true, 0.8, 0.25, 0.20, true, 0.2, Family.ANY)
            else -> SteadyRails(null, true, null, 0.0, 0.10, true, 0.0, Family.ANY)
        }

        /** When fewer songs than this fit the rails, the nearest outside them fill up to it, so a mix never runs dry. */
        const val AT_LEAST = 5
    }
}

/**
 * How far apart two energies are on AudioMuse's own scale, as a share of the lower one (0.25: a quarter more);
 * null when either isn't known. The same either way round, and whatever the scale's units are.
 */
fun energyOff(a: Double?, b: Double?): Double? {
    if (a == null || b == null || a <= 0 || b <= 0) return null
    return maxOf(a / b, b / a) - 1
}

/**
 * The kinds of music Siansa Beats' styles fall into, for telling a house song from a techno one in a mix. Electronic
 * music is one genre to Discogs, so its styles are grouped here (the owner's library, 12 Nov: the styles with the
 * most songs); a style not listed counts as no kind, and is never held against a song. Any other genre (Rock, Hip
 * Hop, Jazz…) is its own kind.
 */
private val ELECTRONIC_FAMILIES: Map<String, String> = buildMap {
    fun kind(name: String, vararg styles: String) = styles.forEach { put(it.lowercase(), name) }
    kind("house", "House", "Deep House", "Tech House", "Progressive House", "Electro House", "Euro House", "Tropical House",
        "Tribal House", "Hard House", "Garage House", "Acid House", "Italo House", "Ghetto House", "Hip-House", "UK Garage",
        "Speed Garage", "Bassline", "Disco", "Nu-Disco")
    kind("techno", "Techno", "Deep Techno", "Minimal Techno", "Minimal", "Hard Techno", "Dub Techno", "Schranz", "Acid",
        "Industrial", "EBM", "Rhythmic Noise", "Bleep")
    kind("trance", "Trance", "Tech Trance", "Hard Trance", "Progressive Trance", "Goa Trance", "Psy-Trance")
    kind("drum and bass", "Drum n Bass", "Halftime", "Jungle", "Breakcore", "Juke")
    kind("breaks and bass", "Dubstep", "Grime", "Breakbeat", "Breaks", "Progressive Breaks", "Big Beat", "Broken Beat", "Electro")
    kind("hard dance", "Hardcore", "Happy Hardcore", "Hardstyle", "Jumpstyle", "Donk", "Hands Up", "Gabber")
    kind("calm electronic", "Ambient", "Downtempo", "Drone", "Trip Hop", "Chillwave", "IDM", "Dub", "Abstract", "Glitch")
    kind("dance pop", "Synth-pop", "Dance-pop", "Eurodance", "Europop", "Italodance", "Italo-Disco", "Euro-Disco", "Hi NRG",
        "Synthwave", "Electroclash", "Freestyle")
}

/** The kind of music a genre of Siansa Beats is ([ELECTRONIC_FAMILIES]); null for one that's no kind. */
fun soundFamily(genre: SongGenre): String? {
    val style = genre.style.ifBlank { genre.name.substringAfterLast(" / ") }.trim().lowercase()
    val top = genre.genre.trim()
    return when {
        top.isEmpty() || top.equals("Electronic", ignoreCase = true) -> ELECTRONIC_FAMILIES[style]
        else -> top.lowercase()
    }
}

/** A song's kinds of music and how much of each: its genres' scores added up by kind. Empty without genres. */
fun soundFamilies(genres: List<SongGenre>?): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    genres?.forEach { g -> soundFamily(g)?.let { out[it] = (out[it] ?: 0.0) + g.score } }
    return out
}

/** What a song mainly is: its kind with the most to it, when that's clear ([MAIN_FAMILY] or more); null when it isn't. */
fun mainFamily(families: Map<String, Double>): String? = families.maxByOrNull { it.value }?.takeIf { it.value >= MAIN_FAMILY }?.key

const val MAIN_FAMILY = 0.5

/**
 * The songs of [pool] a steady mix from [start] may pick from, and how many of them are outside [rails]: the ones
 * inside, and when those are fewer than [SteadyRails.AT_LEAST] (or [count], if smaller), the nearest outside too.
 */
fun onRails(start: DjTrack, pool: List<DjTrack>, count: Int, rails: SteadyRails): Pair<List<DjTrack>, Int> {
    val (fit, outside) = pool.partition { rails.allows(start, it) }
    val short = (minOf(count, SteadyRails.AT_LEAST) - fit.size).coerceAtLeast(0)
    val let = outside.sortedBy { rails.off(start, it) }.take(short)
    return (fit + let) to let.size
}

/**
 * The tempo a song counts as in a mix: Siansa Beats' (Beat This!), except for halftime drum and bass. Its drums feel
 * like 87 and are read so, but DJs play it in a 174 set; a song read at [HALFTIME_TEMPOS] that is clearly drum and
 * bass or halftime by its sound (a genre of [HALFTIME_GENRES] at [HALFTIME_SCORE] or more) counts as double. A song
 * with only a trace of them stays as read: it really is that slow (the owner's library, 12 Nov: 86 of 297).
 */
fun mixTempo(tempo: Double?, genres: List<SongGenre>?): Double? {
    if (tempo == null || tempo !in HALFTIME_TEMPOS || genres == null) return tempo
    val halftime = genres.any { g -> g.score >= HALFTIME_SCORE && HALFTIME_GENRES.any { it.equals(g.name, ignoreCase = true) } }
    return if (halftime) tempo * 2 else tempo
}

val HALFTIME_TEMPOS = 78.0..96.0
const val HALFTIME_SCORE = 0.5
val HALFTIME_GENRES = setOf("Drum n Bass", "Halftime", "Jungle", "Liquid Funk", "Neurofunk", "Techstep", "Darkstep", "Jump Up")

private const val TEMPO_SPREAD = 0.08

/**
 * How good a change from [from] to [to] is: how alike [to] is to the seed, then tempo and key; a new artist is
 * better. With [surprise] (0..1, issue #649) likeness and tempo count for less, so the next song may stray
 * further, and [wild] (0..1, the song's draw) can lift any song; the key still counts in full, so a surprise
 * still mixes in. [target] (an energy, 0..1) pulls towards that energy; [bright] favours happy, party songs.
 *
 * On a steady mix's [rails], from [start] (where the mix began): the tempo is scored against the start as well as
 * the last song, as far as the rails hold to it, without half and double time where they don't allow it; the energy
 * is pulled towards the start's; and the keys count as the rails say.
 */
fun transition(
    from: DjTrack,
    to: DjTrack,
    surprise: Double = 0.0,
    wild: Double = 0.0,
    target: Double? = null,
    bright: Boolean = false,
    rails: SteadyRails? = null,
    start: DjTrack? = null,
): Double {
    val sameArtist = from.artist.isNotBlank() && from.artist.equals(to.artist, ignoreCase = true)
    val s = surprise.coerceIn(0.0, 1.0)
    val halfDouble = rails?.halfDouble ?: true
    val step = tempoMatch(from.tempo, to.tempo, halfDouble)
    // Half the tempo score is the start's when the rails hold fully to it, none when they don't hold at all.
    val anchored = if (rails != null && start != null) 0.5 * rails.hold else 0.0
    val tempo = (1 - anchored) * step + anchored * tempoMatch(start?.tempo, to.tempo, halfDouble)
    var score = 0.45 * (1 - 0.6 * s) * to.likeness +
        0.35 * (1 - 0.5 * s) * tempo +
        (rails?.keyWeight ?: 0.20) * keyMatch(camelot(from.key, from.scale), camelot(to.key, to.scale), lift = rails?.lift ?: false) -
        (if (sameArtist) 0.15 else 0.0) +
        0.4 * s * wild.coerceIn(0.0, 1.0)
    // Pulled towards the start's energy: on AudioMuse's own scale when both are known, else by rank among the songs.
    if (rails != null && start != null) {
        val near = energyOff(start.level, to.level)?.let { (1 - it).coerceIn(0.0, 1.0) }
            ?: start.energy?.let { from -> to.energy?.let { 1 - abs(it - from) } }
        score += ENERGY_WEIGHT * rails.hold * (near ?: 0.5)
    }
    if (target != null) score += ENERGY_WEIGHT * (to.energy?.let { 1 - abs(it - target) } ?: 0.5)
    if (bright) score += LIFT_WEIGHT * (to.lift ?: 0.3)
    return score
}

/** How much a shape's energy target counts next to likeness, tempo and key. */
const val ENERGY_WEIGHT = 0.45
/** How much a song's brightness counts in a euphoric mix. */
const val LIFT_WEIGHT = 0.2

/**
 * DJ mode's order (issue #337): from [seed], each next song is the best change from the one before it, so the
 * set moves smoothly instead of jumping in tempo or key. At most [count] of [pool]. [shape] bends the energy
 * over the picks and [surprise] (0..1) lets them stray, with [random] drawing each song's wild card (issue #649).
 *
 * With [start] (the song a radio or Keep playing began from), a Steady vibes mix stays on its rails ([SteadyRails]):
 * only songs near the start in tempo and energy are picked, so fewer than [count] may come back. Without it (an
 * album's own mix, which plays every song) and for the other shapes, the order is as it was.
 */
fun djOrder(
    seed: DjTrack,
    pool: List<DjTrack>,
    count: Int,
    shape: MixShape = MixShape.STEADY,
    surprise: Double = 0.0,
    random: kotlin.random.Random = kotlin.random.Random.Default,
    start: DjTrack? = null,
): List<DjTrack> {
    val rails = if (start != null && shape == MixShape.STEADY) SteadyRails.of(surprise) else null
    val all = pool.filter { it.id != seed.id && it.id != start?.id }.distinctBy { it.id }
    val left = (if (rails != null && start != null) onRails(start, all, count, rails).first else all).toMutableList()
    val wild = if (surprise > 0) left.associate { it.id to random.nextDouble() } else emptyMap()
    val picked = ArrayList<DjTrack>(count)
    var tail = seed
    while (picked.size < count && left.isNotEmpty()) {
        val target = shape.target(seed.energy, picked.size)
        val next = left.maxBy { transition(tail, it, surprise, wild[it.id] ?: 0.0, target, bright = shape == MixShape.EUPHORIC, rails = rails, start = start) }
        picked += next
        left -= next
        tail = next
    }
    return picked
}

/** Each song's energy as its rank among [values] (0 the calmest, 1 the most energetic); null stays null. */
fun energyByRank(values: Map<String, Double?>): Map<String, Double?> {
    val known = values.filterValues { it != null }.entries.sortedBy { it.value }
    val n = known.size
    val ranked = known.mapIndexed { i, e -> e.key to (if (n <= 1) 0.5 else i.toDouble() / (n - 1)) }.toMap()
    return values.mapValues { (id, _) -> ranked[id] }
}

/** 1 for the most alike of [size] songs in AudioMuse's order down to near 0 for the last: its scores differ by route. */
fun likenessByRank(index: Int, size: Int): Double = if (size <= 1) 1.0 else 1.0 - index.toDouble() / size

/**
 * Keep playing (issue #337): when only a song or so is left in the queue, songs like the last one are added so the
 * music doesn't stop. Steady adds them in the order they're alike; DJ orders them for smooth changes with
 * AudioMuse's tempo and key. Songs in the queue or played lately aren't added again.
 */
class ContinuousPlay(private val graph: AppGraph) {
    private var job: Job? = null
    /** Songs this has added since the app started, so a long session doesn't loop back to them. */
    private val added = LinkedHashSet<String>()
    /** The songs it added in DJ mode, which blend as DJ playback (read from the player's thread). */
    private val addedDj: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * Where Keep playing's DJ mix began: the last song of the queue the first time it added songs. Later top-ups,
     * after songs it added itself, keep it, so a steady mix is held to where it started, not to its last song.
     */
    @Volatile private var mixStart: Song? = null

    /** Whether [id] was added by Keep playing in DJ mode: a change into or out of it is beat-matched. */
    fun addedForDj(id: String?): Boolean = id != null && id in addedDj

    private data class Snapshot(val left: Int, val last: Song?, val hasMedia: Boolean, val repeatOff: Boolean)

    init {
        graph.scope.launch {
            combine(
                graph.player.state
                    .map {
                        // Shuffled, the last song in the list isn't the last to play: the songs follow the one playing.
                        val last = if (it.shuffle) it.current else it.queue.lastOrNull()
                        Snapshot(it.left, last, it.hasMedia, it.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF)
                    }
                    .distinctUntilChanged(),
                graph.settings.state.map { it.continuousPlay }.distinctUntilChanged(),
            ) { snapshot, mode -> snapshot to mode }
                .collect { (snapshot, mode) ->
                    val due = continuousTopUpDue(
                        mode = mode,
                        left = snapshot.left,
                        hasMedia = snapshot.hasMedia,
                        repeatOff = snapshot.repeatOff,
                        // A song radio or a genre radio adds its own songs.
                        radioOn = graph.songRadio.seed.value != null || graph.genreRadio.active,
                        windingDown = graph.sleepTimer.windingDown(),
                        sleepFinishing = graph.sleepTimer.state.value?.finishing == true,
                    )
                    val last = snapshot.last
                    // Nothing is added after a radio station or podcast episode (issue #353): there's nothing like it to find.
                    if (!due || last == null || job?.isActive == true || app.siansa.data.OutsideMedia.isOutside(last.id)) return@collect
                    job = graph.scope.launch { topUp(mode, last) }
                }
        }
    }

    private suspend fun topUp(mode: ContinuousMode, last: Song) {
        val started = System.currentTimeMillis()
        try {
            val queue = graph.player.state.value.queue.map { it.id }
            val lately = runCatching { graph.history.recent(RECENT) }.getOrDefault(emptyList()).map { it.id }
            val skip = HashSet<String>().apply { addAll(queue); addAll(lately); addAll(added); add(last.id) }
            val hits = graph.sonic.similar(last.id, (POOL + skip.size).coerceAtMost(MAX_POOL))
                .filter { it.id !in skip }
                .distinctBy { it.id }
                .take(POOL)
            if (hits.isEmpty()) {
                AppLog.i("KeepPlaying", "Nothing new like \"${last.title}\" to add")
                return
            }
            // DJ only when its needs are met (AudioMuse and Siansa Beats, DjReady): else these are added as in Steady,
            // and play as ordinary songs, not blended as a DJ mix.
            val dj = mode == ContinuousMode.DJ && graph.djProblem().let { problem ->
                if (problem != null) AppLog.i("KeepPlaying", "DJ isn't possible now ($problem); adding as in Steady")
                problem == null
            }
            // The mix began at the first song it carried on from; after one of its own songs, it's still that mix.
            val start = if (dj) (mixStart?.takeIf { last.id in addedDj } ?: last).also { mixStart = it } else null
            // A whole DJ set in one file has no place inside a DJ mix ([fitsDjMix]): the phone's library knows how long each is.
            val fitting = if (!dj) hits else {
                val tooLong = graph.library.songs(hits.map { it.id }).filter { !fitsDjMix(it.durationSec) }.map { it.id }.toSet()
                if (tooLong.isNotEmpty()) AppLog.i("KeepPlaying", "DJ: left out ${tooLong.size} songs longer than ${DJ_LONGEST_SEC / 60} minutes")
                hits.filter { it.id !in tooLong }
            }
            if (fitting.isEmpty()) return
            val ids = if (dj) djIds(last, fitting, start = start) ?: fitting.take(TOP_UP).map { it.id } else fitting.take(TOP_UP).map { it.id }
            val byId = fitting.associateBy { it.id }
            var songs = graph.generator.resolveHits(ids.mapNotNull { byId[it] })
            // Also the ones only the server knew the length of.
            if (dj) songs = songs.filter { fitsDjMix(it.durationSec) }
            // Offline, or with "Only downloaded music" on, only songs on the phone can play.
            if (graph.connection.current.offline || graph.settings.current.downloadedOnly) {
                songs = songs.filter { graph.downloads.isDownloaded(it.id) }
            }
            if (songs.isEmpty()) return
            // Something else may have taken over while AudioMuse was answering: a new list, a radio, the wind down.
            val now = graph.player.state.value
            val stillLast = if (now.shuffle) now.queue.any { it.id == last.id } else now.queue.lastOrNull()?.id == last.id
            if (!stillLast || graph.songRadio.seed.value != null || graph.sleepTimer.windingDown()) {
                AppLog.d("KeepPlaying", "Queue changed while finding songs; not adding")
                return
            }
            added += songs.map { it.id }
            if (dj) {
                addedDj += songs.map { it.id }
                if (addedDj.size > MAX_REMEMBERED) addedDj.retainAll(added)
            }
            while (added.size > MAX_REMEMBERED) added.remove(added.first())
            AppLog.i(
                "KeepPlaying",
                "${mode.label}: added ${songs.size} songs like \"${last.title}\" in ${System.currentTimeMillis() - started}ms",
            )
            withContext(Dispatchers.Main) { graph.player.continueWith(songs) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("KeepPlaying", "Couldn't add songs like \"${last.title}\": ${e.message}")
        }
    }

    /**
     * [songs] in DJ order after [last] (issue #337): smooth changes in tempo and key, the most alike first. A song
     * radio in DJ mode uses this too. [songs] as they are when AudioMuse's analysis isn't available.
     *
     * [start] is the song the mix began from: with it, a Steady vibes mix stays on its rails ([SteadyRails]) and
     * fewer than [count] songs may come back, when the rest are too far from where it started.
     */
    suspend fun djArrange(last: Song, songs: List<Song>, count: Int = songs.size, start: Song? = null, likeness: ((String) -> Double)? = null): List<Song> {
        // A whole DJ set in one file has no place inside a DJ mix ([fitsDjMix]).
        @Suppress("NAME_SHADOWING")
        val songs = songs.filter { fitsDjMix(it.durationSec) }.also {
            if (it.size < songs.size) AppLog.i("KeepPlaying", "DJ: left out ${songs.size - it.size} songs longer than ${DJ_LONGEST_SEC / 60} minutes")
        }
        if (songs.size < 2) return songs.take(count)
        val byId = songs.associateBy { it.id }
        val hits = songs.map { app.siansa.net.ClapHit(it.id, it.title, it.artist, it.album, 0.0) }
        val ids = djIds(last, hits, count, likeness, start) ?: return songs.take(count)
        return ids.mapNotNull { byId[it] }
    }

    /** DJ mode's order of [hits] after [last]; null when AudioMuse's analysis isn't available (Steady order then). */
    /**
     * [likeness] says how alike each song is to [last] (0..1); without it, the order of [hits] says so, the most
     * alike first, as AudioMuse returns them. A reshape passes its own (issue #786): the queue's order isn't
     * likeness, and ranking by it kept whatever was next where it was, whatever the shape.
     */
    private suspend fun djIds(last: Song, hits: List<app.siansa.net.ClapHit>, count: Int = TOP_UP, likeness: ((String) -> Double)? = null, start: Song? = null): List<String>? {
        if (!graph.settings.current.soundReady) {
            AppLog.i("KeepPlaying", "DJ needs Siansa Beats; adding in Steady order")
            return null
        }
        // From Siansa Beats alone: a DJ mix never goes by AudioMuse's tempo (the owner, 11 Nov).
        val all = (listOfNotNull(start?.id, last.id) + hits.map { it.id }).distinct()
        val features = runCatching { graph.audioMuse.features(all, beatsOnly = true) }
            .onFailure { AppLog.w("KeepPlaying", "DJ couldn't read tempo and key: ${it.message}") }
            .getOrNull()
            ?.associateBy { it.id }
            ?: return null
        // Each song's genres by its sound (Siansa Beats 0.32), for halftime drum and bass's tempo in a mix ([mixTempo]);
        // without them every tempo counts as read.
        val genres = graph.beatsGenres.ofSongs(all).orEmpty()
        val energy = energyByRank(all.associateWith { features[it]?.energy })
        fun lift(f: app.siansa.net.TrackFeatures?): Double? = f?.moods?.let { m -> listOfNotNull(m["happy"], m["party"]).takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 1.0) }
        fun track(id: String, artist: String, likeness: Double): DjTrack {
            val f = features[id]
            return DjTrack(id, artist, likeness, mixTempo(f?.tempo, genres[id]), f?.key, f?.scale, energy[id], lift(f), f?.energy, soundFamilies(genres[id]))
        }
        val seed = track(last.id, last.artist, 1.0)
        val begun = start?.let { track(it.id, it.artist, 1.0) }
        val pool = hits.mapIndexed { i, hit -> track(hit.id, hit.artist, likeness?.invoke(hit.id) ?: likenessByRank(i, hits.size)) }
        val settings = graph.settings.current
        val shape = settings.mixShape
        val surprise = settings.mixSurprise / 100.0
        val order = djOrder(seed, pool, count, shape, surprise, start = begun)
        if (begun != null && shape == MixShape.STEADY) {
            // What the rails let through, for "why did it pick that?": how many songs fit, and what was let in besides.
            val rails = SteadyRails.of(surprise)
            val (allowed, outside) = onRails(begun, pool.filter { it.id != seed.id && it.id != begun.id }, count, rails)
            AppLog.i(
                "KeepPlaying",
                "Steady rails from \"${start?.title}\" (${begun.tempo?.let { "%.0f BPM".format(it) } ?: "tempo unknown"}" +
                    "${begun.level?.let { ", energy %.3f".format(it) } ?: ""}" +
                    "${begun.families.takeIf { it.isNotEmpty() }?.let { f -> ", " + f.entries.joinToString(" and ") { "%s %.2f".format(it.key, it.value) } } ?: ", kind of music unknown"}" +
                    "): ${allowed.size - outside} of ${pool.size} songs fit " +
                    "(${rails.inWords()})" + (if (outside > 0) ", $outside nearest outside let in so the mix carries on" else "") +
                    ", ${order.size} picked",
            )
        }
        AppLog.d(
            "KeepPlaying",
            "DJ (${shape.label.lowercase()}, surprise ${settings.mixSurprise} %) from ${seed.tempo?.let { "%.0f BPM".format(it) } ?: "unknown tempo"} ${seed.key.orEmpty()} ${seed.scale.orEmpty()}" +
                "${seed.energy?.let { " energy %.2f".format(it) } ?: ""}" +
                // Where the shape aims the next song, and each song's likeness (issue #798): what decided the next one.
                "${shape.target(seed.energy, 0)?.let { ", next aimed at energy %.2f".format(it) } ?: ""}: " +
                // Each pick's tempo, key, energy (by rank, and AudioMuse's own), likeness and what it mainly is.
                order.joinToString { t ->
                    "${t.tempo?.let { "%.0f".format(it) } ?: "?"} ${t.key.orEmpty()}${t.energy?.let { " e%.2f".format(it) } ?: ""}" +
                        "${t.level?.let { " (%.3f)".format(it) } ?: ""} l%.2f".format(t.likeness) + (mainFamily(t.families)?.let { " $it" } ?: "")
                },
        )
        return order.map { it.id }
    }

    companion object {
        /** Topped up when this many songs or fewer come after the one playing. */
        const val WHEN_LEFT = 1
        const val TOP_UP = 20
        private const val POOL = 60
        private const val MAX_POOL = 500
        private const val RECENT = 200
        private const val MAX_REMEMBERED = 1000
    }
}
