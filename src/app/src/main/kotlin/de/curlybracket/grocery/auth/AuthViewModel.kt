package de.curlybracket.grocery.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.curlybracket.grocery.sync.BackgroundSyncWorker
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object SignedOut : AuthState()
    data object SignedIn : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val connector: SupabaseConnector,
    private val database: PowerSyncDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    val householdIdFlow: StateFlow<String?> = connector.householdIdFlow(viewModelScope)

    init {
        viewModelScope.launch {
            connector.sessionStatus.collect {
                when (it) {
                  is SessionStatus.Authenticated -> {
                    _authState.value = AuthState.SignedIn
                    _userId.value = it.session.user?.id
                  }

                  is SessionStatus.Initializing -> Logger.e("Loading from storage")
                  is SessionStatus.RefreshFailure -> {
                    // SessionStatus.RefreshFailure.cause is deprecated but connector.supabaseClient.auth.events is still experimental
                    // Caught between a rock and a hard place ¯\_(ツ)_/¯
                    when (it.cause) {
                      is RefreshFailureCause.NetworkError ->
                        Logger.e("Network error occurred")

                      is RefreshFailureCause.InternalServerError ->
                        Logger.e("Internal server error occurred")
                    }
                  }

                  is SessionStatus.NotAuthenticated -> {
                    _authState.value = AuthState.SignedOut
                  }
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        connector.login(email, password)
    }

    suspend fun signUp(email: String, password: String) {
        connector.signUp(email, password)
    }

    suspend fun signOut() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(BackgroundSyncWorker.WORK_TAG)
            connector.signOut()
            database.disconnectAndClear()
        } catch (e: Exception) {
            Logger.e("Error signing out: $e")
        }
    }
}
