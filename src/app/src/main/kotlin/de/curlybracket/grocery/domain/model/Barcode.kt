package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable
import com.powersync.db.SqlCursor
import com.powersync.db.getString

@Immutable
data class Barcode(
    val id: String,
    val householdId: String,
    val productKindId: String,
    val barcodeNumber: String,
) {
    companion object {
        fun fromCursor(cursor: SqlCursor): Barcode =
            Barcode(
                id = cursor.getString("id"),
                householdId = cursor.getString("household_id"),
                productKindId = cursor.getString("product_kind_id"),
                barcodeNumber = cursor.getString("barcode_number"),
            )
    }
}
