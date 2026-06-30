package de.curlybracket.grocery.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.connector.supabase.SupabaseConnector
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed class AuthState {
    data object SignedOut : AuthState()
    data object SignedIn : AuthState()
}

internal class AuthViewModel(
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
                        _householdId.value = it.session.user?.appMetadata
                            ?.get("household_id")
                            ?.jsonPrimitive
                            ?.contentOrNull
                    }
                    is SessionStatus.Initializing -> Logger.e("Loading from storage")
                    is SessionStatus.RefreshFailure -> {
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
