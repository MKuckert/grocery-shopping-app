package de.curlybracket.grocery.data.repository

import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.Household
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.ProductWithGroup
import de.curlybracket.grocery.domain.repository.GroceryRepository
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID
import javax.inject.Inject

/**
 * PowerSync repository implementation for Grocery App.
 *
 * NOTE: This is a stub implementation.  The PowerSync Kotlin SDK API has
 * complexities around cursor/row mapping that require careful API inspection.
 * The methods are structured correctly per the interface contract, but the
 * internal cursor transformation logic (particularly the `.toMap()` and cursor
 * iteration patterns) needs to match the actual PowerSync 1.13.0 SDK API.
 *
 * TODO: Update watch() and get() methods with correct PowerSync cursor API
 *       after confirming SDK documentation or examining SDK source.
 */
@ActivityRetainedScoped
internal class GroceryRepositoryImpl @Inject constructor(
  private val db: PowerSyncDatabase,
  private val connector: SupabaseConnector
) : GroceryRepository {

  override fun watchHousehold(): Flow<Household?> = emptyFlow()

  override fun watchProductGroups(): Flow<List<ProductGroup>> = emptyFlow()

  override fun watchProductsWithGroups(householdId: String): Flow<List<ProductWithGroup>> = emptyFlow()

  override fun watchActiveShopping(householdId: String): Flow<List<ProductKind>> = emptyFlow()

  override fun watchStruckThrough(householdId: String): Flow<List<ProductKind>> = emptyFlow()

  override fun watchImpulseBuys(householdId: String): Flow<List<ProductKind>> = emptyFlow()

  override fun watchUnloadingItems(householdId: String): Flow<List<ProductKind>> = emptyFlow()

  override fun watchProductKind(productId: String): Flow<ProductKind?> = emptyFlow()

  override fun watchBarcodes(productKindId: String): Flow<List<Barcode>> = emptyFlow()

  override fun watchSearch(query: String, householdId: String): Flow<List<ProductKind>> = emptyFlow()

  override suspend fun findByBarcode(barcodeNumber: String, householdId: String): ProductKind? = null

  override suspend fun setHouseholdState(state: HouseholdState) {}

  override suspend fun decrementStock(productId: String) {}

  override suspend fun setPendingStock(productId: String, value: Int) {}

  override suspend fun incrementPendingStock(productId: String) {}

  override suspend fun decrementPendingStock(productId: String) {}

  override suspend fun fulfillFull(productId: String) {}

  override suspend fun recalculateQuantityToBuy(productId: String) {}

  override suspend fun setUnloadOpen(productId: String, open: Boolean) {}

  override suspend fun submitUnloading(householdId: String) {}

  override suspend fun updateProductKind(
    productId: String,
    name: String,
    groupId: String?,
    minimumStock: Int,
    currentStock: Int,
    imagePath: String?
  ) {}

  override suspend fun addBarcode(productKindId: String, barcodeNumber: String, householdId: String) {}

  override suspend fun deleteBarcode(barcodeId: String) {}

  override suspend fun createProductKind(
    householdId: String,
    name: String,
    groupId: String,
    minimumStock: Int,
    barcodeNumber: String
  ): String = UUID.randomUUID().toString()

  override suspend fun restoreProductKind(productId: String) {}

  override suspend fun ensureUnsortedGroup(householdId: String): String = UUID.randomUUID().toString()
}
