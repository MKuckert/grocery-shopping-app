package de.curlybracket.grocery

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.auth.AuthState
import de.curlybracket.grocery.auth.AuthViewModel
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.ui.navigation.Route
import de.curlybracket.grocery.ui.screens.SignInScreen
import de.curlybracket.grocery.ui.screens.detail.DetailScreen
import de.curlybracket.grocery.ui.screens.inventory.InventoryScreen
import de.curlybracket.grocery.ui.screens.shopping.ShoppingScreen
import de.curlybracket.grocery.ui.screens.unloading.UnloadingScreen
import kotlinx.coroutines.delay

/**
 * Root app composable. Implements the navigation hub with three main screens
 * driven by household state (IDLE -> Inventory, SHOPPING -> Shopping, UNLOADING -> Unloading).
 * Auth screens are the start destination.
 */
@Composable
fun GroceryApp(audioFeedback: AudioFeedback? = null) {
  val appViewModel: AppViewModel = hiltViewModel()
  val authViewModel: AuthViewModel = hiltViewModel()
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val authState by authViewModel.authState.collectAsStateWithLifecycle()
  val householdState by appViewModel.householdState.collectAsStateWithLifecycle()

  // Release AudioFeedback SoundPool when app is disposed
  DisposableEffect(Unit) {
    onDispose { audioFeedback?.release() }
  }

  // Route to SignIn when signed out
  LaunchedEffect(authState) {
    if (authState == AuthState.SignedOut) {
      navController.navigate(Route.SignIn.path) { popUpTo(0) }
    }
  }

  // Null-household guard: show warning if still null 5 seconds after sign-in
  LaunchedEffect(authState, householdState) {
    if (authState == AuthState.SignedIn && householdState == null) {
      delay(5_000)
      if (householdState == null) {
        snackbarHostState.showSnackbar("Setup incomplete: contact support")
      }
    }
  }

  // Root router: swap screen based on householdState (for signed-in users)
  LaunchedEffect(householdState?.currentState) {
    when (householdState?.currentState) {
      HouseholdState.IDLE -> navController.navigate(Route.Inventory.path) { popUpTo(0) }
      HouseholdState.SHOPPING -> navController.navigate(Route.Shopping.path) { popUpTo(0) }
      HouseholdState.UNLOADING -> navController.navigate(Route.Unloading.path) { popUpTo(0) }
      null -> { /* still loading or signed out */ }
    }
  }

  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) {
    NavHost(navController, startDestination = Route.SignIn.path) {
      composable(Route.SignIn.path) {
        SignInScreen(authViewModel = authViewModel)
      }
      composable(Route.Inventory.path) {
        InventoryScreen(
          onNavigateToDetail = { productId ->
            navController.navigate(Route.Detail(productId).path)
          }
        )
      }
      composable(Route.Shopping.path) {
        ShoppingScreen(
          onNavigateToDetail = { productId ->
            navController.navigate(Route.Detail(productId).path)
          }
        )
      }
      composable(Route.Unloading.path) {
        UnloadingScreen(
          onNavigateToDetail = { productId ->
            navController.navigate(Route.Detail(productId).path)
          }
        )
      }
      composable(Route.Detail.TEMPLATE) {
        DetailScreen(onBack = { navController.popBackStack() })
      }
    }
  }
}
