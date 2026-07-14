package de.curlybracket.grocery.ui.screens.unloading

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import co.touchlab.kermit.Logger
import de.curlybracket.grocery.R
import de.curlybracket.grocery.auth.householdIdFlow
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
internal class UnloadingViewModel @Inject constructor(
    private val repository: GroceryRepository,
    private val connector: SupabaseConnector,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val householdIdFlow: StateFlow<String?> = connector.householdIdFlow(viewModelScope)

    val items: StateFlow<List<ProductKind>> =
        householdIdFlow.flatMapLatest { hid ->
            if (hid == null) flowOf(emptyList())
            else repository.watchUnloadingItems(hid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _showWarningDialog = MutableStateFlow(false)
    val showWarningDialog: StateFlow<Boolean> = _showWarningDialog

    private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
    val snackbarMessage: SharedFlow<SnackbarMessage> = _snackbarMessage

    fun toggleUnloadOpen(product: ProductKind, checked: Boolean) {
        // Checkbox checked = locked (unload_open = false), unchecked = open (unload_open = true)
        val open = !checked
        viewModelScope.launch {
            try {
                repository.setUnloadOpen(product.id, open)
            } catch (e: Exception) {
                Logger.e("Failed to toggle unload state", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.unloading_error_toggle_unload_state),
                        productId = product.id,
                    ),
                )
            }
        }
    }

    fun requestSubmit() {
        val hasOpenItems = items.value.any { it.unloadOpen }
        if (hasOpenItems) {
            _showWarningDialog.value = true
        } else {
            confirmSubmit()
        }
    }

    fun confirmSubmit() {
        _showWarningDialog.value = false
        val hid = householdIdFlow.value ?: return
        viewModelScope.launch {
            try {
                repository.submitUnloading(hid)
            } catch (e: Exception) {
                Logger.e("Failed to submit unloading", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.unloading_error_submit_unloading),
                        productId = "",
                    ),
                )
            }
        }
    }

    fun dismissDialog() {
        _showWarningDialog.value = false
    }

    fun incrementPending(product: ProductKind) {
        viewModelScope.launch {
            try {
                repository.incrementPendingStock(product.id)
            } catch (e: Exception) {
                Logger.e("Failed to increment pending", e)
                _snackbarMessage.emit(
                    SnackbarMessage(
                        text = context.getString(R.string.unloading_error_increment_pending),
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
                        text = context.getString(R.string.unloading_error_decrement_pending),
                        productId = product.id,
                    ),
                )
            }
        }
    }
}
