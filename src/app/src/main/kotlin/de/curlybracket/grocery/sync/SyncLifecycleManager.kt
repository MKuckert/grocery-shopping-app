package de.curlybracket.grocery.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLifecycleManager @Inject constructor(
    private val database: PowerSyncDatabase,
    private val connector: SupabaseConnector,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var disconnectJob: Job? = null

    fun initialize() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        disconnectJob?.cancel()
        scope.launch {
            val status = connector.sessionStatus.first { it !is SessionStatus.Initializing }
            if (status is SessionStatus.Authenticated) {
                Logger.i("SyncLifecycleManager: app foregrounded, connecting")
                database.connect(connector)
            } else {
                Logger.i("SyncLifecycleManager: app foregrounded but not authenticated, skipping connect")
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        disconnectJob = scope.launch {
            delay(DISCONNECT_DEBOUNCE_MS)
            Logger.i("SyncLifecycleManager: app backgrounded, disconnecting")
            database.disconnect()
        }
    }

    companion object {
        private const val DISCONNECT_DEBOUNCE_MS = 2_000L
    }
}
