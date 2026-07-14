package de.curlybracket.grocery.scanner

import android.content.Context
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import de.curlybracket.grocery.R
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.audio.SoundEffect
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import de.curlybracket.grocery.network.OFResult
import de.curlybracket.grocery.network.OpenFoodFactsClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context,
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
                    audioFeedback.play(SoundEffect.SUCCESS)
                    _scanResultFlow.emit(
                        ScanResult.Restored(product.copy(deletedAt = null))
                    )
                } catch (e: Exception) {
                    Logger.e("restoreProductKind failed", e)
                    audioFeedback.play(SoundEffect.FAILURE)
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
                    audioFeedback.play(SoundEffect.SUCCESS)
                    _scanResultFlow.emit(ScanResult.Hit(product))
                } catch (e: Exception) {
                    Logger.e("mode-specific mutation failed", e)
                    audioFeedback.play(SoundEffect.FAILURE)
                }
            }

            else -> {
                audioFeedback.play(SoundEffect.FAILURE)
                val unknownItem = context.getString(R.string.scanner_unknown_item)
                val prefillName = try {
                    when (val result = openFoodFactsClient.lookupBarcode(barcode)) {
                        is OFResult.Hit -> result.productName
                        else -> unknownItem
                    }
                } catch (e: Exception) {
                    Logger.e("Open Food Facts lookup failed", e)
                    unknownItem
                }
                _openFoodFactsResultFlow.emit(
                    OpenFoodFactsLookupResult(barcode, prefillName)
                )
                _scanResultFlow.emit(ScanResult.Miss(barcode))
            }
        }
    }

    fun watchSearch(query: String, householdId: String) = repository.watchSearch(query, householdId)

    suspend fun linkBarcodeToProduct(barcode: String, productId: String, householdId: String) {
        try {
            repository.addBarcode(productId, barcode, householdId)
            val product = repository.watchProductKind(productId).first()
                ?: error("Product $productId not found after barcode link")
            audioFeedback.play(SoundEffect.SUCCESS)
            _scanResultFlow.emit(ScanResult.Linked(product))
        } catch (e: Exception) {
            Logger.e("linkBarcodeToProduct failed", e)
            audioFeedback.play(SoundEffect.FAILURE)
            // Diagnose whether the failure is a duplicate-barcode conflict
            val alreadyLinked = try {
                repository.findByBarcode(barcode, householdId) != null
            } catch (_: Exception) {
                false
            }
            throw if (alreadyLinked) BarcodeAlreadyLinkedException(barcode) else e
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
