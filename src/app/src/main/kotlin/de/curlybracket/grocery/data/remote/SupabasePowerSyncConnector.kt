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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        token = currentSession.accessToken
      )
    }
  }

  override suspend fun uploadData(database: PowerSyncDatabase): Unit {
    val batch = database.getCrudBatch() ?: return

    try {
      for (entry in batch.crud) {
        when (entry.op) {
          UpdateType.PUT -> {
            // Handle INSERT operation
            Log.d(TAG, "INSERT operation for table: ${entry.table}")
            // Future: call supabase.from(entry.table).insert(entry.data)
          }

          UpdateType.PATCH -> {
            // Handle UPDATE operation
            Log.d(TAG, "UPDATE operation for table: ${entry.table}")
            // Future: call supabase.from(entry.table).update(entry.data).eq("id", entry.id)
          }

          UpdateType.DELETE -> {
            // Handle DELETE operation
            Log.d(TAG, "DELETE operation for table: ${entry.table}")
            // Future: call supabase.from(entry.table).delete().eq("id", entry.id)
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
