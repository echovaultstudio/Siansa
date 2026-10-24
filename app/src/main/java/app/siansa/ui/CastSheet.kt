package app.siansa.ui

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.media.MediaRouter
import app.siansa.playback.Cast
import app.siansa.playback.AudioOutput
import app.siansa.playback.CastStatus
import app.siansa.playback.OutputDevice
import app.siansa.playback.dlna.Dlna

/**
 * The cast button: picks a TV or speaker to play on (Chromecast, or DLNA, which needs no Google Play services).
 */
@Composable
fun CastButton(tint: Color = LocalContentColor.current) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Cast.whenReady(context) { } }
    val status by Cast.status.collectAsStateWithLifecycle()
    val dlna by Dlna.selected.collectAsStateWithLifecycle()
    val dlnaConnecting by Dlna.connecting.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    // DLNA works without Google Play services, so the button is always there.
    val device = dlna?.name ?: status.device
    val connecting = dlnaConnecting || status.connecting
    IconButton(onClick = { open = true }) {
        Icon(
            if (device != null) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            if (device != null) "Playing on $device" else "Play on a TV or speaker",
            tint = when {
                connecting -> Accent.copy(alpha = 0.5f)
                device != null -> Accent
                else -> tint
            },
        )
    }
    if (open) CastSheet(onDismiss = { open = false })
}

/**
 * The text next to the device button in now playing: the TV or speaker's name while casting,
 * "Connecting…" while it connects, the Bluetooth or wired headphones the phone plays through,
 * and nothing on the phone's own speaker.
 */
fun castDeviceLabel(status: CastStatus, output: OutputDevice? = null, dlna: String? = null, dlnaConnecting: Boolean = false): String? = when {
    dlnaConnecting -> "Connecting…"
    dlna != null -> dlna
    status.device != null -> status.device
    status.connecting -> "Connecting…"
    output != null -> output.name
    else -> null
}

/**
 * Now playing's device button, bottom left as in Spotify: an icon for where the music plays, followed by
 * the device's name when it isn't this phone's speaker ("Living Room TV", "Sony Headphones"). Tapping it
 * picks a TV or speaker to cast to, on phones that can cast.
 */
@Composable
fun CastDeviceButton(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Cast.whenReady(context) { }
        AudioOutput.start(context)
    }
    val status by Cast.status.collectAsStateWithLifecycle()
    val output by AudioOutput.device.collectAsStateWithLifecycle()
    val dlna by Dlna.selected.collectAsStateWithLifecycle()
    val dlnaConnecting by Dlna.connecting.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val label = castDeviceLabel(status, output, dlna?.name, dlnaConnecting)
    val remote = dlna != null || status.device != null
    val connecting = dlnaConnecting || status.connecting
    val color = when {
        connecting -> Accent.copy(alpha = 0.6f)
        remote -> Accent
        output != null -> Accent
        else -> tint
    }
    val icon = when {
        connecting -> Icons.Rounded.Cast
        remote -> Icons.Rounded.CastConnected
        output?.bluetooth == true -> Icons.Rounded.BluetoothAudio
        output != null -> Icons.Rounded.Headphones
        else -> Icons.Rounded.Cast
    }
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Choose where to play") { open = true }
            .padding(Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (label == null) "Play on a TV or speaker" else null, tint = color, modifier = Modifier.size(22.dp))
        if (label != null) {
            Spacer(Modifier.width(Space.Small))
            FittedText(label, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color, Modifier.testTag("castDeviceName"))
        }
    }
    if (open) CastSheet(onDismiss = { open = false })
}

private class CastTarget(val route: MediaRouter.RouteInfo, val name: String, val detail: String?, val selected: Boolean, val icon: ImageVector)

/**
 * Lists the phone, every Cast device and every DLNA TV or speaker on the Wi-Fi; tapping one moves the
 * music there.
 */
@Composable
private fun CastSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val router = remember { MediaRouter.getInstance(context) }
    val status by Cast.status.collectAsStateWithLifecycle()
    var targets by remember { mutableStateOf(emptyList<CastTarget>()) }
    val dlnaDevices by Dlna.devices.collectAsStateWithLifecycle()
    val dlna by Dlna.selected.collectAsStateWithLifecycle()
    val dlnaConnecting by Dlna.connecting.collectAsStateWithLifecycle()

    // DLNA devices are searched for only while the sheet is open.
    DisposableEffect(Unit) {
        Dlna.startSearch(context)
        onDispose { Dlna.stopSearch() }
    }

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
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.Page)) {
            Text(
                "Play on",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Space.Section, vertical = Space.Small),
            )
            val onPhone = status.device == null && !status.connecting && dlna == null
            TargetRow(Icons.Rounded.PhoneAndroid, "This phone", null, selected = onPhone) {
                if (status.device != null || status.connecting) Cast.disconnect()
                Dlna.select(null)
                onDismiss()
            }
            targets.forEach { target ->
                val connecting = status.connecting && target.selected
                TargetRow(target.icon, target.name, if (connecting) "Connecting…" else target.detail ?: "Chromecast", selected = target.selected && !connecting && dlna == null) {
                    if (!target.selected) target.route.select()
                    onDismiss()
                }
            }
            // DLNA names can repeat a Chromecast's (a TV with both): those show once, as Chromecast.
            val castNames = targets.map { it.name.lowercase() }.toSet()
            dlnaDevices.filter { it.name.lowercase() !in castNames }.forEach { device ->
                val selected = dlna?.id == device.id
                TargetRow(
                    Icons.Rounded.Speaker,
                    device.name,
                    if (selected && dlnaConnecting) "Connecting…" else listOfNotNull("DLNA", device.model).joinToString(" · "),
                    selected = selected && !dlnaConnecting,
                ) {
                    if (!selected) {
                        if (status.device != null || status.connecting) Cast.disconnect()
                        Dlna.select(device)
                    }
                    onDismiss()
                }
            }
            if (targets.isEmpty() && dlnaDevices.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.Section, vertical = Space.Page), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.Page))
                    Text("Looking for TVs and speakers…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "TVs and speakers show up when they're on the same Wi-Fi as this phone: Chromecast and Google TV, and DLNA ones (most smart TVs, Sonos, AV receivers). They play straight from your Navidrome server, as MP3 320 kbit/s.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.Section, vertical = Space.Medium),
            )
        }
    }
}

@Composable
private fun TargetRow(icon: ImageVector, name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Section, vertical = Space.Page),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Page),
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

/**
 * One line of text that gets smaller, down to [minScale] of its size, until it fits; only then is it cut off with
 * "…" (issue #311: "JBL Bar 500" was cut to "JBL BA…").
 */
@Composable
internal fun FittedText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier, minScale: Float = 0.7f) {
    var scale by remember(text) { mutableStateOf(1f) }
    var fitted by remember(text) { mutableStateOf(false) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (fitted) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { layout ->
            if (fitted) return@Text
            when {
                !layout.hasVisualOverflow -> fitted = true
                scale > minScale -> scale = nextFitScale(scale, minScale)
                else -> fitted = true
            }
        },
        // Drawn only once it fits, so the steps in between don't flicker.
        modifier = modifier.drawWithContent { if (fitted) drawContent() },
    )
}

/** The next, smaller size to try: 8% smaller, never below [minScale]. */
internal fun nextFitScale(scale: Float, minScale: Float): Float = (scale * 0.92f).coerceAtLeast(minScale)
