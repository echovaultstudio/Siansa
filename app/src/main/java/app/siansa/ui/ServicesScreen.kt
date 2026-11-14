package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.AppSettings
import app.siansa.data.Service
import app.siansa.log.AppLog
import app.siansa.net.AudiobookshelfApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

/** What a check of a service found: [ok] null while it hasn't been checked, and a line to show. */
data class ServiceCheck(val ok: Boolean?, val words: String, val at: Long = System.currentTimeMillis())

/**
 * The health of the music server and each service (the owner, 13 Nov): the dots on Settings and on Services. Checked
 * when either opens, at most once a minute, with the same calls as Connections' Test buttons; a service that's off or
 * not set up isn't asked. Kept in memory for the session.
 */
object ServiceHealth {
    const val SERVER = "server"
    private const val FRESH_MS = 60_000L
    private val _state = MutableStateFlow<Map<String, ServiceCheck>>(emptyMap())
    val state: StateFlow<Map<String, ServiceCheck>> = _state.asStateFlow()
    private val lock = Mutex()
    private var checkedAt = 0L

    /** Puts a result in (tests, and a fix made on the page). */
    fun set(id: String, check: ServiceCheck) { _state.value = _state.value + (id to check) }

    /**
     * Checks in the app's own scope, so leaving the page doesn't cut a check short (the owner, 13 Nov: a page left
     * while Beats was answering showed it red, "The coroutine scope left the composition", until the next minute).
     */
    fun refreshInBackground(graph: AppGraph, force: Boolean = false) {
        graph.scope.launch(CoroutineName("service-health")) { refresh(graph, force) }
    }

    suspend fun refresh(graph: AppGraph, force: Boolean = false) {
        lock.withLock {
            if (!force && System.currentTimeMillis() - checkedAt < FRESH_MS) return
            checkedAt = System.currentTimeMillis()
            val s = graph.settings.current
            check(SERVER) { testServer(graph, s, s.sourceKind) }
            for (service in Service.entries) {
                if (!Service.on(s, service) || !Service.setUp(s, service)) { _state.value = _state.value - service.id; continue }
                when (service) {
                    Service.BEATS -> check(service.id) { testBeats(graph, s) }
                    Service.AUDIOMUSE -> check(service.id) { testAudioMuse(graph, s) }
                    Service.BOOKS -> check(service.id) { "Connected to " + AudiobookshelfApi(graph.http) { s }.ping() }
                    // No request: whether the sign-in is there, and what's waiting to be sent.
                    Service.LASTFM -> set(service.id, lastFmCheck(s, graph.scrobbler.pendingCount()))
                    Service.LISTENBRAINZ -> set(service.id, ServiceCheck(true, "Set up" + waiting(graph.scrobbler.pendingCount())))
                    Service.AI -> set(service.id, ServiceCheck(true, "Set up · ${s.aiProvider.label}"))
                }
            }
        }
    }

    internal suspend fun check(id: String, block: suspend () -> String) {
        val result = try {
            ServiceCheck(true, withTimeout(8_000) { block() })
        } catch (e: CancellationException) {
            throw e  // the check was called off, not failed: nothing to show
        } catch (e: Exception) {
            AppLog.w("Services", "$id doesn't answer: ${e.message}")
            ServiceCheck(false, e.message?.takeIf { it.isNotBlank() } ?: "Doesn't answer")
        }
        set(id, result)
    }

    internal fun lastFmCheck(s: AppSettings, pending: Int): ServiceCheck =
        if (s.lastFmReady) ServiceCheck(true, "Signed in as ${s.lastFmUser}" + waiting(pending))
        else ServiceCheck(false, "Not signed in" + waiting(pending))

    private fun waiting(pending: Int) = if (pending > 0) " · $pending ${if (pending == 1) "listen" else "listens"} waiting" else ""
}

/** A line of "For the best Siansa": its mark (ok, warn, bad, off, dash), the words, and a service the line offers to switch off. */
data class BestLine(val mark: String, val words: String, val turnOff: Service? = null)

/**
 * What to look at for the best Siansa (Settings › Services): the server, each service with what it gives, what's not
 * needed (AudioMuse with Siansa Beats on) and what's broken. [health] is what [ServiceHealth] found.
 */
internal fun bestLines(s: AppSettings, health: Map<String, ServiceCheck>): List<BestLine> {
    val out = mutableListOf<BestLine>()
    val server = health[ServiceHealth.SERVER]
    out += BestLine(if (server?.ok == false) "bad" else "ok", "Music server · " + (server?.words ?: "${s.sourceKind.label} at ${AppLog.redact(s.navidromeUrl)}"))
    fun line(service: Service, whenOn: String): BestLine {
        val h = health[service.id]
        return when {
            !Service.on(s, service) -> BestLine("off", "${service.label} · off")
            !Service.setUp(s, service) -> BestLine("off", "${service.label} · not set up")
            h?.ok == false -> BestLine("bad", "${service.label} · ${h.words}")
            else -> BestLine("ok", "${service.label} · $whenOn")
        }
    }
    out += line(Service.BEATS, "beats, loudness, keys, genres, similar music and your listening, all from one place")
    val beatsOn = Service.on(s, Service.BEATS) && Service.setUp(s, Service.BEATS)
    val audioMuseOn = Service.on(s, Service.AUDIOMUSE) && Service.setUp(s, Service.AUDIOMUSE)
    out += when {
        beatsOn && audioMuseOn -> BestLine("warn", "AudioMuse-AI · not needed: Siansa Beats covers everything it did. You can turn it off.", Service.AUDIOMUSE)
        beatsOn -> BestLine("dash", "AudioMuse-AI · not needed: Siansa Beats covers everything it did")
        audioMuseOn -> line(Service.AUDIOMUSE, "the sound of each song, similar music and the search by words")
        else -> BestLine("bad", "Neither Siansa Beats nor AudioMuse-AI is on: no DJ blends, similar music or search by words")
    }
    out += line(Service.BOOKS, "audiobooks and podcasts")
    out += line(Service.LASTFM, "scrobbling")
    out += line(Service.LISTENBRAINZ, "sending listens")
    out += line(Service.AI, "mix titles and sound searches in your own words")
    return out
}

