package app.siansa.pairing

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import app.siansa.AppGraph
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * The phone's side of signing in the TV app (issue #860): a `siansa://pair` link, opened from the page the TV's QR
 * code shows, asks once and then sends this phone's server and login to the TV, sealed with the key from the QR code.
 */
object PhonePairing {
    fun isPairing(intent: Intent?): Boolean =
        intent?.data?.let { it.scheme == Pairing.SCHEME && it.host == Pairing.HOST } == true

    /** Handles [intent] when it's a pairing link; does nothing otherwise. */
    fun handle(activity: Activity, graph: AppGraph, intent: Intent?) {
        if (!isPairing(intent)) return
        val raw = intent?.data?.toString()
        // Once only: coming back to the app mustn't ask again.
        intent?.data = null
        val link = Pairing.parse(raw)
        if (link == null) {
            AppLog.w("Pairing", "A pairing link that isn't for a TV on the home network was ignored")
            Toast.makeText(activity, "This code isn't from a Siansa TV on your home network.", Toast.LENGTH_LONG).show()
            return
        }
        val settings = graph.settings.current
        if (!settings.navidromeReady) {
            Toast.makeText(activity, "Sign in to your music server on this phone first.", Toast.LENGTH_LONG).show()
            return
        }
        val server = runCatching { URI(settings.navidromeUrl).host }.getOrNull() ?: settings.navidromeUrl
        val who = settings.navidromeUser.ifBlank { "your API key" }
        AlertDialog.Builder(activity)
            .setTitle("Sign in your TV?")
            .setMessage("Siansa on the TV at ${link.host} will play from $server as $who.")
            .setPositiveButton("Sign in") { _, _ ->
                graph.scope.launch {
                    val ok = send(graph.http, link, Pairing.Login.of(graph.settings.current))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity.applicationContext,
                            if (ok) "Your TV is signed in." else "Couldn't reach the TV. Check it's on the same Wi-Fi as this phone.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Sends [login] to the TV; true when the TV took it. */
    fun send(http: OkHttpClient, link: Pairing.Link, login: Pairing.Login): Boolean {
        val client = http.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
        val request = Request.Builder()
            .url("http://${link.tv}${Pairing.PATH}")
            .post(Pairing.seal(login, link.key).toRequestBody("text/plain".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                AppLog.i("Pairing", "The TV at ${link.tv} answered ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLog.w("Pairing", "Couldn't reach the TV at ${link.tv}: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
