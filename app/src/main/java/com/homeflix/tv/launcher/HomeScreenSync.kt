package com.homeflix.tv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.screens.feeds.FeedCache
import com.homeflix.tv.presentation.screens.feeds.HomeFeedBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * Keeps HomeFlix's home-screen row current while the app is closed: rebuilds
 * the Home feed from the server every few hours, shortly after you stop
 * watching, after boot / an update, and when the launcher asks.
 */
object HomeScreenSync {
    private const val PERIODIC = "home-screen-periodic"
    private const val ONCE = "home-screen-once"

    fun schedule(context: Context) {
        if (!HomeScreenPublisher.supported) return
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HomeScreenWorker>(3, TimeUnit.HOURS).setConstraints(network()).build()
            )
        }
    }

    /** Refresh soon (e.g. right after leaving the app, so progress is current). */
    fun refreshSoon(context: Context, delaySeconds: Long = 20) {
        if (!HomeScreenPublisher.supported) return
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HomeScreenWorker>()
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setConstraints(network())
                    .build()
            )
        }
    }

    private fun network() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeScreenEntryPoint {
    fun mediaRepository(): MediaRepository
    fun feedCache(): FeedCache
    fun homeScreenPublisher(): HomeScreenPublisher
}

class HomeScreenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, HomeScreenEntryPoint::class.java)
        val feed = runCatching {
            withTimeout(60_000) { HomeFeedBuilder(entry.mediaRepository()).build() }
        }.getOrNull() ?: entry.feedCache().read("home") ?: return Result.retry()
        entry.feedCache().write("home", feed) // the app also opens with it
        entry.homeScreenPublisher().publish(feed)
        return Result.success()
    }
}

/** The launcher asks new installs to fill their channels (android.media.tv.action.INITIALIZE_PROGRAMS). */
class HomeScreenInitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HomeScreenSync.schedule(context)
        HomeScreenSync.refreshSoon(context, delaySeconds = 1)
    }
}
