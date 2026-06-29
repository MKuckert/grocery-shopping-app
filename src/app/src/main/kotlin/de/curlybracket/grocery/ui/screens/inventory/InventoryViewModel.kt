package de.curlybracket.grocery.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.GroupWithProducts
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.ProductWithGroup
import de.curlybracket.grocery.domain.model.SnackbarMessage
import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
internal class InventoryViewModel @Inject constructor(
  private val repository: GroceryRepository,
  private val supabase: SupabaseConnector
) : ViewModel() {

  val householdId: StateFlow<String?> = supabase.sessionStatus
    .map { status ->
      when (status) {
        is SessionStatus.Authenticated -> {
          val metadata = status.session.user?.appMetadata
          val householdValue = metadata?.get("household_id")
          if (householdValue is JsonPrimitive && !householdValue.isString) {
            null
          } else if (householdValue is JsonPrimitive) {
            householdValue.content
          } else {
            null
          }
        }
        else -> null
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val groupsWithProducts: StateFlow<List<GroupWithProducts>> =
    householdId
      .flatMapLatest { houseId ->
        if (houseId != null) {
          repository.watchProductsWithGroups(houseId)
            .map { productsWithGroups ->
              productsWithGroups
                .groupBy { it.groupName ?: "Unsorted" }
                .map { (groupName, items) ->
                  GroupWithProducts(
                    groupName = groupName,
                    products = items.map { it.product }
                  )
                }
                .sortedBy { it.groupName }
            }
        } else {
          flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
  val snackbarMessage: Flow<SnackbarMessage> = _snackbarMessage

  fun startShopping() {
    viewModelScope.launch {
      try {
        repository.setHouseholdState(HouseholdState.SHOPPING)
      } catch (e: Exception) {
        Logger.e("Error starting shopping", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to start shopping",
            productId = ""
          )
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
            actionLabel = "Details"
          )
        )
      } catch (e: Exception) {
        Logger.e("Error decrementing stock", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to decrement stock",
            productId = product.id
          )
        )
      }
    }
  }

  fun navigateToDetail(product: ProductKind) {
    viewModelScope.launch {
      _snackbarMessage.emit(
        SnackbarMessage(
          text = "navigate:${product.id}",
          productId = product.id
        )
      )
    }
  }
}
