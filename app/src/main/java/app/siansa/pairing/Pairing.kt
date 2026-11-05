package app.siansa.pairing

import app.siansa.data.AiProvider
import app.siansa.data.AppSettings
import app.siansa.net.LanAddress
import app.siansa.net.SourceKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Signing in the TV app from the phone (issue #860). The TV shows a QR code with its address on the home network and
 * a one-time key; the phone's Siansa sends it the server and login, sealed with that key (AES-GCM), so nothing on the
 * network can read or change it. The key only ever travels in the QR code, on screen.
 *
 * The QR code opens a small page on the TV, `http://<tv>:<port>/#<key>`, in the phone's browser. The key is after the
 * `#`, which browsers never send, and the page's button opens `siansa://pair?tv=<tv>:<port>&k=<key>` in the app.
 */
object Pairing {
    const val SCHEME = "siansa"
    const val HOST = "pair"
    /** Where the TV takes the sealed login. */
    const val PATH = "/pair"
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    private val json = Json { ignoreUnknownKeys = true }
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    /**
     * The server and login a phone sends; only what the TV needs to play from it, and, when the phone's owner ticks it,
     * the phone's other services too ([services]).
     */
    @Serializable
    data class Login(
        val source: String = SourceKind.OPENSUBSONIC.name,
        val url: String,
        val user: String = "",
        val password: String = "",
        val apiKey: String = "",
        val proxyUser: String = "",
        val proxyPassword: String = "",
        val services: Services? = null,
    ) {
        /** [settings] with this login in them, and the services that came with it. */
        fun into(settings: AppSettings): AppSettings = settings.copy(
            sourceKind = SourceKind.parse(source),
            navidromeUrl = url,
            navidromeUser = user,
            navidromePassword = password,
            navidromeApiKey = apiKey,
            proxyUser = proxyUser,
            proxyPassword = proxyPassword,
        ).let { services?.into(it) ?: it }

        companion object {
            fun of(s: AppSettings, withServices: Boolean = false) = Login(
                source = s.sourceKind.name,
                url = s.navidromeUrl,
                user = s.navidromeUser,
                password = s.navidromePassword,
                apiKey = s.navidromeApiKey,
                proxyUser = s.proxyUser,
                proxyPassword = s.proxyPassword,
                services = if (withServices) Services.of(s) else null,
            )
        }
    }

    /**
     * The phone's other services, sent with the login when asked (issue #860): AudioMuse, the AI service, Audiobookshelf
     * and scrobbling (Navidrome's, Last.fm and ListenBrainz). Only those set up on the phone are sent; on the TV each
     * one sent replaces its own, and the rest stay as they were.
     */
    @Serializable
    data class Services(
        val audioMuseUrl: String = "",
        val audioMuseToken: String = "",
        val audioMuseUser: String = "",
        val audioMusePassword: String = "",
        val audioMuseServer: String = "",
        val aiProvider: String = "",
        val aiKey: String = "",
        val aiModel: String = "",
        val aiAddress: String = "",
        val aiTitles: Boolean = true,
        val audiobookshelfUrl: String = "",
        val audiobookshelfToken: String = "",
        val audiobookshelfUser: String = "",
        val audiobookshelfPassword: String = "",
        val scrobbleNavidrome: Boolean = true,
        val scrobblePercent: Int = 50,
        val lastFmApiKey: String = "",
        val lastFmSecret: String = "",
        val lastFmUser: String = "",
        val lastFmSession: String = "",
        val listenBrainzUrl: String = "",
        val listenBrainzToken: String = "",
    ) {
        /** The services' names, as the phone lists what it would send ("AudioMuse, Last.fm"). */
        val names: List<String>
            get() = listOfNotNull(
                "AudioMuse".takeIf { audioMuseUrl.isNotBlank() },
                "the AI service".takeIf { aiProvider.isNotBlank() },
                "Audiobookshelf".takeIf { audiobookshelfUrl.isNotBlank() },
                "Last.fm".takeIf { lastFmSession.isNotBlank() },
                "ListenBrainz".takeIf { listenBrainzToken.isNotBlank() },
            )

        fun into(s: AppSettings): AppSettings {
            var out = s.copy(scrobbleNavidrome = scrobbleNavidrome, scrobblePercent = scrobblePercent)
            if (audioMuseUrl.isNotBlank()) {
                out = out.copy(
                    audioMuseUrl = audioMuseUrl, audioMuseToken = audioMuseToken, audioMuseUser = audioMuseUser,
                    audioMusePassword = audioMusePassword, audioMuseServer = audioMuseServer,
                )
            }
            if (aiProvider.isNotBlank()) {
                out = out.copy(
                    aiProvider = AiProvider.parse(aiProvider), aiKey = aiKey, aiModel = aiModel.ifBlank { out.aiModel },
                    aiAddress = aiAddress, aiTitles = aiTitles,
                )
            }
            if (audiobookshelfUrl.isNotBlank()) {
                out = out.copy(
                    audiobookshelfUrl = audiobookshelfUrl, audiobookshelfToken = audiobookshelfToken,
                    audiobookshelfUser = audiobookshelfUser, audiobookshelfPassword = audiobookshelfPassword,
                )
            }
            if (lastFmSession.isNotBlank()) {
                out = out.copy(lastFmEnabled = true, lastFmApiKey = lastFmApiKey, lastFmSecret = lastFmSecret, lastFmUser = lastFmUser, lastFmSession = lastFmSession)
            }
            if (listenBrainzToken.isNotBlank()) {
                out = out.copy(listenBrainzEnabled = true, listenBrainzUrl = listenBrainzUrl, listenBrainzToken = listenBrainzToken)
            }
            return out
        }

        companion object {
            fun of(s: AppSettings): Services {
                val ai = s.aiKey.isNotBlank() || s.aiAddress.isNotBlank()
                return Services(
                    audioMuseUrl = s.audioMuseUrl, audioMuseToken = s.audioMuseToken, audioMuseUser = s.audioMuseUser,
                    audioMusePassword = s.audioMusePassword, audioMuseServer = s.audioMuseServer,
                    aiProvider = if (ai) s.aiProvider.name else "",
                    aiKey = if (ai) s.aiKey else "", aiModel = if (ai) s.aiModel else "", aiAddress = if (ai) s.aiAddress else "",
                    aiTitles = s.aiTitles,
                    audiobookshelfUrl = s.audiobookshelfUrl, audiobookshelfToken = s.audiobookshelfToken,
                    audiobookshelfUser = s.audiobookshelfUser, audiobookshelfPassword = s.audiobookshelfPassword,
                    scrobbleNavidrome = s.scrobbleNavidrome, scrobblePercent = s.scrobblePercent,
                    // Only a scrobbling service that's switched on and signed in goes.
                    lastFmApiKey = if (s.lastFmReady) s.lastFmApiKey else "", lastFmSecret = if (s.lastFmReady) s.lastFmSecret else "",
                    lastFmUser = if (s.lastFmReady) s.lastFmUser else "", lastFmSession = if (s.lastFmReady) s.lastFmSession else "",
                    listenBrainzUrl = if (s.listenBrainzReady) s.listenBrainzUrl else "",
                    listenBrainzToken = if (s.listenBrainzReady) s.listenBrainzToken else "",
                )
            }
        }
    }

    /** What a pairing link says: the TV's address ("192.168.1.20:8123") and the key. */
    data class Link(val tv: String, val key: ByteArray) {
        val host: String get() = tv.substringBeforeLast(':')
        val port: Int? get() = tv.substringAfterLast(':', "").toIntOrNull()
        override fun equals(other: Any?) = other is Link && other.tv == tv && other.key.contentEquals(key)
        override fun hashCode() = tv.hashCode() * 31 + key.contentHashCode()
    }

    fun newKey(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    fun encodeKey(key: ByteArray): String = b64.encodeToString(key)

    /** The page the QR code opens on the TV: the key after the `#`, so the browser keeps it to itself. */
    fun pageUrl(address: String, port: Int, key: ByteArray): String = "http://$address:$port/#${encodeKey(key)}"

    /** The link that opens the phone's Siansa. */
    fun appLink(tv: String, key: ByteArray): String =
        "$SCHEME://$HOST?tv=${URLEncoder.encode(tv, "UTF-8")}&k=${encodeKey(key)}"

    /**
     * Reads a pairing link, or null when it isn't one or points anywhere but a home-network address: a QR code from
     * somewhere else mustn't be able to collect a login.
     */
    fun parse(link: String?): Link? {
        val uri = runCatching { URI(link ?: return null) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        val params = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val name = part.substringBefore('=', "")
            if (name.isEmpty()) null else name to URLDecoder.decode(part.substringAfter('='), "UTF-8")
        }.toMap()
        val tv = params["tv"]?.trim().orEmpty()
        val key = runCatching { unb64.decode(params["k"].orEmpty()) }.getOrNull() ?: return null
        if (key.size != KEY_BYTES) return null
        val port = tv.substringAfterLast(':', "").toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        val ip = LanAddress.ipv4(tv.substringBeforeLast(':')) ?: return null
        if (!LanAddress.usableAddress(ip) || !LanAddress.private(ip)) return null
        return Link(tv, key)
    }

    /** [login] sealed with [key]: nonce then ciphertext, as base64. */
    fun seal(login: Login, key: ByteArray, random: SecureRandom = SecureRandom()): String {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(json.encodeToString(Login.serializer(), login).toByteArray(Charsets.UTF_8))
        return b64.encodeToString(nonce + sealed)
    }

    /** The login in [message], or null when it wasn't sealed with [key] (or was changed on the way). */
    fun open(message: String, key: ByteArray): Login? = runCatching {
        val bytes = unb64.decode(message.trim())
        if (bytes.size <= NONCE_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, NONCE_BYTES)))
        val plain = cipher.doFinal(bytes.copyOfRange(NONCE_BYTES, bytes.size))
        json.decodeFromString(Login.serializer(), String(plain, Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.url.isNotBlank() }
}
