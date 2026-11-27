package app.siansa.mix

import app.siansa.net.wordsReady
import app.siansa.net.soundReady
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

/** Prepares the day's first mix and today's personal mixes in the background, so they're ready when the app opens. */
class MixWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as SiansaApp).graph
        val settings = graph.settings.current
        // Without AudioMuse the vibe's mix comes from the library (beta #25).
        if (!settings.navidromeReady) return Result.success()
        // Home's Time Machine card for today (issue #1341): made once a day; later runs find it ready.
        if (app.siansa.data.HomeSection.entries.any { it.usesTimeCard && it !in settings.homeHidden }) runCatching { graph.timeCards.refresh() }
        // The vibe's mix is an AI mix: only with AudioMuse (issue #486).
        // Only the day's first mix: a new vibe for the hour doesn't replace the one you have (beta testers #37). Made
        // whoever makes the other mixes: Today's mix is the phone's own (the owner, 26 Nov).
        if (MixRules.showsAiMixes(settings.wordsReady)) runCatching { graph.generator.ensureToday() }
        // While Siansa Beats makes Home's other mixes, the phone makes none of them (the owner, 17 Nov).
        if (settings.beatsReady && settings.beatsFor(app.siansa.data.BeatsUse.MIXES) && app.siansa.net.BeatsSound.mixes.value) return Result.success()
        // Today's personal mixes too (each is made once a day; later runs find them ready).
        MixGenerator.Personal.entries.filter { MixRules.showsPersonal(it, settings.soundReady, settings.phoneSmoothRun) }
            .forEach { kind -> runCatching { graph.generator.ensurePersonal(kind) } }
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
