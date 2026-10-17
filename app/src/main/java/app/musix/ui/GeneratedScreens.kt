package app.musix.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.net.ApiException
import androidx.compose.foundation.layout.Column
import app.musix.mix.moodRadios

fun NavController.openMoodRadio(id: String) = navigate("radio/mood/${Uri.encode(id)}")
fun NavController.openGenreRadio(name: String) = navigate("radio/genre/${Uri.encode(name)}")
fun NavController.openMoodPlaylist(text: String) = navigate("mood/${Uri.encode(text)}")

/** A mood or genre radio shown as a playlist first, so you can look, play or shuffle. */
@Composable
fun RadioScreen(nav: NavController, kind: String, key: String) {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val mood = moodRadios.firstOrNull { it.id == key }
    val title = if (kind == "mood") mood?.name ?: key else key
    val colors = if (mood != null) Color(mood.colors.first) to Color(mood.colors.second) else tileColors(key)
    val songs by loadState(Triple(kind, key, round)) {
        when (kind) {
            "mood" -> {
                graph.connection.current.aiProblem(graph.settings.current)?.let { throw ApiException("AI backend is currently not available :( $it") }
                graph.radios.mood(mood ?: throw IllegalStateException("Unknown radio"))
            }
            else -> graph.radios.genre(key)
        }
    }
    CollectionPage(
        nav = nav,
        title = "$title radio",
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = songs.valueOrNull(),
        error = (songs as? Load.Err)?.message,
        cover = { m -> GeneratedCover(title, colors, m) },
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CollectionByline(
                    null,
                    null,
                    null,
                    (if (kind == "mood") "Sound radio" else "Genre radio") + (songs.valueOrNull()?.let { " · ${it.size} songs" } ?: ""),
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { round++ }) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("New picks")
                }
            }
        },
    )
}

/** A playlist made on the spot from what the listener typed about their mood. */
@Composable
fun MoodScreen(nav: NavController, text: String) {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val result by loadState(text to round) {
        graph.connection.current.aiProblem(graph.settings.current)?.let { throw ApiException("AI backend is currently not available :( $it") }
        graph.generator.moodPlaylist(text)
    }
    val r = result.valueOrNull()
    val colors = tileColors(text)
    CollectionPage(
        nav = nav,
        title = r?.title ?: "Making your playlist…",
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = r?.songs,
        error = (result as? Load.Err)?.message,
        cover = { m -> GeneratedCover(r?.title ?: text, colors, m) },
        header = {
            if (r != null) {
                Text(r.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CollectionByline(null, null, null, "From “$text”" + (r?.let { " · ${it.songs.size} songs" } ?: ""))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { round++ }) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Again")
                }
            }
        },
    )
}

/** "Describe your mood" box that opens a playlist made from it. */
@Composable
fun MoodBox(nav: NavController, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val aiProblem = rememberAiProblem()
    fun go() {
        if (text.trim().length >= 3) nav.openMoodPlaylist(text.trim())
    }
    val colors = Color(0xFF4B2BD6) to Color(0xFFD6338A)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(lerp(colors.first, Color.Black, 0.2f), lerp(colors.second, Color.Black, 0.35f))))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
            Text("How are you feeling?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        if (aiProblem != null) {
            // No box to type into while AudioMuse can't be reached: it would only make an empty playlist.
            AiUnavailable(aiProblem, onDark = true)
            return@Column
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("e.g. tired but hopeful, rainy Sunday, need to focus", color = Color.White.copy(alpha = 0.6f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            trailingIcon = {
                IconButton(onClick = { go() }, enabled = text.trim().length >= 3) {
                    Icon(Icons.Rounded.AutoAwesome, "Make playlist", tint = Color.White)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
