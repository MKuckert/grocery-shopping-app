package de.curlybracket.grocery.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * Tracks authentication state only. Connection management (db.connect / disconnectAndClear)
 * is delegated to SyncService which runs as a foreground service.
 */
@HiltViewModel
internal class AuthViewModel @Inject constructor(
    private val supabase: SupabaseConnector,
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    private val _householdId = MutableStateFlow<String?>(null)
    val householdId: StateFlow<String?> = _householdId

    init {
        viewModelScope.launch {
            supabase.sessionStatus.collect {
                when (it) {
                    is SessionStatus.Authenticated -> {
                        _authState.value = AuthState.SignedIn
                        _userId.value = it.session.user?.id
                        _householdId.value = it.session.user?.userMetadata?.get("household_id") as? String
                    }
                    is SessionStatus.Initializing -> Logger.e("Loading from storage")
                    is SessionStatus.RefreshFailure -> {
                        @Suppress("DEPRECATION")
                        when (it.cause) {
                            is RefreshFailureCause.NetworkError ->
                                Logger.e("Network error occurred")
                            is RefreshFailureCause.InternalServerError ->
                                Logger.e("Internal server error occurred")
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _authState.value = AuthState.SignedOut
                        _householdId.value = null
                    }
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        supabase.login(email, password)
    }

    suspend fun signUp(email: String, password: String) {
        supabase.signUp(email, password)
    }

    suspend fun signOut() {
        try {
            supabase.signOut()
        } catch (e: Exception) {
            Logger.e("Error signing out: $e")
        }
    }
}
