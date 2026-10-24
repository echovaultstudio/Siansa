package app.siansa.net

/**
 * Subsonic / OpenSubsonic error codes as plain messages (issue #206), so a wrong password, a missing song and
 * a refused action don't all look alike. Navidrome's own message is kept for codes without one of these.
 */
object SubsonicErrors {
    fun message(code: Int?, serverMessage: String?): String = when (code) {
        10 -> "Navidrome said something was missing from the request" + detail(serverMessage)
        20 -> "Siansa is too old for this server. Update Siansa."
        30 -> "This server is too old for Siansa. Update Navidrome."
        40 -> "Wrong username or password for Navidrome."
        41, 42 -> "Navidrome doesn't accept this way of logging in for your user" + detail(serverMessage)
        43 -> "Siansa sent two kinds of login at once" + detail(serverMessage)
        44 -> "Navidrome doesn't know this API key."
        50 -> "Your Navidrome user isn't allowed to do this."
        70 -> "Not found on Navidrome. It may have been removed."
        else -> serverMessage?.takeIf { it.isNotBlank() } ?: "Navidrome refused the request"
    }

    private fun detail(message: String?) = message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."
}
