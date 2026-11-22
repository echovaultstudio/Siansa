package app.siansa.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.siansa.R

/**
 * Pull Home down to load it again (the owner, 22 Nov: "a pull down to refresh on home page, with an animated Siansa
 * logo and the header until refresh is done"): the page follows the finger, the Siansa logo grows into the space above
 * it, and while Home loads again the page stays down with the logo breathing in that space, until [refreshing] ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomePullToRefresh(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    val header = with(LocalDensity.current) { PULL_HEADER.toPx() }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().testTag("homePull"),
        state = state,
        indicator = { PullLogo(state, refreshing, Modifier.align(Alignment.TopCenter)) },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = header * pulledDown(refreshing, state.distanceFraction) }, content = content)
    }
}

/** How far down the page sits, as a share of the logo's space: with the finger while pulling, all of it while loading. */
internal fun pulledDown(refreshing: Boolean, fraction: Float): Float = if (refreshing) 1f else fraction.coerceIn(0f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullLogo(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    val fraction = pulledDown(refreshing, state.distanceFraction)
    if (fraction <= 0f) return
    // Breathing only while it loads, so a still Home has no endless animation (and tests settle).
    val breath = if (refreshing) {
        val t = rememberInfiniteTransition(label = "pull")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(BREATH_MS, easing = LinearEasing), RepeatMode.Reverse), label = "breath").value
    } else 0f
    Box(
        modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = Space.Small).size(PULL_HEADER)
            .semantics { contentDescription = if (refreshing) "Loading Home again" else "Pull to load Home again" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(PULL_LOGO).graphicsLayer {
                val grow = LOGO_SMALLEST + (1f - LOGO_SMALLEST) * fraction
                scaleX = grow * (1f + LOGO_BREATH * breath)
                scaleY = grow * (1f + LOGO_BREATH * breath)
                alpha = (fraction * 1.4f).coerceAtMost(1f) * (1f - LOGO_FADE * breath)
                // A turn as the finger pulls, a slow sway while it loads.
                rotationZ = if (refreshing) LOGO_SWAY * (breath * 2f - 1f) else 180f * (1f - fraction)
            },
        )
    }
}

private val PULL_HEADER = 72.dp
private val PULL_LOGO = 96.dp // the launcher drawing has room round it: its mark is two thirds of this
private const val LOGO_SMALLEST = 0.4f
private const val LOGO_BREATH = 0.12f
private const val LOGO_FADE = 0.25f
private const val LOGO_SWAY = 8f
private const val BREATH_MS = 700
