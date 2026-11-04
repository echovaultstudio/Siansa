package app.siansa.tvapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.flow.MutableStateFlow

/** The TV app's pages (issue #860). */
sealed interface TvPage {
    data object Home : TvPage
    data object Library : TvPage
    data object Search : TvPage
    data object Settings : TvPage
    data object NowPlaying : TvPage
    data class Album(val id: String) : TvPage
    data class Artist(val id: String) : TvPage
    data class Playlist(val id: String, val name: String) : TvPage
}

/** The tabs along the top, in order. */
private val TABS = listOf(TvPage.Home to "Home", TvPage.Library to "Library", TvPage.Search to "Search", TvPage.Settings to "Settings")

/**
 * The TV app's frame: sign-in until a server is set, then the tabs along the top and the page under them. Back goes
 * back a page, then to Home, then leaves the app.
 */
@Composable
fun TvRoot(searches: MutableStateFlow<String?>) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val search by searches.collectAsStateWithLifecycle()
    val pages = remember { mutableStateListOf<TvPage>(TvPage.Home) }
    val page = pages.last()

    fun open(next: TvPage) { pages.add(next) }
    fun tab(next: TvPage) { pages.clear(); pages.add(next) }

    // A search from Google TV opens the Search page with it.
    LaunchedEffect(search) { if (search != null) tab(TvPage.Search) }

    BackHandler(enabled = pages.size > 1 || page != TvPage.Home) {
        if (pages.size > 1) pages.removeAt(pages.lastIndex) else tab(TvPage.Home)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!settings.navidromeReady || signingIn) {
            TvSignIn()
            return@Box
        }
        if (page == TvPage.NowPlaying) {
            TvNowPlaying()
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(
                current = pages.first(),
                playing = player.hasMedia,
                onTab = ::tab,
                onNowPlaying = { open(TvPage.NowPlaying) },
            )
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    TvPage.Home -> TvHome(::open)
                    TvPage.Library -> TvLibrary(::open)
                    TvPage.Search -> TvSearch(search, onDone = { searches.value = null }, open = ::open)
                    TvPage.Settings -> TvSettings()
                    is TvPage.Album -> TvAlbum(page.id, ::open)
                    is TvPage.Artist -> TvArtist(page.id, ::open)
                    is TvPage.Playlist -> TvPlaylist(page.id, page.name)
                    TvPage.NowPlaying -> Unit
                }
            }
        }
    }
}

@Composable
private fun TopBar(current: TvPage, playing: Boolean, onTab: (TvPage) -> Unit, onNowPlaying: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TV_EDGE, vertical = Space.Section),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Siansa",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = Space.Section),
        )
        for ((tab, label) in TABS) {
            val selected = tab == current
            val shape = RoundedCornerShape(percent = 50)
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .tvFocus(shape, onClick = { onTab(tab) })
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background, shape)
                    .padding(horizontal = Space.Page, vertical = Space.Small),
            )
        }
        if (playing) {
            Box(Modifier.weight(1f))
            Row(
                Modifier
                    .tvFocus(RoundedCornerShape(percent = 50), onClick = onNowPlaying)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                    .padding(horizontal = Space.Page, vertical = Space.Small),
                horizontalArrangement = Arrangement.spacedBy(Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Text("Now playing", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
