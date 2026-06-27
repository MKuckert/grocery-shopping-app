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
import de.curlybracket.grocery.auth.AuthViewModel
import de.curlybracket.grocery.domain.model.HouseholdState
import de.curlybracket.grocery.ui.navigation.Route
import de.curlybracket.grocery.ui.screens.SignInScreen
import de.curlybracket.grocery.ui.screens.inventory.InventoryScreen

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
  val householdState by appViewModel.householdState.collectAsStateWithLifecycle()

  // Root router: swap screen based on householdState
  LaunchedEffect(householdState?.currentState) {
    when (householdState?.currentState) {
      HouseholdState.IDLE -> navController.navigate(Route.Inventory.path) { popUpTo(0) }
      HouseholdState.SHOPPING -> navController.navigate(Route.Shopping.path) { popUpTo(0) }
      HouseholdState.UNLOADING -> navController.navigate(Route.Unloading.path) { popUpTo(0) }
      null -> { /* auth screens handle this */ }
    }
  }

  NavHost(navController, startDestination = Route.SignIn.path) {
    composable(Route.SignIn.path) {
      SignInScreen(
        authViewModel = authViewModel,
        onSignUpClicked = { navController.navigate(Route.SignUp.path) }
      )
    }
    composable(Route.SignUp.path) {
      // TODO: SignUpScreen to be implemented
    }
    composable(Route.Inventory.path) {
      InventoryScreen(
        onNavigateToDetail = { productId ->
          navController.navigate(Route.Detail(productId).path)
        }
      )
    }
    composable(Route.Shopping.path) {
      // TODO: ShoppingScreen to be implemented
    }
    composable(Route.Unloading.path) {
      // TODO: UnloadingScreen to be implemented
    }
    composable(Route.Detail.TEMPLATE) { backStack ->
      val productId = backStack.arguments!!.getString("productId")!!
      // TODO: DetailScreen to be implemented
    }
  }
}
