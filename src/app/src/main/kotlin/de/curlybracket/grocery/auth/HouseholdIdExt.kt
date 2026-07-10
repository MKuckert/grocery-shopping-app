package de.curlybracket.grocery.auth

import com.powersync.connector.supabase.SupabaseConnector
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun SupabaseConnector.householdIdFlow(
    scope: CoroutineScope,
): StateFlow<String?> =
    sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated ->
                    status.session.user?.appMetadata
                        ?.get("household_id")
                        ?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)
