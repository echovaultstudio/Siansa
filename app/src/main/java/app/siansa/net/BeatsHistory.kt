package app.siansa.net

import app.siansa.data.AppSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/** A stretch of a listener's history in numbers, from Siansa Beats: what a report shows, and the listens per hour of the day. */
class BeatsTotals(val totals: HistoryApi.Totals, val hours: List<Int>)

/**
 * The listening history read back from Siansa Beats (the owner, 12 Nov: with listens going to Siansa Beats alone, the
 * Time machine was empty and still asked for Koito or Last.fm). Siansa Beats keeps each listener's history
 * ([BeatsListens] sends it there, and it takes in exports of Koito and Last.fm), so everything that read the history
 * from Koito or Last.fm reads it here too: the Time machine, the listening reports, Rediscover and the taste profile.
 *
 * [get] asks Siansa Beats; [settings] say whether listens go there and who the listener is.
 */
class BeatsHistory(private val get: suspend (pathAndQuery: String) -> JsonElement, private val settings: () -> AppSettings) {
    /** Whether Siansa Beats is where the history is: "Scrobble to Siansa Beats" is on and it takes listens. */
    val on: Boolean get() = settings().beatsScrobbleReady

    private fun listener(): String = URLEncoder.encode(BeatsListens.listener(settings()), "UTF-8")

    /**
     * The listener's most listened [kind] ("songs", "artists" or "albums") from [fromSec] up to [toSec], most first.
     * A song or an album comes with its name as [Played.title].
     */
    suspend fun top(kind: String, fromSec: Long, toSec: Long, limit: Int): List<Played> =
        beatsTop(get("$TOP?user=${listener()}&kind=$kind&from=$fromSec&to=$toSec&limit=$limit") as? JsonObject, kind)

    /** That stretch in numbers (Siansa Beats 0.33), the hours of the day counted in the time zone [zone]. */
    suspend fun totals(fromSec: Long, toSec: Long, zone: String): BeatsTotals =
        beatsTotals(get("$TOTALS?user=${listener()}&from=$fromSec&to=$toSec&tz=${URLEncoder.encode(zone, "UTF-8")}") as? JsonObject)
            ?: throw ApiException("Siansa Beats sent an unexpected answer")

    companion object {
        const val TOP = "/api/v1/listens/top"
        const val TOTALS = "/api/v1/listens/totals"
    }
}

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.number(name: String): Long? = (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

/** Siansa Beats' answer to `listens/top` for [kind]: each row an artist, with a song's or an album's name as its title. */
fun beatsTop(answer: JsonObject?, kind: String): List<Played> =
    (answer?.get("top") as? JsonArray).orEmpty().mapNotNull { row ->
        val o = row as? JsonObject ?: return@mapNotNull null
        val artist = o.text("artist") ?: return@mapNotNull null
        val title = when (kind) {
            "artists" -> null
            "albums" -> o.text("album") ?: return@mapNotNull null
            else -> o.text("title") ?: return@mapNotNull null
        }
        Played(artist, title, o.number("listens") ?: 0)
    }

/** Siansa Beats' answer to `listens/totals`; null when it isn't one. */
fun beatsTotals(answer: JsonObject?): BeatsTotals? {
    val listens = answer?.number("listens") ?: return null
    val hours = (answer["hours"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
    return BeatsTotals(
        HistoryApi.Totals(
            minutes = ((answer.number("seconds") ?: 0) / 60).toInt(),
            plays = listens.toInt(),
            artists = (answer.number("artists") ?: 0).toInt(),
            tracks = (answer.number("songs") ?: 0).toInt(),
            albums = (answer.number("albums") ?: 0).toInt(),
            newArtists = (answer.number("new_artists") ?: 0).toInt(),
            newTracks = 0,
        ),
        if (hours.size == 24) hours else List(24) { 0 },
    )
}
