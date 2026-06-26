package de.curlybracket.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.auth.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level ViewModel that provides access to authentication state.
 * This ViewModel can be injected into composables that need app-wide state.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val connector: SupabaseConnector,
) : ViewModel()

