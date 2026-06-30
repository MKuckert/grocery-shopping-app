package de.curlybracket.grocery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import de.curlybracket.grocery.auth.AuthState
import de.curlybracket.grocery.auth.AuthViewModel
import de.curlybracket.grocery.ui.screens.SignInScreen

@Composable
fun GroceryApp(
    supabase: SupabaseConnector,
    database: PowerSyncDatabase,
) {
    val authViewModel = remember { AuthViewModel(supabase) }
    val authState by authViewModel.authState.collectAsState()

    MaterialTheme {
        when (authState) {
            is AuthState.SignedIn -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Grocery App")
                }
            }
            is AuthState.SignedOut -> {
                SignInScreen(authViewModel = authViewModel)
            }
        }
    }
}
