package de.curlybracket.grocery.ui.screens.shopping

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import co.touchlab.kermit.Logger
import de.curlybracket.grocery.R
import de.curlybracket.grocery.auth.householdIdFlow
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.SnackbarMessage
import de.curlybracket.grocery.domain.repository.GroceryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: GroceryRepository,
    private val connector: SupabaseConnector,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val householdIdFlow: StateFlow<String?> = connector.householdIdFlow(viewModelScope)

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

    private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
    val snackbarMessage: SharedFlow<SnackbarMessage> = _snackbarMessage

    fun finishShopping() {
        viewModelScope.launch {
            try {
                repository.setHouseholdState(HouseholdState.UNLOADING)
            } catch (e: Exception) {
                Logger.e("Failed to finish shopping", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.shopping_error_finish_shopping),
                        productId = "",
                    ),
                )
            }
        }
    }

    fun fulfillFull(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.fulfillFull(product.id)
            } catch (e: Exception) {
                Logger.e("Failed to fulfill item", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.shopping_error_fulfill_item),
                        productId = product.id,
                    ),
                )
            }
        }
    }

    fun incrementPending(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.incrementPendingStock(product.id)
            } catch (e: Exception) {
                Logger.e("Failed to increment pending", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.shopping_error_increment_pending),
                        productId = product.id,
                    ),
                )
            }
        }
    }

    fun decrementPending(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.decrementPendingStock(product.id)
            } catch (e: Exception) {
                Logger.e("Failed to decrement pending", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.shopping_error_decrement_pending),
                        productId = product.id,
                    ),
                )
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
                Logger.e("Failed to force-add item", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.shopping_error_force_add_item),
                        productId = product.id,
                    ),
                )
            }
        }
    }
}
