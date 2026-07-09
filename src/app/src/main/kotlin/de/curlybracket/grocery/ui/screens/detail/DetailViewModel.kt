package de.curlybracket.grocery.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.SnackbarMessage
import de.curlybracket.grocery.domain.repository.GroceryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: GroceryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val productId: String = savedStateHandle["productId"]!!

    val product: StateFlow<ProductKind?> =
        repository.watchProductKind(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<ProductGroup>> =
        repository.watchProductGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val barcodes: StateFlow<List<Barcode>> =
        repository.watchBarcodes(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
    val snackbarMessage: SharedFlow<SnackbarMessage> = _snackbarMessage

    // Local edit state — kept in sync with product stream until user edits
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _groupId = MutableStateFlow<String?>(null)
    val groupId: StateFlow<String?> = _groupId

    private val _currentStock = MutableStateFlow(0)
    val currentStock: StateFlow<Int> = _currentStock

    private val _minimumStock = MutableStateFlow(1)
    val minimumStock: StateFlow<Int> = _minimumStock

    private val _userEditing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            product.collect { p ->
                if (p != null && !_userEditing.value) {
                    _name.value = p.name
                    _groupId.value = p.groupId
                    _currentStock.value = p.currentStock
                    _minimumStock.value = p.minimumStock
                }
            }
        }
    }

    fun updateName(name: String) {
        _userEditing.value = true
        _name.value = name
    }

    fun updateGroup(groupId: String?) {
        _userEditing.value = true
        _groupId.value = groupId
    }

    fun updateCurrentStock(value: Int) {
        _userEditing.value = true
        _currentStock.value = value.coerceAtLeast(0)
    }

    fun updateMinimumStock(value: Int) {
        _userEditing.value = true
        _minimumStock.value = value.coerceAtLeast(0)
    }

    fun addBarcode(barcodeNumber: String) {
        val hid = product.value?.householdId ?: return
        viewModelScope.launch {
            try {
                repository.addBarcode(productId, barcodeNumber, hid)
            } catch (e: Exception) {
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to add barcode", productId = productId))
            }
        }
    }

    fun deleteBarcode(barcode: Barcode) {
        viewModelScope.launch {
            try {
                repository.deleteBarcode(barcode.id)
            } catch (e: Exception) {
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to delete barcode", productId = productId))
            }
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.updateProductKind(
                    productId = productId,
                    name = _name.value.trim(),
                    groupId = _groupId.value,
                    minimumStock = _minimumStock.value,
                    currentStock = _currentStock.value,
                    imagePath = product.value?.imagePath,
                )
                _userEditing.value = false
                _snackbarMessage.emit(SnackbarMessage(text = "Saved", productId = productId))
            } catch (e: Exception) {
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to save changes", productId = productId))
            } finally {
                _isSaving.value = false
            }
        }
    }
}
