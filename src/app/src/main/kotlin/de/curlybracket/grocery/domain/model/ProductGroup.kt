package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable
import com.powersync.db.SqlCursor
import com.powersync.db.getString
import com.powersync.db.getStringOptional

@Immutable
data class ProductGroup(
    val id: String,
    val householdId: String,
    val name: String,
    val deletedAt: String?,
) {
    companion object {
        fun fromCursor(cursor: SqlCursor): ProductGroup =
            ProductGroup(
                id = cursor.getString("id"),
                householdId = cursor.getString("household_id"),
                name = cursor.getString("name"),
                deletedAt = cursor.getStringOptional("deleted_at"),
            )
    }
}
