package de.curlybracket.grocery.data.repository

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.powersync.db.getLong
import com.powersync.db.getString
import com.powersync.db.getStringOptional
import com.powersync.db.internal.PowerSyncTransaction
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.Household
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.domain.model.ProductGroup
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.model.ProductWithGroup
import de.curlybracket.grocery.domain.repository.GroceryRepository
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

@Singleton
internal class GroceryRepositoryImpl @Inject constructor(
    private val db: PowerSyncDatabase,
) : GroceryRepository {

    // --- Reactive Flows ---

    override fun watchHousehold(): Flow<Household?> =
        db.watch(
            sql = "SELECT id, current_state, shopping_started_at FROM households LIMIT 1",
        ) { cursor: SqlCursor ->
            Household(
                id = cursor.getString("id"),
                currentState = HouseholdState.valueOf(cursor.getString("current_state")),
                shoppingStartedAt = cursor.getStringOptional("shopping_started_at"),
            )
        }.map { it.firstOrNull() }

    override fun watchProductGroups(): Flow<List<ProductGroup>> =
        db.watch(
            sql = """
                SELECT id, household_id, name, deleted_at
                FROM product_groups
                WHERE deleted_at IS NULL
                ORDER BY name ASC
            """.trimIndent(),
        ) { cursor: SqlCursor ->
            ProductGroup(
                id = cursor.getString("id"),
                householdId = cursor.getString("household_id"),
                name = cursor.getString("name"),
                deletedAt = cursor.getStringOptional("deleted_at"),
            )
        }

    override fun watchProductsWithGroups(householdId: String): Flow<List<ProductWithGroup>> =
        db.watch(
            sql = """
                SELECT
                  pk.id, pk.household_id, pk.group_id, pk.name,
                  pk.current_stock, pk.minimum_stock, pk.quantity_to_buy,
                  pk.pending_stock, pk.image_path, pk.unload_open, pk.deleted_at,
                  pg.name AS group_name
                FROM product_kinds pk
                LEFT JOIN product_groups pg ON pk.group_id = pg.id AND pg.deleted_at IS NULL
                WHERE pk.household_id = ? AND pk.deleted_at IS NULL
                ORDER BY pg.name ASC NULLS LAST, pk.name ASC
            """.trimIndent(),
            parameters = listOf(householdId),
        ) { cursor: SqlCursor ->
            ProductWithGroup(
                product = productKindFromCursor(cursor),
                groupName = cursor.getStringOptional("group_name"),
            )
        }

    override fun watchActiveShopping(householdId: String): Flow<List<ProductKind>> =
        db.watch(
            sql = """
                SELECT id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
                FROM product_kinds
                WHERE household_id = ? AND deleted_at IS NULL
                  AND quantity_to_buy > 0 AND pending_stock < quantity_to_buy
                ORDER BY name ASC
            """.trimIndent(),
            parameters = listOf(householdId),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }

    override fun watchStruckThrough(householdId: String): Flow<List<ProductKind>> =
        db.watch(
            sql = """
                SELECT id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
                FROM product_kinds
                WHERE household_id = ? AND deleted_at IS NULL
                  AND quantity_to_buy > 0 AND pending_stock >= quantity_to_buy
                ORDER BY name ASC
            """.trimIndent(),
            parameters = listOf(householdId),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }

    override fun watchImpulseBuys(householdId: String): Flow<List<ProductKind>> =
        db.watch(
            sql = """
                SELECT id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
                FROM product_kinds
                WHERE household_id = ? AND deleted_at IS NULL
                  AND quantity_to_buy = 0 AND pending_stock > 0
                ORDER BY name ASC
            """.trimIndent(),
            parameters = listOf(householdId),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }

    override fun watchUnloadingItems(householdId: String): Flow<List<ProductKind>> =
        db.watch(
            sql = """
                SELECT id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
                FROM product_kinds
                WHERE household_id = ? AND deleted_at IS NULL AND pending_stock > 0
                ORDER BY name ASC
            """.trimIndent(),
            parameters = listOf(householdId),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }

    override fun watchProductKind(productId: String): Flow<ProductKind?> =
        db.watch(
            sql = """
                SELECT id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
                FROM product_kinds
                WHERE id = ? AND deleted_at IS NULL
            """.trimIndent(),
            parameters = listOf(productId),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }
            .map { it.firstOrNull() }

    override fun watchBarcodes(productKindId: String): Flow<List<Barcode>> =
        db.watch(
            sql = """
                SELECT id, household_id, product_kind_id, barcode_number
                FROM barcodes
                WHERE product_kind_id = ?
                ORDER BY barcode_number ASC
            """.trimIndent(),
            parameters = listOf(productKindId),
        ) { cursor: SqlCursor ->
            Barcode(
                id = cursor.getString("id"),
                householdId = cursor.getString("household_id"),
                productKindId = cursor.getString("product_kind_id"),
                barcodeNumber = cursor.getString("barcode_number"),
            )
        }

    override fun watchSearch(query: String, householdId: String): Flow<List<ProductKind>> =
        db.watch(
            sql = """
                SELECT id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
                FROM product_kinds
                WHERE household_id = ? AND deleted_at IS NULL AND name LIKE ?
                ORDER BY name ASC
            """.trimIndent(),
            parameters = listOf(householdId, "%$query%"),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }

    // --- Single-shot reads ---

    override suspend fun findByBarcode(barcodeNumber: String, householdId: String): ProductKind? =
        db.getOptional(
            sql = """
                SELECT pk.id, pk.household_id, pk.group_id, pk.name, pk.current_stock,
                       pk.minimum_stock, pk.quantity_to_buy, pk.pending_stock,
                       pk.image_path, pk.unload_open, pk.deleted_at
                FROM product_kinds pk
                INNER JOIN barcodes b ON b.product_kind_id = pk.id
                WHERE b.household_id = ? AND b.barcode_number = ? AND pk.deleted_at IS NULL
                LIMIT 1
            """.trimIndent(),
            parameters = listOf(householdId, barcodeNumber),
        ) { cursor: SqlCursor -> productKindFromCursor(cursor) }

    // --- Mutations ---

    override suspend fun setHouseholdState(state: HouseholdState) {
        db.execute(
            sql = """
                UPDATE households
                SET current_state = ?,
                    shopping_started_at = CASE WHEN ? = 'SHOPPING' THEN datetime('now') ELSE shopping_started_at END,
                    updated_at = datetime('now')
                WHERE id = (SELECT id FROM households LIMIT 1)
            """.trimIndent(),
            parameters = listOf(state.name, state.name),
        )
    }

    override suspend fun decrementStock(productId: String) {
        db.writeTransaction { tx ->
            tx.execute(
                sql = "UPDATE product_kinds SET current_stock = MAX(0, current_stock - 1), updated_at = datetime('now') WHERE id = ?",
                parameters = listOf(productId),
            )
            recalculateQuantityToBuyTx(tx, productId)
        }
    }

    override suspend fun setPendingStock(productId: String, value: Int) {
        db.execute(
            sql = "UPDATE product_kinds SET pending_stock = MAX(0, ?), updated_at = datetime('now') WHERE id = ?",
            parameters = listOf(value, productId),
        )
    }

    override suspend fun incrementPendingStock(productId: String) {
        db.execute(
            sql = "UPDATE product_kinds SET pending_stock = pending_stock + 1, updated_at = datetime('now') WHERE id = ?",
            parameters = listOf(productId),
        )
    }

    override suspend fun decrementPendingStock(productId: String) {
        db.execute(
            sql = "UPDATE product_kinds SET pending_stock = MAX(0, pending_stock - 1), updated_at = datetime('now') WHERE id = ?",
            parameters = listOf(productId),
        )
    }

    override suspend fun fulfillFull(productId: String) {
        db.execute(
            sql = "UPDATE product_kinds SET pending_stock = quantity_to_buy, updated_at = datetime('now') WHERE id = ?",
            parameters = listOf(productId),
        )
    }

    override suspend fun recalculateQuantityToBuy(productId: String) {
        db.writeTransaction { tx -> recalculateQuantityToBuyTx(tx, productId) }
    }

    override suspend fun setUnloadOpen(productId: String, open: Boolean) {
        db.execute(
            sql = "UPDATE product_kinds SET unload_open = ?, updated_at = datetime('now') WHERE id = ?",
            parameters = listOf(if (open) 1L else 0L, productId),
        )
    }

    override suspend fun submitUnloading(householdId: String) {
        db.writeTransaction { tx ->
            tx.execute(
                sql = """
                    UPDATE product_kinds
                    SET current_stock = current_stock + pending_stock, pending_stock = 0, unload_open = 0,
                        updated_at = datetime('now')
                    WHERE household_id = ? AND pending_stock > 0
                """.trimIndent(),
                parameters = listOf(householdId),
            )
            tx.execute(
                sql = """
                    UPDATE product_kinds
                    SET quantity_to_buy = MAX(0, minimum_stock - current_stock), updated_at = datetime('now')
                    WHERE household_id = ? AND deleted_at IS NULL
                """.trimIndent(),
                parameters = listOf(householdId),
            )
            tx.execute(
                sql = "UPDATE households SET current_state = 'IDLE', updated_at = datetime('now') WHERE id = (SELECT id FROM households LIMIT 1)",
            )
        }
    }

    override suspend fun updateProductKind(
        productId: String,
        name: String,
        groupId: String?,
        minimumStock: Int,
        currentStock: Int,
        imagePath: String?,
    ) {
        db.writeTransaction { tx ->
            tx.execute(
                sql = """
                    UPDATE product_kinds
                    SET name = ?, group_id = ?, minimum_stock = ?, current_stock = ?, image_path = ?,
                        updated_at = datetime('now')
                    WHERE id = ?
                """.trimIndent(),
                parameters = listOf(name, groupId, minimumStock, currentStock, imagePath, productId),
            )
            recalculateQuantityToBuyTx(tx, productId)
        }
    }

    override suspend fun addBarcode(productKindId: String, barcodeNumber: String, householdId: String) {
        db.execute(
            sql = "INSERT INTO barcodes (id, household_id, product_kind_id, barcode_number, created_at, updated_at) VALUES (?, ?, ?, ?, datetime('now'), datetime('now'))",
            parameters = listOf(UUID.randomUUID().toString(), householdId, productKindId, barcodeNumber),
        )
    }

    override suspend fun deleteBarcode(barcodeId: String) {
        db.execute(
            sql = "DELETE FROM barcodes WHERE id = ?",
            parameters = listOf(barcodeId),
        )
    }

    override suspend fun createProductKindWithBarcode(
        householdId: String,
        name: String,
        groupId: String,
        minimumStock: Int,
        barcodeNumber: String,
    ): String {
        val productId = UUID.randomUUID().toString()
        val barcodeId = UUID.randomUUID().toString()
        db.writeTransaction { tx ->
            tx.execute(
                sql = """
                    INSERT INTO product_kinds
                      (id, household_id, group_id, name, current_stock, minimum_stock,
                       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at,
                       created_at, updated_at)
                    VALUES (?, ?, ?, ?, 0, ?, ?, 0, null, 0, null, datetime('now'), datetime('now'))
                """.trimIndent(),
                parameters = listOf(productId, householdId, groupId, name, minimumStock, minimumStock),
            )
            tx.execute(
                sql = "INSERT INTO barcodes (id, household_id, product_kind_id, barcode_number, created_at, updated_at) VALUES (?, ?, ?, ?, datetime('now'), datetime('now'))",
                parameters = listOf(barcodeId, householdId, productId, barcodeNumber),
            )
        }
        return productId
    }

    override suspend fun restoreProductKind(productId: String) {
        db.writeTransaction { tx ->
            tx.execute(
                sql = "UPDATE product_kinds SET deleted_at = NULL, updated_at = datetime('now') WHERE id = ?",
                parameters = listOf(productId),
            )
            recalculateQuantityToBuyTx(tx, productId)
        }
    }

    override suspend fun ensureUnsortedGroup(householdId: String): String {
        val existing = db.getOptional(
            sql = """
                SELECT id FROM product_groups
                WHERE household_id = ? AND name = 'Unsorted' AND deleted_at IS NULL
                LIMIT 1
            """.trimIndent(),
            parameters = listOf(householdId),
        ) { cursor: SqlCursor -> cursor.getString("id") }

        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        db.execute(
            sql = "INSERT INTO product_groups (id, household_id, name, deleted_at, created_at, updated_at) VALUES (?, ?, 'Unsorted', NULL, datetime('now'), datetime('now'))",
            parameters = listOf(newId, householdId),
        )
        return newId
    }

    // --- Private helpers ---

    private fun recalculateQuantityToBuyTx(tx: PowerSyncTransaction, productId: String) {
        tx.execute(
            sql = "UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock), updated_at = datetime('now') WHERE id = ?",
            parameters = listOf(productId),
        )
    }

    private fun productKindFromCursor(cursor: SqlCursor): ProductKind = ProductKind(
        id = cursor.getString("id"),
        householdId = cursor.getString("household_id"),
        groupId = cursor.getStringOptional("group_id"),
        name = cursor.getString("name"),
        currentStock = cursor.getLong("current_stock").toInt(),
        minimumStock = cursor.getLong("minimum_stock").toInt(),
        quantityToBuy = cursor.getLong("quantity_to_buy").toInt(),
        pendingStock = cursor.getLong("pending_stock").toInt(),
        imagePath = cursor.getStringOptional("image_path"),
        unloadOpen = cursor.getLong("unload_open") != 0L,
        deletedAt = cursor.getStringOptional("deleted_at"),
    )
}
