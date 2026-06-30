package de.curlybracket.grocery.scanner

import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import de.curlybracket.grocery.network.OpenFoodFactsClient
import de.curlybracket.grocery.network.OFResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

data class OpenFoodFactsLookupResult(
  val barcode: String,
  val prefillName: String
)

class ScannerProcessor @Inject constructor(
  private val repository: GroceryRepository,
  private val audioFeedback: AudioFeedback,
  private val openFoodFactsClient: OpenFoodFactsClient
) {

  private val _scanResultFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
  val scanResultFlow = _scanResultFlow.asSharedFlow()

  private val _openFoodFactsResultFlow = MutableSharedFlow<OpenFoodFactsLookupResult>(extraBufferCapacity = 1)
  val openFoodFactsResultFlow = _openFoodFactsResultFlow.asSharedFlow()

  suspend fun processScan(
    barcode: String,
    mode: ScannerMode
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
        
        // Perform Open Food Facts lookup
        val prefillName = when (val result = openFoodFactsClient.lookupBarcode(barcode)) {
          is OFResult.Hit -> result.productName
          is OFResult.Miss -> "Unknown Item"
          else -> "Unknown Item"  // RateLimit is internal and never returned from lookupBarcode
        }
        
        // Emit the lookup result to update UI with prefilled name
        _openFoodFactsResultFlow.emit(OpenFoodFactsLookupResult(barcode, prefillName))
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

  suspend fun createNewProduct(
    barcode: String,
    productName: String,
    householdId: String,
    mode: ScannerMode
  ): ProductKind? {
    return try {
      // Ensure "Unsorted" group exists
      val groupId = repository.ensureUnsortedGroup(householdId)
      
      // Create the product
      repository.createProductKind(
        householdId = householdId,
        name = productName,
        groupId = groupId,
        minimumStock = 1,
        barcodeNumber = barcode
      )
      
      // Fetch and return the newly created product
      repository.findByBarcode(barcode, householdId)
    } catch (e: Exception) {
      null
    }
  }
}
