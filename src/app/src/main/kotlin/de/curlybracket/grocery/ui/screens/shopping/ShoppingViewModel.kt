package de.curlybracket.grocery.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductKind
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
internal class ShoppingViewModel @Inject constructor(
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
  val activeShopping: StateFlow<List<ProductKind>> =
    householdId
      .flatMapLatest { houseId ->
        if (houseId != null) {
          repository.watchActiveShopping(houseId)
        } else {
          flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val struckThrough: StateFlow<List<ProductKind>> =
    householdId
      .flatMapLatest { houseId ->
        if (houseId != null) {
          repository.watchStruckThrough(houseId)
        } else {
          flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val impulseBuys: StateFlow<List<ProductKind>> =
    householdId
      .flatMapLatest { houseId ->
        if (houseId != null) {
          repository.watchImpulseBuys(houseId)
        } else {
          flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _searchQuery = MutableSharedFlow<String>()
  val searchQuery: StateFlow<String> = _searchQuery
    .stateIn(viewModelScope, SharingStarted.Eagerly, "")

  @OptIn(ExperimentalCoroutinesApi::class)
  val searchResults: StateFlow<List<ProductKind>> =
    searchQuery
      .flatMapLatest { query ->
        if (householdId.value != null && query.isNotEmpty()) {
          repository.watchSearch(query, householdId.value!!)
        } else {
          flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
  val snackbarMessage: Flow<SnackbarMessage> = _snackbarMessage

  fun finishShopping() {
    viewModelScope.launch {
      try {
        repository.setHouseholdState(HouseholdState.UNLOADING)
      } catch (e: Exception) {
        Logger.e("Error finishing shopping", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to finish shopping",
            productId = ""
          )
        )
      }
    }
  }

  fun fulfillFull(product: ProductKind) {
    viewModelScope.launch {
      try {
        repository.fulfillFull(product.id)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "${product.name}: marked as complete",
            productId = product.id
          )
        )
      } catch (e: Exception) {
        Logger.e("Error fulfilling product", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to update ${product.name}",
            productId = product.id
          )
        )
      }
    }
  }

  fun incrementPending(product: ProductKind) {
    viewModelScope.launch {
      try {
        repository.incrementPendingStock(product.id)
      } catch (e: Exception) {
        Logger.e("Error incrementing pending stock", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to update ${product.name}",
            productId = product.id
          )
        )
      }
    }
  }

  fun decrementPending(product: ProductKind) {
    viewModelScope.launch {
      try {
        repository.decrementPendingStock(product.id)
      } catch (e: Exception) {
        Logger.e("Error decrementing pending stock", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to update ${product.name}",
            productId = product.id
          )
        )
      }
    }
  }

  fun updateSearchQuery(q: String) {
    viewModelScope.launch {
      _searchQuery.emit(q)
    }
  }

  fun forceAddToCart(product: ProductKind) {
    viewModelScope.launch {
      try {
        // If pending_stock is 0, set it to 1
        if (product.pendingStock == 0) {
          repository.setPendingStock(product.id, 1)
        }
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "${product.name}: added to cart",
            productId = product.id
          )
        )
      } catch (e: Exception) {
        Logger.e("Error adding to cart", e)
        _snackbarMessage.emit(
          SnackbarMessage(
            text = "Failed to add ${product.name} to cart",
            productId = product.id
          )
        )
      }
    }
  }
}
