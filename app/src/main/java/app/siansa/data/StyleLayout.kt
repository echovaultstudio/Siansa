package app.siansa.data

/**
 * The buttons the mini player can show (issue #346), in the order a new install shows the first three. The
 * rest are extras a user can add in Settings › Style › Mini Player.
 */
enum class MiniButton(val id: String, val label: String) {
    PLAY("play", "Play or pause"),
    CAST("cast", "Cast"),
    NEXT("next", "Next song"),
    LYRICS("lyrics", "Lyrics"),
    ADD("add", "Add to playlist"),
    LIKE("like", "Like");

    companion object {
        /** A fresh install's (the owner, 0.52.0-dev): play or pause, next song, add to playlist. Cast is on Home. */
        val default: List<MiniButton> = listOf(PLAY, NEXT, ADD)

        /** At most this many, so the song's title keeps room beside them. */
        const val MAX = 4

        /** Reads a saved list ("play,next"): unknown ids dropped, each once, at most [MAX]. Empty is allowed. */
        fun parse(saved: String?): List<MiniButton> {
            if (saved == null) return default
            return saved.split(',').mapNotNull { id -> entries.firstOrNull { it.id == id.trim() } }.distinct().take(MAX)
        }

        fun format(buttons: List<MiniButton>): String = buttons.joinToString(",") { it.id }

        /** [shown] with [button] added at the end or taken out; adding past [MAX] changes nothing. */
        fun toggle(shown: List<MiniButton>, button: MiniButton): List<MiniButton> = when {
            button in shown -> shown - button
            shown.size >= MAX -> shown
            else -> shown + button
        }
    }
}

/** The tabs the bottom bar can show (issue #346), in their default order. */
enum class NavTab(val route: String, val label: String) {
    HOME("home", "Home"),
    SEARCH("search", "Search"),
    LIBRARY("library", "Your Library"),
    // Between Your Library and Settings by default (issue #339); called Flow since #369. The route stays
    // "dscvr" so saved nav bar orders keep it.
    DSCVR("dscvr", "Flow"),
    SETTINGS("settings", "Settings");

    companion object {
        val default: List<NavTab> = entries.toList()

        /** At most this many tabs fit the bar. */
        const val MAX = 5

        /** Settings can't be hidden: it's the only way back to these choices. */
        val required: Set<NavTab> = setOf(SETTINGS)

        /**
         * Reads a saved order of shown tabs ("home,dscvr,…"): unknown ids dropped, each once, at most [MAX],
         * and Settings put back at the end if it's missing. Nothing saved is the default bar.
         */
        fun parse(saved: String?): List<NavTab> {
            if (saved.isNullOrBlank()) return default
            val shown = saved.split(',').mapNotNull { id -> entries.firstOrNull { it.route == id.trim() } }.distinct()
            val withRequired = shown + required.filter { it !in shown }
            // Never more than fit: drop the last optional tabs first.
            var out = withRequired
            while (out.size > MAX) out = out - out.last { it !in required }
            return out
        }

        fun format(tabs: List<NavTab>): String = tabs.joinToString(",") { it.route }

        /**
         * [shown] with [tab] taken out, or put back after the tab it usually follows; Settings stays, and adding
         * past [MAX] changes nothing.
         */
        fun toggle(shown: List<NavTab>, tab: NavTab): List<NavTab> {
            if (tab in required) return shown
            if (tab in shown) return shown - tab
            if (shown.size >= MAX) return shown
            val before = entries.subList(0, tab.ordinal).lastOrNull { it in shown }
            val at = if (before == null) 0 else shown.indexOf(before) + 1
            return shown.toMutableList().apply { add(at, tab) }
        }
    }
}

/**
 * The order an arrange list shows (issue #372): the [shown] items in their order on the bar, then the rest of
 * [all] (hidden) in their usual order, so a hidden item can be shown again and dragged into place.
 */
fun <T> arrangeOrder(shown: List<T>, all: List<T>): List<T> = shown.distinct() + all.filter { it !in shown }

/** What an arrange list saves: the items in [order] that are [shown], in that order. */
fun <T> shownInOrder(order: List<T>, shown: Set<T>): List<T> = order.filter { it in shown }

/**
 * The options in Home's menu (issue #872), top to bottom as a new install shows them. Their order is set in
 * Settings › Style › Home Menu; all four always show, since each is the only way to it from Home.
 */
enum class HomeMenuItem(val id: String, val label: String) {
    DICE("dice", "Roll the dice"),
    PLAY_ON("play_on", "Play on…"),
    SOURCES("sources", "Sources"),
    ARRANGE("arrange", "Arrange Home");

    companion object {
        val default: List<HomeMenuItem> = entries.toList()

        /**
         * Reads a saved order ("sources,dice,…"): unknown ids dropped, each once, and any option the saved order
         * doesn't have (new in a later version) put back where it is by default, so none can go missing.
         */
        fun parse(saved: String?): List<HomeMenuItem> {
            if (saved.isNullOrBlank()) return default
            val order = saved.split(',').mapNotNull { id -> entries.firstOrNull { it.id == id.trim() } }.distinct().toMutableList()
            for (item in entries) {
                if (item in order) continue
                order.add(item.ordinal.coerceAtMost(order.size), item)
            }
            return order
        }

        fun format(order: List<HomeMenuItem>): String = order.joinToString(",") { it.id }
    }
}
