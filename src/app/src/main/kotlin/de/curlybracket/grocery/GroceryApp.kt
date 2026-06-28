package de.curlybracket.grocery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.curlybracket.grocery.auth.AuthState
import de.curlybracket.grocery.auth.AuthViewModel
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.ui.navigation.Route
import de.curlybracket.grocery.ui.screens.SignInScreen
import de.curlybracket.grocery.ui.screens.detail.DetailScreen
import de.curlybracket.grocery.ui.screens.inventory.InventoryScreen
import de.curlybracket.grocery.ui.screens.shopping.ShoppingScreen
import de.curlybracket.grocery.ui.screens.unloading.UnloadingScreen

/**
 * Root app composable. Implements the navigation hub with three main screens
 * driven by household state (IDLE -> Inventory, SHOPPING -> Shopping, UNLOADING -> Unloading).
 * Auth screens are the start destination.
 */
@Composable
fun GroceryApp() {
  val appViewModel: AppViewModel = hiltViewModel()
  val authViewModel: AuthViewModel = hiltViewModel()
  val navController = rememberNavController()
  val authState by authViewModel.authState.collectAsStateWithLifecycle()
  val householdState by appViewModel.householdState.collectAsStateWithLifecycle()

  // Root router: swap screen based on householdState (for signed-in users)
  LaunchedEffect(householdState?.currentState) {
    when (householdState?.currentState) {
      HouseholdState.IDLE -> navController.navigate(Route.Inventory.path) { popUpTo(0) }
      HouseholdState.SHOPPING -> navController.navigate(Route.Shopping.path) { popUpTo(0) }
      HouseholdState.UNLOADING -> navController.navigate(Route.Unloading.path) { popUpTo(0) }
      null -> { /* still loading or signed out */ }
    }
  }

  NavHost(navController, startDestination = Route.SignIn.path) {
    composable(Route.SignIn.path) {
      SignInScreen(
        authViewModel = authViewModel,
        onSignedIn = {
          navController.navigate(Route.Inventory.path) {
            popUpTo(Route.SignIn.path) { inclusive = true }
          }
        }
      )
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
    composable(Route.Detail.TEMPLATE) { backStack ->
      DetailScreen(onBack = { navController.popBackStack() })
    }
  }
}
