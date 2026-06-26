package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ProductGroup(
  val id: String,
  val householdId: String,
  val name: String,
  val deletedAt: String?
) {
  companion object {
    fun fromMap(row: Map<String, Any?>): ProductGroup {
      return ProductGroup(
        id = row["id"] as String,
        householdId = row["household_id"] as String,
        name = row["name"] as String,
        deletedAt = row["deleted_at"] as? String
      )
    }
  }
}
