package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.BeatsUse

/**
 * Settings › Discovery (the owner, 17 Nov; they were only switches on Siansa Beats' card before): how genres are told,
 * where similar music and the search by words come from, and Flow's moods. Each is Siansa Beats' when it's set up; the
 * Siansa Beats page lists them and links here.
 */
@Composable
fun DiscoveryScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Discovery", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page).testTag("discovery"),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            if (!settings.beatsReady) {
                Hint(
                    if (settings.audioMuseReady) "Similar music and the search by words come from AudioMuse-AI. With Siansa Beats set up (Settings › Services), genres by sound and Flow's moods come too."
                    else "With Siansa Beats set up (Settings › Services): genres heard in the music, similar music and the search by words, and Flow's moods. Without it, genres come from your songs' tags.",
                )
                PageLink("Services", "Set up Siansa Beats", "discoveryServices") { nav.navigate("settings/services") }
            } else {
                FoldSection("Genres", if (settings.beatsFor(BeatsUse.GENRES)) "By sound" else "Your tags") {
                    BeatsUseSwitch(graph, BeatsUse.GENRES)
                }
                HorizontalDivider()
                FoldSection("Similar music & search by words", if (settings.beatsFor(BeatsUse.SOUND)) "Siansa Beats" else "Off") {
                    BeatsUseSwitch(graph, BeatsUse.SOUND)
                }
                HorizontalDivider()
                FoldSection("Moods in Flow", if (settings.beatsFor(BeatsUse.MOODS)) "On" else "Off") {
                    BeatsUseSwitch(graph, BeatsUse.MOODS)
                }
            }
        }
    }
}
