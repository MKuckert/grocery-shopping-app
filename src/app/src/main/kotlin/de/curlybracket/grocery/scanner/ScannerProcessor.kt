package de.curlybracket.grocery.scanner

import android.content.Context
import co.touchlab.kermit.Logger
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import de.curlybracket.grocery.network.OFResult
import de.curlybracket.grocery.network.OpenFoodFactsClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class OpenFoodFactsLookupResult(
    val barcode: String,
    val prefillName: String,
)

@Singleton
class ScannerProcessor @Inject constructor(
    private val repository: GroceryRepository,
    private val audioFeedback: AudioFeedback,
    private val openFoodFactsClient: OpenFoodFactsClient,
) {

    private val _scanResultFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
    val scanResultFlow = _scanResultFlow.asSharedFlow()

    private val _openFoodFactsResultFlow =
        MutableSharedFlow<OpenFoodFactsLookupResult>(extraBufferCapacity = 1)
    val openFoodFactsResultFlow = _openFoodFactsResultFlow.asSharedFlow()

    suspend fun processScan(barcode: String, mode: ScannerMode) {
        val householdId = mode.householdId

        val product = try {
            repository.findByBarcode(barcode, householdId)
        } catch (e: Exception) {
            Logger.e("findByBarcode failed", e)
            null
        }

        when {
            product != null && product.deletedAt != null -> {
                try {
                    repository.restoreProductKind(product.id)
                    repository.recalculateQuantityToBuy(product.id)
                    audioFeedback.playSuccess()
                    _scanResultFlow.emit(
                        ScanResult.Restored(product.copy(deletedAt = null))
                    )
                } catch (e: Exception) {
                    Logger.e("restoreProductKind failed", e)
                    audioFeedback.playFailure()
                }
            }

            product != null -> {
                try {
                    when (mode) {
                        is ScannerMode.DecrementStock ->
                            repository.decrementStock(product.id)
                        is ScannerMode.IncrementPendingStock ->
                            repository.incrementPendingStock(product.id)
                    }
                    audioFeedback.playSuccess()
                    _scanResultFlow.emit(ScanResult.Hit(product))
                } catch (e: Exception) {
                    Logger.e("mode-specific mutation failed", e)
                    audioFeedback.playFailure()
                }
            }

            else -> {
                audioFeedback.playFailure()
                val prefillName = try {
                    when (val result = openFoodFactsClient.lookupBarcode(barcode)) {
                        is OFResult.Hit -> result.productName
                        else -> "Unknown Item"
                    }
                } catch (e: Exception) {
                    Logger.e("Open Food Facts lookup failed", e)
                    "Unknown Item"
                }
                _openFoodFactsResultFlow.emit(
                    OpenFoodFactsLookupResult(barcode, prefillName)
                )
                _scanResultFlow.emit(ScanResult.Miss(barcode))
            }
        }
    }

    suspend fun createNewProduct(
        context: Context,
        barcode: String,
        productName: String,
        householdId: String,
        photoPath: String?,
    ): ProductKind? {
        return try {
            val groupId = repository.ensureUnsortedGroup(householdId)

            repository.createProductKindWithBarcode(
                householdId = householdId,
                name = productName,
                groupId = groupId,
                minimumStock = 1,
                barcodeNumber = barcode,
            )

            val permanentPath: String? = photoPath?.let { cachePath ->
                try {
                    val cacheFile = File(cachePath)
                    val destDir = context.filesDir
                        .resolve("product_images").also { it.mkdirs() }
                    val destFile = File(destDir, cacheFile.name)
                    cacheFile.copyTo(destFile, overwrite = true)
                    destFile.absolutePath
                } catch (e: Exception) {
                    Logger.e("Photo move failed", e)
                    null
                }
            }

            val newProduct = repository.findByBarcode(barcode, householdId)

            if (permanentPath != null && newProduct != null) {
                try {
                    repository.updateProductKind(
                        productId = newProduct.id,
                        name = productName,
                        groupId = groupId,
                        minimumStock = 1,
                        currentStock = 0,
                        imagePath = permanentPath,
                    )
                } catch (e: Exception) {
                    Logger.e("updateProductKind (imagePath) failed", e)
                }
            }

            newProduct
        } catch (e: Exception) {
            Logger.e("createNewProduct failed", e)
            null
        }
    }
}
