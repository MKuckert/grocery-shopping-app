package de.curlybracket.grocery.scanner

import android.content.Context
import android.util.Log
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.domain.repository.GroceryRepository
import de.curlybracket.grocery.network.OFResult
import de.curlybracket.grocery.network.OpenFoodFactsClient
import java.io.File

private const val TAG = "ScannerProcessor"

class ScannerProcessor(
    private val repository: GroceryRepository,
    private val audioFeedback: AudioFeedback,
    private val openFoodFactsClient: OpenFoodFactsClient,
) {

    suspend fun process(
        barcode: String,
        mode: ScannerMode,
        onStateChange: (ScannerState) -> Unit,
        onResult: (ScanResult) -> Unit,
    ) {
        val householdId = when (mode) {
            is ScannerMode.Inventory -> mode.householdId
            is ScannerMode.Shopping -> mode.householdId
        }

        val product = try {
            repository.findByBarcode(barcode, householdId)
        } catch (e: Exception) {
            Log.e(TAG, "findByBarcode failed", e)
            null
        }

        when {
            product != null && product.deletedAt != null -> {
                // Soft-delete resurrection
                try {
                    repository.restoreProductKind(product.id)
                    audioFeedback.playSuccess()
                    onResult(ScanResult.Restored(product))
                } catch (e: Exception) {
                    Log.e(TAG, "restoreProductKind failed", e)
                    audioFeedback.playFailure()
                }
            }

            product != null -> {
                // Hit — apply mode-specific mutation
                try {
                    when (mode) {
                        is ScannerMode.Inventory -> repository.decrementStock(product.id)
                        is ScannerMode.Shopping -> repository.incrementPendingStock(product.id)
                    }
                    audioFeedback.playSuccess()
                    onResult(ScanResult.Hit(product))
                } catch (e: Exception) {
                    Log.e(TAG, "mutation failed", e)
                    audioFeedback.playFailure()
                }
            }

            else -> {
                // Miss — look up Open Food Facts
                audioFeedback.playFailure()
                val ofResult = openFoodFactsClient.lookupBarcode(barcode)
                val prefillName = when (ofResult) {
                    is OFResult.Hit -> ofResult.productName
                    else -> "Unknown Item"
                }
                onStateChange(ScannerState.CaptureRequired(barcode, prefillName, null))
                onResult(ScanResult.Miss(barcode))
            }
        }
    }

    suspend fun commitNewProduct(
        context: Context,
        state: ScannerState.CaptureRequired,
        mode: ScannerMode,
        onResult: (ScanResult) -> Unit,
    ) {
        val householdId = when (mode) {
            is ScannerMode.Inventory -> mode.householdId
            is ScannerMode.Shopping -> mode.householdId
        }

        val groupId = repository.ensureUnsortedGroup(householdId)

        val permanentPath: String? = state.photoPath?.let { cachePath ->
            try {
                val cacheFile = File(cachePath)
                val destDir = context.filesDir.resolve("product_images").also { it.mkdirs() }
                val destFile = File(destDir, cacheFile.name)
                cacheFile.copyTo(destFile, overwrite = true)
                destFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "photo move failed", e)
                null
            }
        }

        val productId = repository.createProductKind(
            householdId = householdId,
            name = state.prefillName,
            groupId = groupId,
            minimumStock = 1,
            barcodeNumber = state.barcode,
        )

        if (permanentPath != null) {
            try {
                repository.updateProductKind(
                    productId = productId,
                    name = state.prefillName,
                    groupId = groupId,
                    minimumStock = 1,
                    currentStock = 0,
                    imagePath = permanentPath,
                )
            } catch (e: Exception) {
                Log.e(TAG, "updateProductKind (imagePath) failed", e)
            }
        }

        val newProduct = repository.findByBarcode(state.barcode, householdId)
        if (newProduct != null) {
            audioFeedback.playSuccess()
            onResult(ScanResult.Hit(newProduct))
        }
    }
}
