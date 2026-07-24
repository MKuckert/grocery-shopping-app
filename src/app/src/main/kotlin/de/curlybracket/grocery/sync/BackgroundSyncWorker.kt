package de.curlybracket.grocery.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: PowerSyncDatabase,
    private val connector: SupabaseConnector,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionStatus = connector.sessionStatus.first { it !is SessionStatus.Initializing }
        if (sessionStatus !is SessionStatus.Authenticated) {
            Logger.i("BackgroundSyncWorker: not authenticated, skipping")
            return Result.failure()
        }

        return try {
            database.connect(connector)
            withTimeout(SYNC_TIMEOUT_MS) {
                database.currentStatus.asFlow()
                    .filter { status -> !status.downloading && !status.uploading && status.connected }
                    .first()
            }
            Logger.i("BackgroundSyncWorker: sync complete")
            Result.success()
        } catch (e: TimeoutCancellationException) {
            Logger.w("BackgroundSyncWorker: sync timed out, will retry")
            Result.retry()
        } catch (e: Exception) {
            Logger.e("BackgroundSyncWorker: sync failed", e)
            Result.retry()
        } finally {
            database.disconnect()
        }
    }

    companion object {
        const val WORK_TAG = "background_sync"
        private const val SYNC_TIMEOUT_MS = 30_000L
    }
}
