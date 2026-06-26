package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Barcode(
  val id: String,
  val householdId: String,
  val productKindId: String,
  val barcodeNumber: String
) {
  companion object {
    fun fromMap(row: Map<String, Any?>): Barcode {
      return Barcode(
        id = row["id"] as String,
        householdId = row["household_id"] as String,
        productKindId = row["product_kind_id"] as String,
        barcodeNumber = row["barcode_number"] as String
      )
    }
  }
}
