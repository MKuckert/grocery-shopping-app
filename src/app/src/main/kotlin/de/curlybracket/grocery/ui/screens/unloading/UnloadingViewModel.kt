package de.curlybracket.grocery.ui.screens.unloading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
internal class UnloadingViewModel @Inject constructor(
  private val repository: GroceryRepository,
  private val supabase: SupabaseConnector
) : ViewModel() {

  private val householdId: StateFlow<String?> = supabase.sessionStatus
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
  val items: StateFlow<List<ProductKind>> =
    householdId
      .flatMapLatest { houseId ->
        if (houseId != null) {
          repository.watchUnloadingItems(houseId)
        } else {
          flowOf(emptyList())
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _showWarningDialog = MutableStateFlow(false)
  val showWarningDialog: StateFlow<Boolean> = _showWarningDialog.asStateFlow()

  private val _snackbarMessage = MutableSharedFlow<String>()
  val snackbarMessage: Flow<String> = _snackbarMessage

  fun incrementPending(product: ProductKind) {
    viewModelScope.launch {
      repository.setPendingStock(product.id, product.pendingStock + 1)
    }
  }

  fun decrementPending(product: ProductKind) {
    viewModelScope.launch {
      repository.setPendingStock(product.id, product.pendingStock.coerceAtLeast(1))
    }
  }

  fun setUnloadOpen(product: ProductKind, open: Boolean) {
    viewModelScope.launch {
      try {
        repository.setUnloadOpen(product.id, open)
      } catch (e: Exception) {
        Logger.e("Error setting unload_open", e)
        _snackbarMessage.emit("Failed to update item status")
      }
    }
  }

  fun requestSubmit() {
    val uncheckedCount = items.value.count { it.unloadOpen }
    if (uncheckedCount > 0) {
      _showWarningDialog.value = true
    } else {
      confirmSubmit()
    }
  }

  fun confirmSubmit() {
    _showWarningDialog.value = false
    val houseId = householdId.value ?: return
    viewModelScope.launch {
      try {
        repository.submitUnloading(houseId)
      } catch (e: Exception) {
        Logger.e("Error submitting unloading", e)
        _snackbarMessage.emit("Failed to submit unloading")
      }
    }
  }

  fun dismissDialog() {
    _showWarningDialog.value = false
  }
}
