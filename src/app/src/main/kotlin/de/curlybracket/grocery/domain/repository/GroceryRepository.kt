package de.curlybracket.grocery.domain.repository

import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.Household
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.ProductWithGroup
import kotlinx.coroutines.flow.Flow

interface GroceryRepository {

    // --- Reactive Flows ---

    fun watchHousehold(): Flow<Household?>

    fun watchProductGroups(): Flow<List<ProductGroup>>

    fun watchProductsWithGroups(householdId: String): Flow<List<ProductWithGroup>>

    fun watchActiveShopping(householdId: String): Flow<List<ProductKind>>

    fun watchStruckThrough(householdId: String): Flow<List<ProductKind>>

    fun watchImpulseBuys(householdId: String): Flow<List<ProductKind>>

    fun watchUnloadingItems(householdId: String): Flow<List<ProductKind>>

    fun watchProductKind(productId: String): Flow<ProductKind?>

    fun watchBarcodes(productKindId: String): Flow<List<Barcode>>

    fun watchSearch(query: String, householdId: String): Flow<List<ProductKind>>

    // --- Single-shot reads ---

    suspend fun findByBarcode(barcodeNumber: String, householdId: String): ProductKind?

    // --- Mutations ---

    suspend fun setHouseholdState(state: HouseholdState)

    suspend fun decrementStock(productId: String)

    suspend fun setPendingStock(productId: String, value: Int)

    suspend fun incrementPendingStock(productId: String)

    suspend fun decrementPendingStock(productId: String)

    suspend fun fulfillFull(productId: String)

    suspend fun recalculateQuantityToBuy(productId: String)

    suspend fun setUnloadOpen(productId: String, open: Boolean)

    suspend fun submitUnloading(householdId: String)

    suspend fun updateProductKind(
        productId: String,
        name: String,
        groupId: String?,
        minimumStock: Int,
        currentStock: Int,
        imagePath: String?,
    )

    suspend fun addBarcode(productKindId: String, barcodeNumber: String, householdId: String)

    suspend fun deleteBarcode(barcodeId: String)

    suspend fun createProductKind(
        householdId: String,
        name: String,
        groupId: String,
        minimumStock: Int = 1,
        barcodeNumber: String,
    ): String

    suspend fun restoreProductKind(productId: String)

    suspend fun ensureUnsortedGroup(householdId: String): String
}
