package app.siansa.data

import android.content.Context
import app.siansa.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * A radio kept as a favourite (the owner, 10 Nov): a sound, mood or genre radio, or "More like" a playlist, by the page
 * that opens it ([route], "radio/mood/happy"), with its name and its cover's two colours (ARGB) for Your Library and
 * Home.
 */
@Serializable
data class FavouriteRadio(val route: String, val title: String, val from: Long, val to: Long, val at: Long)

/**
 * Favourite radios, kept on the phone per user: hearted on the radio's page, listed under Your Library › Favourites,
 * and a custom card can be made of one (its songs on Home).
 */
class FavouriteRadios(context: Context, private val scope: CoroutineScope) {
    private val json = JsonFile(File(context.filesDir, Users.scoped("favourite_radios.json")), ListSerializer(FavouriteRadio.serializer())) { emptyList() }
    private val _all = MutableStateFlow<List<FavouriteRadio>>(emptyList())
    /** The favourite radios, the newest first. */
    val all: StateFlow<List<FavouriteRadio>> = _all.asStateFlow()

    init {
        scope.launch { _all.value = json.read() }
    }

    fun isFavourite(route: String): Boolean = _all.value.any { it.route == route }

    /** Hearts or unhearts the radio at [route]. */
    fun toggle(route: String, title: String, from: Long, to: Long) {
        val on = !isFavourite(route)
        _all.value = toggled(_all.value, FavouriteRadio(route, title, from, to, System.currentTimeMillis()))
        AppLog.i("Favorites", "${if (on) "Favourite" else "No longer favourite"}: radio $title ($route)")
        scope.launch { json.update { toggled(it, FavouriteRadio(route, title, from, to, System.currentTimeMillis())) } }
    }

    suspend fun get(route: String): FavouriteRadio? = json.read().firstOrNull { it.route == route }

    companion object {
        /** [list] with [radio] taken out when it's there (by its route), else put first. */
        fun toggled(list: List<FavouriteRadio>, radio: FavouriteRadio): List<FavouriteRadio> =
            if (list.any { it.route == radio.route }) list.filterNot { it.route == radio.route } else listOf(radio) + list
    }
}
