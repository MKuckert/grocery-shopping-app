package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable
import com.powersync.db.SqlCursor
import com.powersync.db.getBoolean
import com.powersync.db.getLong
import com.powersync.db.getString
import com.powersync.db.getStringOptional

@Immutable
data class ProductKind(
    val id: String,
    val householdId: String,
    val groupId: String?,
    val name: String,
    val currentStock: Int,
    val minimumStock: Int,
    val quantityToBuy: Int,
    val pendingStock: Int,
    val imagePath: String?,
    val unloadOpen: Boolean,
    val deletedAt: String?,
) {
    companion object {
        fun fromCursor(cursor: SqlCursor): ProductKind =
            ProductKind(
                id = cursor.getString("id"),
                householdId = cursor.getString("household_id"),
                groupId = cursor.getStringOptional("group_id"),
                name = cursor.getString("name"),
                currentStock = cursor.getLong("current_stock").toInt(),
                minimumStock = cursor.getLong("minimum_stock").toInt(),
                quantityToBuy = cursor.getLong("quantity_to_buy").toInt(),
                pendingStock = cursor.getLong("pending_stock").toInt(),
                imagePath = cursor.getStringOptional("image_path"),
                unloadOpen = cursor.getBoolean("unload_open"),
                deletedAt = cursor.getStringOptional("deleted_at"),
            )
    }
}
