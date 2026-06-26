# Task 6: Root Navigation (NavHost)

> **Depends on:** Task 5
> **Status:** [ ]

---

## Description

Replace `GroceryApp.kt` and the custom `NavController` / `Screen` sealed class with a Compose Navigation `NavHost`.

### Route definitions — `ui/navigation/Routes.kt`

```kotlin
sealed class Route(val path: String) {
  // Auth
  data object SignIn : Route("sign_in")
  data object SignUp : Route("sign_up")
  // Main screens
  data object Inventory : Route("inventory")
  data object Shopping : Route("shopping")
  data object Unloading : Route("unloading")
  // Detail (accessible from any main screen)
  data class Detail(val productId: String) : Route("detail/{productId}") {
    companion object { const val TEMPLATE = "detail/{productId}" }
  }
}
```

### `AppViewModel.kt` — `ui/navigation/`

New `@HiltViewModel` that owns `householdState` for the root composable:

```kotlin
@HiltViewModel
class AppViewModel @Inject constructor(
  private val repository: GroceryRepository
) : ViewModel() {
  val householdState: StateFlow<Household?> =
    repository.watchHousehold()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

### `GroceryApp.kt` (rewritten)

```kotlin
@Composable
fun GroceryApp() {
  val appViewModel: AppViewModel = hiltViewModel()
  val navController = rememberNavController()
  val householdState by appViewModel.householdState.collectAsStateWithLifecycle()

  // Root router: swap screen based on householdState
  LaunchedEffect(householdState?.currentState) {
    when (householdState?.currentState) {
      HouseholdState.IDLE     -> navController.navigate(Route.Inventory.path) { popUpTo(0) }
      HouseholdState.SHOPPING -> navController.navigate(Route.Shopping.path)  { popUpTo(0) }
      HouseholdState.UNLOADING-> navController.navigate(Route.Unloading.path) { popUpTo(0) }
      null -> { /* auth screens handle this */ }
    }
  }

  NavHost(navController, startDestination = Route.SignIn.path) {
    composable(Route.SignIn.path)  { SignInScreen(...) }
    composable(Route.SignUp.path)  { SignUpScreen(...) }
    composable(Route.Inventory.path)  { InventoryScreen(navController) }
    composable(Route.Shopping.path)   { ShoppingScreen(navController) }
    composable(Route.Unloading.path)  { UnloadingScreen(navController) }
    composable(Route.Detail.TEMPLATE) { backStack ->
      val productId = backStack.arguments!!.getString("productId")!!
      DetailScreen(productId, navController)
    }
  }
}
```

### `MainActivity` update
Remove `supabase` and `database` parameters from the `GroceryApp(...)` call site. `GroceryApp` now takes zero parameters — dependencies flow through Hilt.

### Navigation rule
DetailScreen is reachable from all three main screens. Back navigation returns the user to wherever they came from (default back stack behavior).

---

## Review Criteria

- `AppViewModel` is a `@HiltViewModel`; `GroceryApp` uses `hiltViewModel()` — no manual ViewModel construction.
- `GroceryApp()` takes zero parameters; `MainActivity` updated accordingly.
- `LaunchedEffect` driven by `householdState` is the only trigger for root-level screen switches.
- DetailScreen route receives `productId` as a route argument (not global state).
- Auth screens navigate to Inventory only after `householdState` stream emits.
- No residual references to the old `NavController` or `Screen` sealed class.
