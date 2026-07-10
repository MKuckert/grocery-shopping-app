package de.curlybracket.grocery.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import co.touchlab.kermit.Logger
import de.curlybracket.grocery.domain.model.GroupWithProducts
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.SnackbarMessage
import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class InventoryViewModel @Inject constructor(
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

    val groupsWithProducts: StateFlow<List<GroupWithProducts>> =
        householdIdFlow
            .flatMapLatest { hid ->
                if (hid == null) {
                    flowOf(emptyList())
                } else {
                    repository.watchProductsWithGroups(hid)
                        .map { list ->
                            list
                                .groupBy { it.groupName ?: "Unsorted" }
                                .entries
                                .sortedBy { it.key }
                                .map { (groupName, items) ->
                                    GroupWithProducts(
                                        groupName = groupName,
                                        products = items.map { it.product },
                                    )
                                }
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
    val snackbarMessage: SharedFlow<SnackbarMessage> = _snackbarMessage

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent

    fun startShopping() {
        viewModelScope.launch {
            try {
                repository.setHouseholdState(HouseholdState.SHOPPING)
            } catch (e: Exception) {
                Logger.e("Failed to start shopping", e)
                _snackbarMessage.emit(
                    SnackbarMessage(text = "Failed to start shopping", productId = ""),
                )
            }
        }
    }

    fun decrementStock(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.decrementStock(product.id)
                val newStock = (product.currentStock - 1).coerceAtLeast(0)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = "${product.name}: $newStock remaining",
                        productId = product.id,
                        actionLabel = "Details",
                    ),
                )
            } catch (e: Exception) {
                Logger.e("Failed to decrement stock", e)
                _snackbarMessage.emit(
                    SnackbarMessage(text = "Failed to decrement stock", productId = product.id),
                )
            }
        }
    }

    fun navigateToDetail(product: ProductKind) {
        viewModelScope.launch {
            _navigationEvent.emit(product.id)
        }
    }
}
