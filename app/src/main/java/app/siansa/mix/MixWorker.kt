package app.siansa.mix

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.siansa.SiansaApp
import java.util.concurrent.TimeUnit

/** Prepares the current time slot's mix and today's personal mixes in the background, so it's ready when the app opens. */
class MixWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as SiansaApp).graph
        val settings = graph.settings.current
        if (!settings.navidromeReady || !settings.audioMuseReady) return Result.success()
        runCatching { graph.generator.ensureCurrent(force = false) }
        // Today's personal mixes too (each is made once a day; later runs find them ready).
        MixGenerator.Personal.entries.forEach { kind -> runCatching { graph.generator.ensurePersonal(kind) } }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MixWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("daily-mixes", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
