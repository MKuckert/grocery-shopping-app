package de.curlybracket.grocery.ui.screens.inventory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import co.touchlab.kermit.Logger
import de.curlybracket.grocery.R
import de.curlybracket.grocery.auth.householdIdFlow
import de.curlybracket.grocery.domain.model.GroupWithProducts
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.SnackbarMessage
import de.curlybracket.grocery.domain.repository.GroceryRepository
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: GroceryRepository,
    private val connector: SupabaseConnector,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val householdIdFlow: StateFlow<String?> = connector.householdIdFlow(viewModelScope)

    val groupsWithProducts: StateFlow<List<GroupWithProducts>> =
        householdIdFlow
            .flatMapLatest { hid ->
                if (hid == null) {
                    flowOf(emptyList())
                } else {
                    repository.watchProductsWithGroups(hid)
                        .map { list ->
                            list
                                .groupBy { it.groupName ?: context.getString(R.string.inventory_group_unsorted) }
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
                    SnackbarMessage(
                        text = context.getString(R.string.inventory_error_start_shopping),
                        productId = "",
                    ),
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
                        text = context.getString(R.string.inventory_snackbar_remaining, product.name, newStock),
                        productId = product.id,
                        actionLabel = context.getString(R.string.action_details),
                    ),
                )
            } catch (e: Exception) {
                Logger.e("Failed to decrement stock", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.inventory_error_decrement_stock),
                        productId = product.id,
                    ),
                )
            }
        }
    }

    fun navigateToDetail(product: ProductKind) {
        viewModelScope.launch {
            _navigationEvent.emit(product.id)
        }
    }

    fun showDeletedProductUndo(deletedProductId: String, productName: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(
                SnackbarMessage(
                    text = context.getString(R.string.inventory_snackbar_product_deleted, productName),
                    productId = deletedProductId,
                    actionLabel = context.getString(R.string.action_undo),
                ),
            )
        }
    }

    fun restoreProduct(productId: String) {
        viewModelScope.launch {
            try {
                repository.restoreProductKind(productId)
            } catch (e: Exception) {
                Logger.e("Failed to restore product", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.inventory_error_restore_product),
                        productId = productId,
                    ),
                )
            }
        }
    }
}
