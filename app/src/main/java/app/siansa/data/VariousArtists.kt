package app.siansa.data

/** Album artist names that mean a compilation, normalised (issue #504): Navidrome's and Emby's own, and common ones. */
private val VARIOUS = setOf(
    "various artists", "various", "va", "v a", "varios artistas", "verschiedene interpreten", "verschiedene kunstler",
    "artistes divers", "artisti vari", "diverse artiesten", "vari artisti",
)

/** Whether an album artist name says the album is a compilation ("Various Artists", "VA"…). */
fun isVariousArtists(name: String): Boolean = normalizeKey(name) in VARIOUS
