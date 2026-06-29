package de.curlybracket.grocery.scanner

import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.domain.repository.GroceryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class ScannerProcessor @Inject constructor(
  private val repository: GroceryRepository,
  private val audioFeedback: AudioFeedback
) {

  private val _scanResultFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
  val scanResultFlow = _scanResultFlow.asSharedFlow()

  suspend fun processScan(
    barcode: String,
    mode: ScannerMode,
    onOpenFoodFactsLookupStart: (String) -> Unit
  ) {
    val householdId = when (mode) {
      is ScannerMode.Inventory -> mode.householdId
      is ScannerMode.Shopping -> mode.householdId
    }

    // O(1) local lookup
    val product = repository.findByBarcode(barcode, householdId)

    when {
      // Hit: product found locally
      product != null && product.deletedAt == null -> {
        audioFeedback.playSuccess()
        applyModeSpecificMutation(product.id, mode)
        _scanResultFlow.emit(ScanResult.Hit(product))
      }

      // Soft-delete resurrection
      product != null && product.deletedAt != null -> {
        repository.restoreProductKind(product.id)
        repository.recalculateQuantityToBuy(product.id)
        audioFeedback.playSuccess()
        val restored = product.copy(deletedAt = null)
        _scanResultFlow.emit(ScanResult.Restored(restored))
      }

      // Miss: no local match, trigger Open Food Facts lookup
      else -> {
        audioFeedback.playFailure()
        onOpenFoodFactsLookupStart(barcode)
        _scanResultFlow.emit(ScanResult.Miss(barcode))
      }
    }
  }

  private suspend fun applyModeSpecificMutation(productId: String, mode: ScannerMode) {
    when (mode) {
      is ScannerMode.Inventory -> {
        // Decrement stock for Inventory mode
        repository.decrementStock(productId)
      }

      is ScannerMode.Shopping -> {
        // Increment pending for Shopping mode
        repository.incrementPendingStock(productId)
      }
    }
  }
}
