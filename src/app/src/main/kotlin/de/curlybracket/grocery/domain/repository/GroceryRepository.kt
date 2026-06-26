package de.curlybracket.grocery.domain.repository

import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.Household
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.ProductWithGroup
import kotlinx.coroutines.flow.Flow

interface GroceryRepository {
  // Household state — the root routing signal
  fun watchHousehold(): Flow<Household?>

  // All active groups (deleted_at IS NULL)
  fun watchProductGroups(): Flow<List<ProductGroup>>

  // Inventory: active products joined with their group name
  // SINGLE denormalized JOIN query; in-memory grouping happens in InventoryViewModel via Flow.map
  fun watchProductsWithGroups(householdId: String): Flow<List<ProductWithGroup>>

  // Shopping list sections (three independent queries)
  fun watchActiveShopping(householdId: String): Flow<List<ProductKind>>

  fun watchStruckThrough(householdId: String): Flow<List<ProductKind>>

  fun watchImpulseBuys(householdId: String): Flow<List<ProductKind>>

  // Unloading: products where pending_stock > 0
  fun watchUnloadingItems(householdId: String): Flow<List<ProductKind>>

  // Detail screen
  fun watchProductKind(productId: String): Flow<ProductKind?>

  // Barcode list for detail screen
  fun watchBarcodes(productKindId: String): Flow<List<Barcode>>

  // Global search (used by SearchBar in ShoppingScreen)
  fun watchSearch(query: String, householdId: String): Flow<List<ProductKind>>

  // Barcode lookup — O(1) single-shot query
  suspend fun findByBarcode(barcodeNumber: String, householdId: String): ProductKind?

  // Mutations
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
    imagePath: String?
  )

  suspend fun addBarcode(productKindId: String, barcodeNumber: String, householdId: String)

  suspend fun deleteBarcode(barcodeId: String)

  suspend fun createProductKind(
    householdId: String,
    name: String,
    groupId: String,
    minimumStock: Int = 1,
    barcodeNumber: String
  ): String

  suspend fun restoreProductKind(productId: String)

  suspend fun ensureUnsortedGroup(householdId: String): String
}
