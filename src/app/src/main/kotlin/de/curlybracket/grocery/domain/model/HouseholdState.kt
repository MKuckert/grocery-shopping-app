package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable
import com.powersync.db.SqlCursor
import com.powersync.db.getString
import com.powersync.db.getStringOptional

enum class HouseholdState { IDLE, SHOPPING, UNLOADING }

@Immutable
data class Household(
    val id: String,
    val currentState: HouseholdState,
    val shoppingStartedAt: String?,
) {
    companion object {
        fun fromCursor(cursor: SqlCursor): Household =
            Household(
                id = cursor.getString("id"),
                currentState = HouseholdState.valueOf(cursor.getString("current_state")),
                shoppingStartedAt = cursor.getStringOptional("shopping_started_at"),
            )
    }
}
