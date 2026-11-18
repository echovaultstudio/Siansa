package app.siansa.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import app.siansa.mix.NOTICE_ICONS

/** The icons a notice of your own can have (the owner, 18 Nov), by the keys kept on the card ([NOTICE_ICONS]), with a name for TalkBack. */
internal object NoticeIcons {
    private val icons: Map<String, Pair<ImageVector, String>> = mapOf(
        "album" to (Icons.Rounded.Album to "Album"),
        "song" to (Icons.Rounded.MusicNote to "Note"),
        "new" to (Icons.Rounded.NewReleases to "New"),
        "star" to (Icons.Rounded.Star to "Star"),
        "heart" to (Icons.Rounded.Favorite to "Heart"),
        "fire" to (Icons.Rounded.LocalFireDepartment to "Fire"),
        "bolt" to (Icons.Rounded.Bolt to "Bolt"),
        "headphones" to (Icons.Rounded.Headphones to "Headphones"),
        "mic" to (Icons.Rounded.Mic to "Microphone"),
        "piano" to (Icons.Rounded.Piano to "Piano"),
        "calendar" to (Icons.Rounded.CalendarMonth to "Calendar"),
        "history" to (Icons.Rounded.History to "Clock"),
        "explore" to (Icons.Rounded.Explore to "Compass"),
        "party" to (Icons.Rounded.Celebration to "Party"),
        "moon" to (Icons.Rounded.Nightlight to "Moon"),
        "list" to (Icons.AutoMirrored.Rounded.QueueMusic to "List"),
    )

    /** Every icon to pick from, in [NOTICE_ICONS]' order: its key, picture and name. */
    val all: List<Triple<String, ImageVector, String>> get() = NOTICE_ICONS.mapNotNull { key -> icons[key]?.let { Triple(key, it.first, it.second) } }

    fun of(key: String): ImageVector? = icons[key]?.first

    fun nameOf(key: String): String? = icons[key]?.second
}
