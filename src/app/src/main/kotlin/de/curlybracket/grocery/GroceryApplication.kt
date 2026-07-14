package de.curlybracket.grocery

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import de.curlybracket.grocery.sync.BackgroundSyncWorker
import de.curlybracket.grocery.sync.SyncLifecycleManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GroceryApplication : Application() {

    @Inject lateinit var syncLifecycleManager: SyncLifecycleManager

    override fun onCreate() {
        super.onCreate()
        syncLifecycleManager.initialize()
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .addTag(BackgroundSyncWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BackgroundSyncWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
