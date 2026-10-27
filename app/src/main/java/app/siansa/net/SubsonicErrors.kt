package app.siansa.net

/**
 * Subsonic / OpenSubsonic error codes as plain messages (issue #206), so a wrong password, a missing song and
 * a refused action don't all look alike. The server's own message is kept for codes without one of these. They say
 * "the server", not Navidrome: an OpenSubsonic server may be Gonic, Airsonic or another (#573).
 */
object SubsonicErrors {
    fun message(code: Int?, serverMessage: String?): String = when (code) {
        10 -> "The server said something was missing from the request" + detail(serverMessage)
        20 -> "Siansa is too old for this server. Update Siansa."
        30 -> "This server is too old for Siansa. Update it."
        40 -> "Wrong username or password for your server."
        41, 42 -> "Your server doesn't accept this way of logging in for your user" + detail(serverMessage)
        43 -> "Siansa sent two kinds of login at once" + detail(serverMessage)
        44 -> "Your server doesn't know this API key."
        50 -> "Your user isn't allowed to do this on the server."
        70 -> "Not found on your server. It may have been removed."
        else -> serverMessage?.takeIf { it.isNotBlank() } ?: "Your server refused the request"
    }

    private fun detail(message: String?) = message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."
}