/**
 * The green of "working", in a shade that can be seen on [background]: the light green of the dark themes is
 * 1.7 to 1 on a light page, so a light page gets a dark green (5 to 1).
 */
internal fun okGreen(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1E7A46) else Color(0xFF7FD1A0)

/** A dot: green when a check passed, red when it failed, grey while unchecked or off. */
@Composable
internal fun ServiceDot(ok: Boolean?, modifier: Modifier = Modifier) {
    val colour = when (ok) { true -> okGreen(MaterialTheme.colorScheme.background); false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.outline }
    Box(modifier.size(8.dp).background(colour, CircleShape))  // spacing: exact
}

/**
 * Settings › Services (the owner, 13 Nov): what Siansa talks to besides the music server, each with its switch,
 * what it gives, and whether it answers; and "For the best Siansa" at the top, which says what's not needed and
 * what's broken. A service switched off is left alone and shown nowhere.
 */
@Composable
fun ServicesScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val health by ServiceHealth.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ServiceHealth.refreshInBackground(graph) }
    Screen(title = "Services", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Hint("What Siansa talks to besides your music server. A service turned off is left alone and shown nowhere.")
            SettingsCard(tag = "bestSiansa") {
                HubGroupLabel("FOR THE BEST SIANSA")
                bestLines(settings, health).forEach { line ->
                    Row(Modifier.fillMaxWidth().testTag("best-${line.mark}"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                        // The marks differ in width: each in a column of its own, so the words line up.
                        Box(Modifier.width(Space.Section), contentAlignment = Alignment.Center) {
                            Text(
                                when (line.mark) { "ok" -> "✓"; "bad" -> "✕"; "warn" -> "!"; "dash" -> "–"; else -> "○" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (line.mark) { "ok" -> okGreen(MaterialTheme.colorScheme.background); "bad" -> MaterialTheme.colorScheme.error; "warn" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(line.words, style = MaterialTheme.typography.bodyMedium)
                            if (line.turnOff != null) {
                                TextButton(onClick = {
                                    AppLog.i("Settings", "Services: ${line.turnOff.label} switched off from For the best Siansa")
                                    graph.settings.setServiceOn(line.turnOff, false)
                                }, modifier = Modifier.testTag("turnOff-${line.turnOff.id}")) { Text("Turn ${line.turnOff.label} off") }
                            }
                        }
                    }
                }
            }
            Service.entries.forEach { service ->
                if (service == Service.BOOKS && app.siansa.BuildConfig.TV_APP) return@forEach  // the TV has no books (issue #860)
                ServiceCard(graph, settings, service, health[service.id], nav)
            }
        }
    }
}

@Composable
private fun ServiceCard(graph: AppGraph, s: AppSettings, service: Service, check: ServiceCheck?, nav: NavController) {
    val on = Service.on(s, service)
    val setUp = Service.setUp(s, service)
    SettingsCard(tag = "service-${service.id}", modifier = Modifier.alpha(if (on) 1f else 0.7f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(service.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    AppLog.i("Settings", "Services: ${service.label} ${if (want) "on" else "off"}")
                    when (service) {
                        Service.LASTFM -> graph.settings.save(graph.settings.current.copy(lastFmEnabled = want))
                        Service.LISTENBRAINZ -> graph.settings.save(graph.settings.current.copy(listenBrainzEnabled = want))
                        else -> graph.settings.setServiceOn(service, want)
                    }
                },
                modifier = Modifier.testTag("serviceSwitch-${service.id}"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            ServiceDot(if (!on || !setUp) null else check?.ok)
            Text(
                when {
                    !on -> "Off" + if (service == Service.AUDIOMUSE && s.beatsReady) " · Siansa Beats covers it" else ""
                    !setUp -> "Not set up"
                    check == null -> "Checking…"
                    else -> check.words
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (on && setUp && check?.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("serviceStatus-${service.id}"),
            )
        }
        Hint(service.gives)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
            TextButton(onClick = {
                if (service.connection == "scrobbling") nav.navigate("settings/scrobbling") else nav.navigate("settings/connections/${service.connection}")
            }) { Text(if (setUp) "Set up" else "Set it up") }
        }
    }
}

/** A card on a settings page: the page's background lifted a step, the page margin inside, its rows a step apart. */
@Composable
internal fun SettingsCard(tag: String, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium), content = content)
    }
}

/** A small amber label over a group of things ("SERVICES", "PLAYING"). */
@Composable
internal fun HubGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
