package de.curlybracket.grocery.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector

class FakeWorkerFactory(
    private val database: PowerSyncDatabase,
    private val connector: SupabaseConnector,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != BackgroundSyncWorker::class.java.name) return null
        return BackgroundSyncWorker(appContext, workerParameters, database, connector)
    }
}
