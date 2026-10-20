package app.musix.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** One mix of a home carousel (issue #253): its name and what it should sound like. */
@Serializable
data class CarouselMix(val name: String, val sound: String)

/**
 * A home carousel (issue #253): 3 or 4 mixes of your own for part of the day, like "Your morning" with
 * Wake up, Caffeine kick and Mellow morning. Set in Vibes; Home shows the one for the hour.
 */
@Serializable
data class Carousel(
    val id: String,
    val title: String,
    val startHour: Int,
    val endHour: Int,
    val mixes: List<CarouselMix>,
    /** Raised on each change, so the day's mixes are made again from the new names and sounds. */
    val version: Int = 0,
) {
    fun covers(hour: Int): Boolean = when {
        startHour == endHour -> true
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    /** The mixes with a name and a sound, at most [MAX]. */
    val usable: List<CarouselMix> get() = mixes.filter { it.name.isNotBlank() && it.sound.isNotBlank() }.take(MAX)

    /** The vibe each mix is made from: the mix's sound, in the carousel's hours, never the same id twice a version. */
    fun selectorFor(index: Int): Selector {
        val mix = usable[index]
        return Selector(
            id = "carousel-$id-v$version-$index",
            name = mix.name,
            startHour = startHour,
            endHour = endHour,
            guidance = "${title.trim()}: ${mix.name.trim()}. ${mix.sound.trim()}",
            baseQuery = mix.sound.trim(),
            length = MIX_LENGTH,
        )
    }

    companion object {
        const val MIN = 3
        const val MAX = 4
        const val MIX_LENGTH = 30
    }
}

/** What a carousel needs before it can be saved, or null when it's fine. */
fun carouselProblem(c: Carousel): String? = when {
    c.title.isBlank() -> "Give the carousel a name."
    c.usable.size < Carousel.MIN -> "Fill in at least ${Carousel.MIN} mixes, each with a name and a sound."
    c.usable.map { it.name.trim().lowercase() }.distinct().size < c.usable.size -> "Give each mix its own name."
    c.usable.map { it.sound.trim().lowercase() }.distinct().size < c.usable.size -> "Give each mix its own sound, so they differ."
    else -> null
}

object DefaultCarousels {
    val all = listOf(
        Carousel(
            "morning", "Your morning", 5, 11,
            listOf(
                CarouselMix("Wake up", "gentle bright acoustic, soft vocals, slowly building"),
                CarouselMix("Caffeine kick", "upbeat energetic groove, punchy drums, positive"),
                CarouselMix("Mellow morning", "calm mellow chill, warm and relaxed"),
                CarouselMix("Sunny start", "happy sunny feel-good pop, bright"),
            ),
        ),
        Carousel(
            "day", "Your day", 11, 17,
            listOf(
                CarouselMix("Deep focus", "instrumental focus music, steady, minimal, no vocals"),
                CarouselMix("Get it done", "driving upbeat rhythm, energetic and motivating"),
                CarouselMix("Lunch break", "easy-going groove, funky and light"),
                CarouselMix("Afternoon lift", "uplifting melodic, bright and catchy"),
            ),
        ),
        Carousel(
            "evening", "Your evening", 17, 22,
            listOf(
                CarouselMix("Wind down", "relaxed mellow, soft and warm"),
                CarouselMix("Cooking", "groovy soulful, warm and upbeat"),
                CarouselMix("Golden hour", "dreamy chill, warm synths, sunset"),
                CarouselMix("Night out", "danceable party, energetic club"),
            ),
        ),
        Carousel(
            "night", "Your night", 22, 5,
            listOf(
                CarouselMix("Late night", "dark atmospheric, slow and deep"),
                CarouselMix("Lights low", "ambient calm, quiet and soft"),
                CarouselMix("Night drive", "moody electronic, driving synths"),
                CarouselMix("Drifting off", "very calm, soft piano and ambient, for sleep"),
            ),
        ),
    )
}

/** The home carousels, kept per user. */
class CarouselStore(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, Users.scoped("carousels.json")),
        ListSerializer(Carousel.serializer()),
    ) { DefaultCarousels.all }

    val state: StateFlow<List<Carousel>?> = json.state

    suspend fun all(): List<Carousel> = json.read()

    suspend fun get(id: String): Carousel? = all().firstOrNull { it.id == id }

    /** The carousel for [hour], if any. */
    suspend fun forHour(hour: Int): Carousel? = all().firstOrNull { it.covers(hour) }

    suspend fun save(carousel: Carousel) {
        json.update { list -> list.map { if (it.id == carousel.id) carousel.copy(version = it.version + 1) else it } }
    }

    suspend fun reset(id: String) {
        val default = DefaultCarousels.all.firstOrNull { it.id == id } ?: return
        json.update { list -> list.map { if (it.id == id) default.copy(version = it.version + 1) else it } }
    }
}
