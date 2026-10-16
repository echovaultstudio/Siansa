package app.musix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.media.MediaRouter
import app.musix.playback.Cast

/**
 * The cast button: picks a TV or speaker to play on. Hidden on phones that can't cast, where
 * [keepSpace] still holds its place so a centred title stays centred.
 */
@Composable
fun CastButton(tint: Color = LocalContentColor.current, keepSpace: Boolean = false) {
    val context = LocalContext.current
    remember { Cast.context(context) }
    val status by Cast.status.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    if (!status.available) {
        if (keepSpace) Spacer(Modifier.size(48.dp))
        return
    }
    IconButton(onClick = { open = true }) {
        Icon(
            if (status.device != null) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            if (status.device != null) "Playing on ${status.device}" else "Play on a TV or speaker",
            tint = when {
                status.device != null -> Accent
                status.connecting -> Accent.copy(alpha = 0.5f)
                else -> tint
            },
        )
    }
    if (open) CastSheet(onDismiss = { open = false })
}

private class CastTarget(val route: MediaRouter.RouteInfo, val name: String, val detail: String?, val selected: Boolean, val icon: ImageVector)

/** Lists the phone and every Cast device on the Wi-Fi; tapping one moves the music there. */
@Composable
private fun CastSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val router = remember { MediaRouter.getInstance(context) }
    val status by Cast.status.collectAsStateWithLifecycle()
    var targets by remember { mutableStateOf(emptyList<CastTarget>()) }

    // Searches for devices only while the sheet is open.
    DisposableEffect(router) {
        fun refresh() {
            targets = router.routes
                .filter { !it.isDefault && !it.isBluetooth && it.isEnabled && it.matchesSelector(Cast.selector) }
                .map { route ->
                    CastTarget(
                        route = route,
                        name = route.name,
                        detail = route.description?.takeIf { it.isNotBlank() },
                        selected = route.isSelected,
                        icon = if (route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER) Icons.Rounded.Speaker else Icons.Rounded.Tv,
                    )
                }
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        router.addCallback(Cast.selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Play on",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            TargetRow(Icons.Rounded.PhoneAndroid, "This phone", null, selected = status.device == null && !status.connecting) {
                if (status.device != null || status.connecting) Cast.disconnect()
                onDismiss()
            }
            targets.forEach { target ->
                val connecting = status.connecting && target.selected
                TargetRow(target.icon, target.name, if (connecting) "Connecting…" else target.detail, selected = target.selected && !connecting) {
                    if (!target.selected) target.route.select()
                    onDismiss()
                }
            }
            if (targets.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Looking for TVs and speakers…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Cast devices show up when they're on the same Wi-Fi as this phone. The TV plays straight from your Navidrome server, as MP3 320 kbit/s.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun TargetRow(icon: ImageVector, name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (selected) Accent else MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = Accent)
    }
}
