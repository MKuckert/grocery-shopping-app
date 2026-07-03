package de.curlybracket.grocery.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: GroceryRepository,
    private val connector: SupabaseConnector,
) : ViewModel() {

    val householdIdFlow: StateFlow<String?> = connector.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated ->
                    status.session.user?.appMetadata?.get("household_id")
                        ?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeShopping: StateFlow<List<ProductKind>> =
        householdIdFlow.flatMapLatest { hid ->
            if (hid == null) flowOf(emptyList())
            else repository.watchActiveShopping(hid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val struckThrough: StateFlow<List<ProductKind>> =
        householdIdFlow.flatMapLatest { hid ->
            if (hid == null) flowOf(emptyList())
            else repository.watchStruckThrough(hid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val impulseBuys: StateFlow<List<ProductKind>> =
        householdIdFlow.flatMapLatest { hid ->
            if (hid == null) flowOf(emptyList())
            else repository.watchImpulseBuys(hid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<ProductKind>> =
        householdIdFlow.flatMapLatest { hid ->
            if (hid == null) flowOf(emptyList())
            else _searchQuery.flatMapLatest { query ->
                if (query.isBlank()) flowOf(emptyList())
                else repository.watchSearch(query, hid)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    fun finishShopping() {
        viewModelScope.launch {
            try {
                repository.setHouseholdState(HouseholdState.UNLOADING)
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun fulfillFull(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.fulfillFull(product.id)
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun incrementPending(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.incrementPendingStock(product.id)
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun decrementPending(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.decrementPendingStock(product.id)
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun forceAddToCart(product: ProductKind) {
        viewModelScope.launch {
            try {
                if (product.pendingStock == 0) {
                    repository.setPendingStock(product.id, 1)
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error: ${e.message}")
            }
        }
    }
}
