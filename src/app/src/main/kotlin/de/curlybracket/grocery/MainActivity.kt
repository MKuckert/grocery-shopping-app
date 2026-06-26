package de.curlybracket.grocery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.powersync.connector.supabase.SupabaseConnector
import dagger.hilt.android.AndroidEntryPoint
import de.curlybracket.grocery.sync.SyncService
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var supabase: SupabaseConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the foreground sync service once the user is authenticated
        lifecycleScope.launch {
            supabase.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    startForegroundService(
                        Intent(this@MainActivity, SyncService::class.java)
                    )
                }
            }
        }

        setContent {
            GroceryApp()
        }
    }
}
