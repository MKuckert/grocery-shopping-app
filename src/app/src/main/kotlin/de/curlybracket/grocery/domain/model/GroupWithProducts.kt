package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable
import com.powersync.db.SqlCursor
import com.powersync.db.getStringOptional

@Immutable
data class GroupWithProducts(
    val groupName: String,
    val products: List<ProductKind>,
)

@Immutable
data class ProductWithGroup(
    val product: ProductKind,
    val groupName: String?,
) {
    companion object {
        fun fromCursor(cursor: SqlCursor): ProductWithGroup =
            ProductWithGroup(
                product = ProductKind.fromCursor(cursor),
                groupName = cursor.getStringOptional("group_name"),
            )
    }
}
