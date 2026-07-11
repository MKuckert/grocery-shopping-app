package de.curlybracket.grocery.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.SnackbarMessage
import de.curlybracket.grocery.domain.repository.GroceryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private val _savedIndicator = MutableStateFlow(false)
    val savedIndicator: StateFlow<Boolean> = _savedIndicator

    private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
    val snackbarMessage: SharedFlow<SnackbarMessage> = _snackbarMessage

    private val _deleteEvent = MutableSharedFlow<String>()
    val deleteEvent: SharedFlow<String> = _deleteEvent

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

    private var pendingSaveJob: Job? = null

    init {
        // Sync product state into edit fields when product loads and user isn't editing
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

        // Debounced auto-save: combine all 4 edit flows, drop(1) skips initial emission
        pendingSaveJob = viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            combine(_name, _groupId, _currentStock, _minimumStock) { name, groupId, currentStock, minimumStock ->
                EditSnapshot(name, groupId, currentStock, minimumStock)
            }
                .drop(1)
                .debounce(800)
                .collect { snapshot ->
                    if (_userEditing.value) {
                        performSave(snapshot)
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
                Logger.e("Failed to add barcode", e)
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to add barcode", productId = productId))
            }
        }
    }

    fun deleteBarcode(barcode: Barcode) {
        viewModelScope.launch {
            try {
                repository.deleteBarcode(barcode.id)
            } catch (e: Exception) {
                Logger.e("Failed to delete barcode", e)
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to delete barcode", productId = productId))
            }
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val hid = product.value?.householdId ?: return
        viewModelScope.launch {
            try {
                val newGroupId = repository.createProductGroup(hid, trimmed)
                updateGroup(newGroupId)
            } catch (e: Exception) {
                Logger.e("Failed to create product group", e)
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to create group", productId = productId))
            }
        }
    }

    fun deleteProduct() {
        viewModelScope.launch {
            try {
                repository.deleteProductKind(productId)
                _deleteEvent.emit(productId)
            } catch (e: Exception) {
                Logger.e("Failed to delete product", e)
                _snackbarMessage.emit(SnackbarMessage(text = "Failed to delete product", productId = productId))
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        pendingSaveJob?.cancel()
        // Immediately persist any pending changes when the ViewModel is destroyed.
        // runBlocking is safe here: this is local SQLite, sub-millisecond, not a network call.
        // The save indicator is skipped — the screen is already gone.
        if (_userEditing.value) {
            runBlocking {
                performSave(currentSnapshot(), showIndicator = false)
            }
        }
    }

    private suspend fun performSave(snapshot: EditSnapshot, showIndicator: Boolean = true) {
        try {
            repository.updateProductKind(
                productId = productId,
                name = snapshot.name.trim(),
                groupId = snapshot.groupId,
                minimumStock = snapshot.minimumStock,
                currentStock = snapshot.currentStock,
                imagePath = product.value?.imagePath,
            )
            // Only reset _userEditing if the current values still match what we saved.
            // If the user edited during the save, leave _userEditing = true so the
            // debounce fires again for the new changes.
            if (_name.value == snapshot.name &&
                _groupId.value == snapshot.groupId &&
                _currentStock.value == snapshot.currentStock &&
                _minimumStock.value == snapshot.minimumStock
            ) {
                _userEditing.value = false
            }
            if (showIndicator) {
                _savedIndicator.value = true
                delay(1_500)
                _savedIndicator.value = false
            }
        } catch (e: Exception) {
            Logger.e("Failed to save changes", e)
            _snackbarMessage.emit(SnackbarMessage(text = "Failed to save changes", productId = productId))
        }
    }

    private fun currentSnapshot() = EditSnapshot(
        name = _name.value,
        groupId = _groupId.value,
        currentStock = _currentStock.value,
        minimumStock = _minimumStock.value,
    )

    private data class EditSnapshot(
        val name: String,
        val groupId: String?,
        val currentStock: Int,
        val minimumStock: Int,
    )
}
