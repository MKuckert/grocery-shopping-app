package de.curlybracket.grocery

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.auth.AuthState
import de.curlybracket.grocery.auth.AuthViewModel
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.ui.navigation.AppViewModel
import de.curlybracket.grocery.ui.navigation.Route
import de.curlybracket.grocery.ui.screens.SignInScreen
import de.curlybracket.grocery.ui.screens.SignUpScreen
import de.curlybracket.grocery.ui.screens.detail.DetailScreen
import de.curlybracket.grocery.ui.screens.inventory.InventoryScreen
import de.curlybracket.grocery.ui.screens.shopping.ShoppingScreen
import de.curlybracket.grocery.ui.screens.unloading.UnloadingScreen
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioFeedbackEntryPoint {
    fun audioFeedback(): AudioFeedback
}

@Composable
fun GroceryApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val navController = rememberNavController()

    val context = LocalContext.current
    val audioFeedback = EntryPointAccessors
        .fromApplication(context.applicationContext, AudioFeedbackEntryPoint::class.java)
        .audioFeedback()

    DisposableEffect(Unit) {
        onDispose { audioFeedback.release() }
    }

    val householdState by appViewModel.householdState.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authState) {
        if (authState is AuthState.SignedOut) {
            navController.navigate(Route.SignIn.path) { popUpTo(0) }
        }
    }

    LaunchedEffect(householdState?.currentState) {
        when (householdState?.currentState) {
            HouseholdState.IDLE -> navController.navigate(Route.Inventory.path) { popUpTo(0) }
            HouseholdState.SHOPPING -> navController.navigate(Route.Shopping.path) { popUpTo(0) }
            HouseholdState.UNLOADING -> navController.navigate(Route.Unloading.path) { popUpTo(0) }
            null -> { /* auth screens handle this */ }
        }
    }

    LaunchedEffect(authState, householdState) {
        if (authState == AuthState.SignedIn && householdState == null) {
            delay(5_000)
            if (householdState == null) {
                snackbarHostState.showSnackbar("Setup incomplete: contact support")
            }
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { _ ->
        NavHost(navController = navController, startDestination = Route.SignIn.path) {
            composable(Route.SignIn.path) {
                SignInScreen(authViewModel = authViewModel)
            }
            composable(Route.SignUp.path) {
                SignUpScreen(authViewModel = authViewModel)
            }
            composable(Route.Inventory.path) {
                InventoryScreen(
                    onNavigateToDetail = { productId ->
                        navController.navigate(Route.Detail(productId).path)
                    },
                )
            }
            composable(Route.Shopping.path) {
                ShoppingScreen(
                    onNavigateToDetail = { productId ->
                        navController.navigate(Route.Detail(productId).path)
                    },
                )
            }
            composable(Route.Unloading.path) {
                UnloadingScreen()
            }
            composable(Route.Detail.TEMPLATE) { backStack ->
                val productId = backStack.arguments?.getString("productId")
                    ?: error("productId argument required")
                DetailScreen(
                    productId = productId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        } // end Scaffold
    }
}
