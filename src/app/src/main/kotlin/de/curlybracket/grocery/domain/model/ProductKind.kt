package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

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
  val deletedAt: String?
) {
  companion object {
    fun fromMap(row: Map<String, Any?>): ProductKind {
      return ProductKind(
        id = row["id"] as String,
        householdId = row["household_id"] as String,
        groupId = row["group_id"] as? String,
        name = row["name"] as String,
        currentStock = (row["current_stock"] as? Long)?.toInt() ?: 0,
        minimumStock = (row["minimum_stock"] as? Long)?.toInt() ?: 0,
        quantityToBuy = (row["quantity_to_buy"] as? Long)?.toInt() ?: 0,
        pendingStock = (row["pending_stock"] as? Long)?.toInt() ?: 0,
        imagePath = row["image_path"] as? String,
        unloadOpen = ((row["unload_open"] as? Long) ?: 0L) != 0L,
        deletedAt = row["deleted_at"] as? String
      )
    }
  }
}
