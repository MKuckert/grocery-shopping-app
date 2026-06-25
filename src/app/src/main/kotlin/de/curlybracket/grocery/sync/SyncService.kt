package de.curlybracket.grocery.sync

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.sync.SyncStatusData
import dagger.hilt.android.AndroidEntryPoint
import de.curlybracket.grocery.R
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class SyncService : LifecycleService() {

    @Inject lateinit var connector: SupabaseConnector
    @Inject lateinit var database: PowerSyncDatabase

    private var holdsServiceLock = false
    private val notificationManager get() = NotificationManagerCompat.from(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        holdsServiceLock = SERVICE_RUNNING.compareAndSet(false, true)
        if (!holdsServiceLock) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            startId,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        // Update notification whenever sync status changes
        lifecycleScope.launch {
            database.currentStatus.asFlow().collect { status ->
                try {
                    Logger.i("SyncService status: $status")
                    notificationManager.notify(startId, buildNotification(status))
                } catch (e: SecurityException) {
                    Logger.d("Ignoring SecurityException updating notification", e)
                }
            }
        }

        // Connect/disconnect in response to auth state
        lifecycleScope.launch {
            connector.sessionStatus.collect { sessionStatus ->
                when (sessionStatus) {
                    is SessionStatus.Authenticated -> {
                        database.connect(connector)
                    }
                    is SessionStatus.NotAuthenticated -> {
                        database.disconnectAndClear()
                        Logger.i("SyncService: user logged out, stopping")
                        return@collect
                    }
                    else -> Unit
                }
            }
        }.invokeOnCompletion { cause ->
            if (cause !is CancellationException) {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        if (holdsServiceLock) SERVICE_RUNNING.set(false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.background_channel_name))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: SyncStatusData? = null): Notification =
        Notification.Builder(this, CHANNEL_ID).apply {
            setContentTitle(getString(R.string.sync_notification_title))
            setSmallIcon(R.mipmap.ic_launcher)
            if (state != null && (state.uploading || state.downloading)) {
                setProgress(0, 0, true)
            }
        }.build()

    companion object {
        private const val CHANNEL_ID = "background_sync"
        private val SERVICE_RUNNING = AtomicBoolean(false)
    }
}
