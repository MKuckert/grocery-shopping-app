package de.curlybracket.grocery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import de.curlybracket.grocery.audio.AudioFeedback

/**
 * Root app composable. Currently a placeholder.
 * Will be fully implemented in Task 6: Root Navigation (NavHost).
 */
@Composable
fun GroceryApp(
    supabase: SupabaseConnector,
    database: PowerSyncDatabase,
    audioFeedback: AudioFeedback,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text("Grocery App - Under Construction")
    }
}
