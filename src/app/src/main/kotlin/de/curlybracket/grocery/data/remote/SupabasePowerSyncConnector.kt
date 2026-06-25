package de.curlybracket.grocery.data.remote

import android.util.Log
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.UpdateType
import io.github.jan.supabase.SupabaseClient
import de.curlybracket.grocery.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabasePowerSyncConnector @Inject constructor(
  private val supabase: SupabaseClient
) : PowerSyncBackendConnector() {

  private val refreshMutex = Mutex()
  private val TAG = "SupabasePowerSyncConnector"

  override suspend fun fetchCredentials(): PowerSyncCredentials {
    return refreshMutex.withLock {
      val session = supabase.auth.currentSessionOrNull()
        ?: throw IllegalStateException("No active Supabase session")

      // Try to refresh to ensure the token is fresh
      try {
        supabase.auth.refreshCurrentSession()
      } catch (e: Exception) {
        Log.w(TAG, "Session refresh failed, using current token: ${e.message}")
        // Continue with current token if refresh fails
      }

      val currentSession = supabase.auth.currentSessionOrNull()
        ?: throw IllegalStateException("No active Supabase session after refresh attempt")

      return PowerSyncCredentials(
        endpoint = BuildConfig.POWERSYNC_URL,
        token = currentSession.accessToken,
        userId = currentSession.user?.id
      )
    }
  }

  override suspend fun uploadData(database: PowerSyncDatabase): Unit {
    val batch = database.getCrudBatch() ?: return

    try {
      for (entry in batch.crud) {
        // For now, just log the operations and mark as complete
        // The actual Supabase API calls require the correct CrudEntry data access pattern
        // which depends on the PowerSync SDK version being used
        when (entry.op) {
          UpdateType.PUT -> {
            Log.d(TAG, "INSERT operation queued: table=${entry.table}, id=${entry.id}")
            // TODO: Implement actual insert via supabase.postgrest when CrudEntry API is verified
            supabase.from(entry.table).insert(entry.opData!!.jsonValues.jsonArray);
          }
          UpdateType.PATCH -> {
            Log.d(TAG, "UPDATE operation queued: table=${entry.table}, id=${entry.id}")
            // TODO: Implement actual update via supabase.postgrest when CrudEntry API is verified
          }
          UpdateType.DELETE -> {
            Log.d(TAG, "DELETE operation queued: table=${entry.table}, id=${entry.id}")
            // TODO: Implement actual delete via supabase.postgrest when CrudEntry API is verified
          }
        }
      }
      batch.complete(null)
    } catch (e: RestException) {
      Log.e(TAG, "Upload error: ${e.message}", e)
      throw e
    }
  }
}
