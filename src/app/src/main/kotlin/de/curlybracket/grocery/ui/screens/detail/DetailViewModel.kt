package de.curlybracket.grocery.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
internal class DetailViewModel @Inject constructor(
  private val repository: GroceryRepository,
  private val supabase: SupabaseConnector,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val productId: String = checkNotNull(savedStateHandle["productId"])

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

  val product: StateFlow<ProductKind?> = repository.watchProductKind(productId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val groups: StateFlow<List<ProductGroup>> = repository.watchProductGroups()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val barcodes: StateFlow<List<Barcode>> = repository.watchBarcodes(productId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _saveError = MutableSharedFlow<String>()
  val saveError = _saveError.asSharedFlow()

  private var editName: String? = null
  private var editGroupId: String? = null
  private var editCurrentStock: Int? = null
  private var editMinimumStock: Int? = null

  fun updateName(name: String) {
    editName = name
  }

  fun updateGroup(groupId: String?) {
    editGroupId = groupId
  }

  fun updateCurrentStock(value: Int) {
    editCurrentStock = value
  }

  fun updateMinimumStock(value: Int) {
    editMinimumStock = value
  }

  fun addBarcode(barcodeNumber: String) {
    viewModelScope.launch {
      val houseId = householdId.value
      if (houseId != null) {
        try {
          repository.addBarcode(productId, barcodeNumber, houseId)
        } catch (e: Exception) {
          Logger.e("Error adding barcode", e)
          _saveError.emit("Failed to add barcode")
        }
      }
    }
  }

  fun deleteBarcode(barcode: Barcode) {
    viewModelScope.launch {
      try {
        repository.deleteBarcode(barcode.id)
      } catch (e: Exception) {
        Logger.e("Error deleting barcode", e)
        _saveError.emit("Failed to delete barcode")
      }
    }
  }

  fun saveChanges() {
    val p = product.value ?: return
    viewModelScope.launch {
      try {
        repository.updateProductKind(
          productId = p.id,
          name = editName ?: p.name,
          groupId = editGroupId ?: p.groupId,
          minimumStock = editMinimumStock ?: p.minimumStock,
          currentStock = editCurrentStock ?: p.currentStock,
          imagePath = p.imagePath
        )
        // Reset edit state after successful save
        editName = null
        editGroupId = null
        editCurrentStock = null
        editMinimumStock = null
      } catch (e: Exception) {
        Logger.e("Error saving product", e)
        _saveError.emit("Failed to save changes")
      }
    }
  }
}
