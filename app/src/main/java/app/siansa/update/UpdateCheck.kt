package app.siansa.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.siansa.BuildConfig
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Whether a newer Siansa is out (the owner, 14 Nov: "prompt users when an update is ready, so it reaches them as soon as
 * it's published"). Once a day the app reads the list of Siansa's F-Droid repo on siansa.app, which every release
 * updates at the same time as it goes to Google Play: nothing about the phone or the person is sent. A newer version
 * shows as a notice at the top of Home ("Update ready"), which opens the store the app came from.
 *
 * Only the app people install: not Siansa Dev (an app of its own) or the TV app (its store updates it).
 */
object UpdateCheck {
    const val INDEX = "https://siansa.app/fdroid/repo/index-v1.json"
    private const val DAY_MS = 24 * 60 * 60_000L
    private const val PREFS = "update_check"

    /** Whether this build looks for updates. */
    val applies: Boolean get() = BuildConfig.BUILD_TYPE == "release" && !BuildConfig.TV_APP

    /**
     * The newer version that's out, or null: asks siansa.app at most once a day (the answer is kept between), and
     * never throws. [installed] is this app's version.
     */
    suspend fun newer(context: Context, http: OkHttpClient, installed: String = BuildConfig.VERSION_NAME): String? {
        if (!applies) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var latest = prefs.getString("latest", null)
        if (now - prefs.getLong("checked", 0L) > DAY_MS) {
            val etag = prefs.getString("etag", null)
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(INDEX).apply { etag?.let { header("If-None-Match", it) } }.build()
                    http.newCall(request).execute().use { response ->
                        when {
                            response.code == 304 -> Unit
                            response.isSuccessful -> {
                                latest = latestIn(response.body?.string().orEmpty(), context.packageName) ?: latest
                                prefs.edit().putString("etag", response.header("ETag")).putString("latest", latest).apply()
                            }
                            else -> AppLog.d("Update", "Update check: HTTP ${response.code}")
                        }
                    }
                    prefs.edit().putLong("checked", now).apply()
                    AppLog.i("Update", "Update check: this is $installed, the newest is ${latest ?: "unknown"}")
                }.onFailure { AppLog.d("Update", "Update check failed: ${it.message}") }
            }
        }
        return latest?.takeIf { isNewer(it, installed) }
    }

    /** The highest version of [pkg] in an F-Droid repo's index-v1.json, or null. */
    fun latestIn(index: String, pkg: String): String? = runCatching {
        val root = app.siansa.data.AppJson.parseToJsonElement(index) as? JsonObject ?: return null
        val builds = (root["packages"] as? JsonObject)?.get(pkg) as? JsonArray ?: return null
        builds.mapNotNull { ((it as? JsonObject)?.get("versionName") as? JsonPrimitive)?.content }
            .filter { parts(it) != null }
            .maxWithOrNull { a, b -> compare(a, b) }
    }.getOrNull()

    /** Whether [latest] is a higher version than [installed] (1.5.0 over 1.4.2; a "-dev" or "beta" ending is left aside). */
    fun isNewer(latest: String, installed: String): Boolean {
        if (parts(latest) == null || parts(installed) == null) return false
        return compare(latest, installed) > 0
    }

    private fun parts(v: String): List<Int>? =
        Regex("""^(\d+)\.(\d+)\.(\d+)""").find(v.trim())?.groupValues?.drop(1)?.map { it.toInt() }

    private fun compare(a: String, b: String): Int {
        val x = parts(a) ?: return -1
        val y = parts(b) ?: return 1
        for (i in 0 until 3) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return 0
    }

    /**
     * Opens the app's page in the store it was installed from (Google Play, or the F-Droid app, which both open
     * `market://` links); without one, Siansa's download page.
     */
    fun openStore(context: Context) {
        val pkg = context.packageName
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.packageManager.getInstallSourceInfo(pkg).installingPackageName
            else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(pkg)
        }.getOrNull()
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tries = listOfNotNull(
            installer?.let { Intent(market).setPackage(it) },
            market,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://siansa.app/#get")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        for (intent in tries) {
            try {
                context.startActivity(intent)
                AppLog.i("Update", "Opened ${intent.`package` ?: intent.data} for the update")
                return
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
    }
}
