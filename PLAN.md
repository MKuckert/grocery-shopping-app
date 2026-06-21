# Plan: Android Project Initial Setup

## Objective

Bootstrap a fully buildable and runnable Android application skeleton for the household grocery/inventory app. The deliverable is a single-module Kotlin Android project with all foundational layers in place: Gradle infrastructure, version catalog, Hilt DI wiring, PowerSync schema, Supabase auth connector, root navigation driven by `households.current_state`, and empty screen shells for all five screens. No business logic beyond login and state-driven routing is in scope for this plan.

---

## Requirements & Decisions

- **Application ID:** `de.curlybracket.grocery`
- **Min / Compile SDK:** API 35 (Android 15)
- **Target SDK:** API 37 (ANdroid 17)
- **Gradle DSL:** Kotlin DSL (`.kts`) + `gradle/libs.versions.toml` Version Catalog
- **Frameworks:** Jetpack Compose (UI), Hilt (DI), CameraX + ML Kit (barcode scanning), PowerSync 1.13.0 (local-first sync), Supabase Kotlin SDK (backend/auth)
- **Architecture Pattern:** MVI — ViewModels expose `StateFlow<UiState>`, UI sends sealed `Intent/Action` events
- **Module Structure:** Single module (`app/`)
- **Chosen Libraries:**
  - `com.powersync:core:1.13.0`
  - `com.powersync:connector-supabase:1.13.0`
  - Supabase Kotlin SDK (auth client — exact BOM version to be resolved at build time from Maven Central; artifact: `io.github.jan-tennermann:supabase-kt-bom`)
  - CameraX (core, lifecycle, view) — latest stable from `androidx.camera`
  - ML Kit Barcode Scanning — `com.google.mlkit:barcode-scanning`
  - Navigation Compose — `androidx.navigation:navigation-compose`
  - Kotlin Coroutines + Flow — `org.jetbrains.kotlinx:kotlinx-coroutines-android`
  - Kotlin Serialization — `org.jetbrains.kotlinx:kotlinx-serialization-json`
  - Hilt 2.55+ (use KSP plugin, not kapt — supported since Hilt 2.51)
  - Compose BOM (latest stable at build time)
- **Secrets Strategy:** `local.properties` values read by `app/build.gradle.kts` via `project.findProperty()` and injected into `BuildConfig` string fields. `local.properties` is gitignored.

### Error Handling Strategy

| Failure Scenario                                 | Handling                                                                                                                                                                                                   |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wrong credentials at login                       | `AuthRepository.signIn()` returns `Result.failure(e)`; ViewModel emits `LoginUiState.Error(e.message)` displayed below the submit button                                                                   |
| JWT expiry during active session                 | `SupabasePowerSyncConnector.fetchCredentials()` calls `supabase.auth.refreshCurrentSession()`; on refresh failure, emits a `SESSION_EXPIRED` event that `AppNavHost` observes to re-route to `LoginScreen` |
| `uploadData()` network error                     | Re-throw the exception so PowerSync SDK retries with its built-in exponential back-off                                                                                                                     |
| Supabase secrets missing from `local.properties` | `BuildConfig` fields are empty strings; `DatabaseModule` guards both values with `require(value.isNotBlank())` at injection time, surfacing the misconfiguration immediately at app startup                |
| PowerSync offline on cold start                  | `households` table is empty; `HouseholdStateRouter` handles `null`/empty query result by displaying a loading indicator instead of crashing                                                                |
| Concurrent JWT refresh race                      | A `Mutex` inside `SupabasePowerSyncConnector` ensures only one coroutine refreshes the token at a time; others `withLock` wait for the result                                                              |

---

## Implementation Steps

> Status Markers: [ ] Open, [/] In Progress, [x] Completed (set after accepted review only!)

- [x] **Task 1: Root Gradle Configuration**
  - **Description:** Create or modify the following files in `src`:
    - `settings.gradle.kts` — project name `"GroceryShopping"`, `pluginManagement` block declaring `gradlePluginPortal()`, `google()`, `mavenCentral()` repos, and `dependencyResolutionManagement` pointing all repositories to the same three remotes.
    - `build.gradle.kts` — declares all top-level plugins with `apply false` only (no `dependencies {}` block at root level).
    - `gradle.properties` — sets `android.useAndroidX=true`, `kotlin.code.style=official`, `android.nonTransitiveRClass=true`, `org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC`.
    - `gradle/wrapper/gradle-wrapper.properties` — pins Gradle wrapper to `9.2.1`.
  - **Review Criteria:** `android_gradlew('help')` runs to completion without error. `settings.gradle.kts` resolves all plugin IDs to be declared in Task 2.

- [x] **Task 2: Gradle Version Catalog (`src/gradle/libs.versions.toml`)**
  - **Description:** Centralise ALL dependency and plugin versions in the `[versions]` table. Minimum required entries: `agp` (9.2.1), `kotlin` (2.1.x), `composeBom`, `hilt`, `powersync`, `supabase`, `cameraX`, `mlkitBarcode`, `navigationCompose`, `coroutines`, `serialization`, `ksp`. In `[libraries]`, declare every individual artifact using the version refs (e.g., `powersync-core = { module = "com.powersync:core", version.ref = "powersync" }`). In `[plugins]`, declare: `android-application`, `kotlin-android`, `kotlin-compose`, `kotlin-serialization`, `hilt-android`, `ksp`. Create two `[bundles]`: `compose` (ui, material3, tooling-preview, activity) and `camerax` (core, lifecycle, view).
  - **Review Criteria:** No raw version string appears anywhere in any `build.gradle.kts` file. All `libs.*` accessors resolve without IDE errors. `android_gradlew(':app:dependencies')` outputs the full resolved dependency tree.

- [x] **Task 3: App Module Build Script (`src/app/build.gradle.kts`)**
  - **Description:** Apply plugins from version catalog: `alias(libs.plugins.android.application)`, `alias(libs.plugins.kotlin.android)`, `alias(libs.plugins.kotlin.compose)`, `alias(libs.plugins.kotlin.serialization)`, `alias(libs.plugins.hilt.android)`, `alias(libs.plugins.ksp)`. Configure `android {}` block: `compileSdk = 37`, `minSdk = 35`, `targetSdk = 37`, `applicationId = "de.curlybracket.grocery"`, `versionCode = 1`, `versionName = "0.1.0"`, `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`. Enable `buildFeatures { compose = true; buildConfig = true }`. Add `BuildConfig` string fields:
    ```kotlin
    buildConfigField("String", "SUPABASE_URL", "\"${project.findProperty("supabase.url") ?: ""}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("supabase.anon.key") ?: ""}\"")
    buildConfigField("String", "POWERSYNC_URL", "\"${project.findProperty("powersync.url") ?: ""}\"")
    ```
    Add all `dependencies {}` using version catalog accessors (bundles for compose + camerax, individual entries for hilt, powersync, navigation, coroutines, serialization, mlkit). Use `ksp(libs.hilt.compiler)` (not `kapt`).
  - **Review Criteria:** `android_gradlew(':app:assembleDebug')` succeeds with zero warnings about deprecated kapt usage. `BuildConfig.SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `POWERSYNC_URL` are present and accessible in Kotlin code.

- [x] **Task 4: Secrets Infrastructure**
  - **Description:** Add three placeholder key–value entries to `local.properties` (developer fills in real values):
    ```
    supabase.url=
    supabase.anon.key=
    powersync.url=
    ```
    Verify `.gitignore` (already present in the repo) includes `local.properties` and `*.jks`. Document the three required keys in `README.md` under a new "Local Setup" section.
  - **Review Criteria:** `git status` does not list `local.properties`. Deleting `local.properties` and running `android_gradlew(':app:assembleDebug')` produces empty-string `BuildConfig` fields (not a compile error). `README.md` local setup section is present.

- [x] **Task 5: Android Manifest**
  - **Description:** Modify `src/app/src/main/AndroidManifest.xml`. Declare `<uses-permission android:name="android.permission.INTERNET"/>` and `<uses-permission android:name="android.permission.CAMERA"/>`. On the `<application>` element: `android:name=".GroceryApplication"`, `android:label="@string/app_name"`, `android:theme="@style/Theme.Grocery"` (Material3 theme, declared in Theme.kt via Compose — the XML theme only needs to be a plain `AppCompat` or `Theme.Material3` parent to avoid window background flicker). Declare `MainActivity` with `android:exported="true"` and the `MAIN`/`LAUNCHER` intent filter. Add `<meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="barcode_ui"/>` inside `<application>` for ML Kit pre-download.
  - **Review Criteria:** `android_gradlew(':app:processDebugManifest')` succeeds. CAMERA permission is present. Application `android:name` matches the Kotlin class name exactly.

- [x] **Task 6: Application Class + Hilt DI Modules**
  - **Description:**
    - `GroceryApplication.kt`: `@HiltAndroidApp class GroceryApplication : Application()`. No other logic needed at this stage.
    - `src/app/src/main/di/DatabaseModule.kt`: `@Module @InstallIn(SingletonComponent::class)` object. Provides `@Singleton PowerSyncDatabase` — instantiated with the `AppSchema` (from Task 7) and the `SupabasePowerSyncConnector`. Provides `@Singleton SupabasePowerSyncConnector` — instantiated with the injected Supabase auth client.
    - `src/app/src/main/di/NetworkModule.kt`: `@Module @InstallIn(SingletonComponent::class)` object. Provides `@Singleton` Supabase client: reads `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`; calls `require(url.isNotBlank()) { "SUPABASE_URL is not configured in local.properties" }` and same for key before constructing the client. Install the `Auth` plugin.
  - **Review Criteria:** App compiles; any `@HiltViewModel` that `@Inject`s `PowerSyncDatabase` or the Supabase auth client resolves at compile time. App crashes at launch with a clear `IllegalStateException` message (not `NullPointerException`) when secrets are blank.

- [ ] **Task 7: PowerSync Schema Definition (`src/app/src/main/data/db/AppSchema.kt`)**
  - **Description:** Define `val AppSchema = Schema(listOf(...))` with four `Table` entries, mapping every column from `DATABASE.md §1.1`. Type mapping rules: UUID/TEXT/TIMESTAMP columns → `Column.text()`, INTEGER columns → `Column.integer()`, BOOLEAN columns → `Column.integer()` (SQLite stores booleans as `0`/`1`).

    | Table            | Columns                                                                                                                                                                                                                                  |
    | ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
    | `households`     | `current_state` (text), `shopping_started_at` (text)                                                                                                                                                                                     |
    | `product_groups` | `household_id` (text), `name` (text), `deleted_at` (text)                                                                                                                                                                                |
    | `product_kinds`  | `household_id` (text), `group_id` (text), `name` (text), `current_stock` (integer), `minimum_stock` (integer), `quantity_to_buy` (integer), `pending_stock` (integer), `image_path` (text), `unload_open` (integer), `deleted_at` (text) |
    | `barcodes`       | `household_id` (text), `product_kind_id` (text), `barcode_number` (text)                                                                                                                                                                 |

  - **Review Criteria:** `AppSchema` compiles. Table and column names exactly match `DATABASE.md §1.1`. No column is omitted.

- [ ] **Task 8: Supabase + PowerSync Backend Connector (`src/app/src/main/data/remote/SupabasePowerSyncConnector.kt`)**
  - **Description:** `class SupabasePowerSyncConnector @Inject constructor(private val supabase: SupabaseClient) : PowerSyncBackendConnector`. Fields: `private val refreshMutex = Mutex()`.
    - `fetchCredentials()`: wrap body in `refreshMutex.withLock { ... }`. Call `supabase.auth.currentSessionOrNull()`. If session is expired, call `supabase.auth.refreshCurrentSession()`. Construct and return `PowerSyncCredentials(endpoint = BuildConfig.POWERSYNC_URL, token = session.accessToken)`. If no session exists, throw `IllegalStateException("No active Supabase session")`.
    - `uploadData(database: PowerSyncDatabase)`: call `database.getCrudBatch()`. If `null` (no pending writes), return immediately. Iterate batch entries; for each, match `CrudEntry.op` to `INSERT/UPDATE/DELETE` and call the corresponding Supabase Postgrest table function. On `RestException`, log the error and re-throw so PowerSync SDK handles retry. On success, call `batch.complete(null)`.
  - **Review Criteria:** Class compiles and is injectable via Hilt. `fetchCredentials()` returns a non-null `PowerSyncCredentials` when a valid session exists. `uploadData()` returns without error when `getCrudBatch()` returns `null`. Mutex prevents concurrent refresh calls (verifiable via unit test stub).

- [ ] **Task 9: Auth Repository, MVI Contract & Login Screen**
  - **Description:**
    - `src/app/src/main/data/repository/AuthRepository.kt` (`@Inject constructor(private val supabase: SupabaseClient)`): `suspend fun signIn(email: String, password: String): Result<Unit>` — wraps `supabase.auth.signInWith(Email) { this.email = email; password = password }` in `runCatching`. `suspend fun signOut(): Result<Unit>` — wraps `supabase.auth.signOut()`. `fun sessionFlow(): Flow<CurrentSession?>` — emits from `supabase.auth.sessionStatus`.
    - `src/app/src/main/ui/auth/LoginContract.kt`: `sealed class LoginUiState` (`Idle`, `Loading`, `Error(val message: String)`, `Success`). `sealed class LoginIntent` (`Submit(val email: String, val password: String)`, `ClearError`).
    - `src/app/src/main/ui/auth/LoginViewModel.kt`: `@HiltViewModel`, `StateFlow<LoginUiState>` (starts at `Idle`). `fun onIntent(intent: LoginIntent)` — on `Submit`: guard empty fields with `Error("Email and password are required")`; otherwise emit `Loading`, call `authRepository.signIn()`; emit `Success` or `Error(e.message)`. On `ClearError`: emit `Idle`.
    - `src/app/src/main/ui/auth/LoginScreen.kt`: Stateless composable `fun LoginScreen(state: LoginUiState, onIntent: (LoginIntent) -> Unit)`. Contains: `OutlinedTextField` for email, `OutlinedTextField` for password (with `KeyboardType.Password` and `PasswordVisualTransformation`), `Button("Sign In", enabled = state != Loading, onClick = { onIntent(Submit(email, password)) })`, and `Text(error.message, color = MaterialTheme.colorScheme.error)` shown only when state is `Error`.
  - **Review Criteria:** `LoginScreen` has an Android Studio `@Preview` that renders without crash. Submitting blank fields emits `Error` (no crash). `LoginViewModel` compiles with Hilt injection. `sessionFlow()` emits `null` when not authenticated.

- [ ] **Task 10: Root Navigation Host & MainActivity**
  - **Description:**
    - `src/app/src/main/ui/navigation/NavRoutes.kt`: `sealed class Screen(val route: String)` — `Login`, `Loading`, `Inventory`, `Shopping`, `Unloading`, `Detail(val productKindId: String)` (route = `"detail/{productKindId}"`).
    - `src/app/src/main/ui/navigation/HouseholdStateRouter.kt`: A `@Singleton` helper class (injected into `AppNavHost`'s ViewModel or called as a pure function) that maps a nullable `String?` households state value to a `Screen`:
      - `null` / empty result (no rows synced yet) → `Screen.Loading`
      - `"IDLE"` → `Screen.Inventory`
      - `"SHOPPING"` → `Screen.Shopping`
      - `"UNLOADING"` → `Screen.Unloading`
      - any unknown value → `Screen.Loading` (defensive fallback, logs a warning)
    - `src/app/src/main/ui/navigation/AppNavHost.kt`: `@Composable fun AppNavHost(navController: NavHostController, startDestination: String)`. The `NavHost` registers all six destinations (including `Screen.Loading`). Navigation authority is **two-layer**:
      1. **Auth layer (outer):** `MainActivity` determines whether to show `Screen.Login` or proceed into the app graph. Auth is the gate — the data-state layer never fires until a valid session exists.
      2. **Data-state layer (inner, `AppNavHost`):** A `LaunchedEffect(Unit)` inside `AppNavHost` collects `PowerSyncDatabase.watch("SELECT current_state FROM households LIMIT 1")` as a `Flow<String?>`. On each emission it calls `HouseholdStateRouter.route(value)` and navigates: `navController.navigate(screen.route) { popUpTo(navController.graph.startDestinationRoute!!) { inclusive = true } }`. This replaces the back stack predictably using the named start destination rather than a magic index.
      - The data-state `LaunchedEffect` is only active after the user is authenticated. The start destination passed to `AppNavHost` from `MainActivity` is always `Screen.Loading.route` (not `Inventory`) once a session exists — this ensures the data-state layer immediately takes full routing authority and the "empty households" case (first sync, no rows yet) is correctly handled by showing `Screen.Loading` rather than an undefined route.
    - `LoadingScreen.kt` (`src/app/src/main/ui/loading/`): Simple full-screen `CircularProgressIndicator` centred composable. No ViewModel needed.
    - `MainActivity.kt`: `@AndroidEntryPoint class MainActivity : ComponentActivity()`. In `onCreate`: call `WindowCompat.setDecorFitsSystemWindows(window, false)` for edge-to-edge. Determine `startDestination`: if `supabase.auth.currentSessionOrNull() == null` use `Screen.Login.route` else `Screen.Loading.route` (hands off to data-state layer immediately). Call `setContent { GroceryTheme { AppNavHost(rememberNavController(), startDestination) } }`. The `login` destination composable in `AppNavHost`, upon observing `LoginUiState.Success`, calls `navController.navigate(Screen.Loading.route) { popUpTo(navController.graph.startDestinationRoute!!) { inclusive = true } }` to hand routing to the data-state layer and remove `Login` from the back stack.
  - **Review Criteria:** Cold-starting the app without a session lands on `LoginScreen`. Cold-starting with a valid session lands on `LoadingScreen` briefly, then transitions to `InventoryScreen` once PowerSync emits a row. Manually writing `SHOPPING` to the local SQLite `households` table (via a DB browser tool) while the app is open triggers navigation to `ShoppingScreen`. The Back button never returns to `LoginScreen` after authentication. Navigating when `households` is empty shows `LoadingScreen` (not a crash).

- [ ] **Task 11: Screen Shell Composables + ViewModels**
  - **Description:** Create stub pairs for the four feature screens plus the loading screen. Each feature screen follows the same pattern:
    - `data class UiState(val isLoading: Boolean = true)` inside the contract file.
    - `@HiltViewModel class [X]ViewModel @Inject constructor(private val db: PowerSyncDatabase) : ViewModel()` — exposes `val uiState: StateFlow<UiState> = MutableStateFlow(UiState())`.
    - `@Composable fun [X]Screen(viewModel: [X]ViewModel = hiltViewModel())` — `val state by viewModel.uiState.collectAsStateWithLifecycle()`. Body: `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (state.isLoading) CircularProgressIndicator() }`.
    - Packages: `ui/inventory/`, `ui/shopping/`, `ui/unloading/`, `ui/detail/`.
    - `ui/loading/LoadingScreen.kt`: `@Composable fun LoadingScreen()` — `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }`. No ViewModel needed.
    - `DetailScreen` additionally accepts a `productKindId: String` nav argument from `navBackStackEntry.arguments`.
  - **Review Criteria:** All five screens (Inventory, Shopping, Unloading, Detail, Loading) appear in `AppNavHost` route registrations. Navigating programmatically to each produces a centred loading spinner without crash. Hilt provides the ViewModel without manual factory code.

- [ ] **Task 12: Material3 Theme**
  - **Description:** Create `src/app/src/main/ui/theme/Color.kt` (define primary/secondary/tertiary seed colours), `src/app/src/main/ui/theme/Type.kt` (default `Typography()`), `src/app/src/main/ui/theme/Theme.kt` (`@Composable fun GroceryTheme(content: @Composable () -> Unit)`). Since `minSdk = 35 ≥ 31`, `dynamicColorScheme(LocalContext.current)` is always available — use it unconditionally (no static fallback needed, but a static scheme may be kept as a build-flag override for testing). Apply `GroceryTheme` as the root wrapper in `MainActivity.setContent`.
  - **Review Criteria:** App launches with Material3 styling. `GroceryTheme` is the outermost composable in `setContent`. Dynamic color scheme is applied. A Compose `@Preview` with `GroceryTheme` renders correctly.

---

## Edge Case & Safety Checklist

- **minSdk 35 compatibility:** Accepted constraint — must be documented in `README.md`. App will not install on Android 15 or earlier devices.
- **Missing secrets at launch:** `require()` guards in `NetworkModule` produce a descriptive `IllegalStateException`, not a `NullPointerException`, surfacing the configuration error immediately.
- **No active session on cold start:** `AppNavHost` reads the session synchronously before setting the start destination; app routes to `LoginScreen` before PowerSync attempts to connect (avoids connector crash on missing credentials).
- **Empty `households` table after first sync:** `HouseholdStateRouter` (defined in Task 10) maps a `null`/empty watch result to `Screen.Loading`, ensuring the user sees a spinner rather than crashing or routing to an undefined screen.
- **Navigation authority conflict (Auth vs. Data State):** Auth is the outer gate — `MainActivity` routes to `Screen.Login` or hands off to the data-state layer. The data-state layer (`AppNavHost` watch query) only fires once a valid session exists. `MainActivity` always passes `Screen.Loading.route` as the start destination for authenticated users, so the data-state layer immediately assumes full routing control. The two layers never compete.
- **`popUpTo` magic number:** Use `navController.graph.startDestinationRoute!!` instead of `popUpTo(0)` to clear the back stack in a named, explicit way.
- **Concurrent JWT refresh race condition:** `Mutex` in `SupabasePowerSyncConnector.fetchCredentials()` prevents thundering-herd refresh calls from parallel PowerSync background operations.
- **Hilt KSP vs kapt:** Use `ksp` plugin exclusively — kapt is deprecated for Kotlin-only projects and incompatible with K2 compiler. No `kapt {}` block should appear anywhere.
- **`uploadData` null batch:** PowerSync calls `uploadData` periodically; a `null` batch (no pending writes) must return immediately without any Supabase API calls to avoid unnecessary network traffic.

---

## Review Log (Plan Review)

- **Round 1:** Three issues raised: (1) `HouseholdStateRouter` was referenced but not defined as a task; (2) navigation authority conflict between auth layer and data-state layer; (3) missing "empty households" handling. All three addressed: `HouseholdStateRouter` is now defined in Task 10 with explicit null/unknown mapping; navigation is now a two-layer model (auth gate → data-state layer) with `Screen.Loading` as the bridge; `popUpTo(0)` replaced with named start destination.
- **Round 2:** Approved. All Round 1 issues verified as resolved. Technical stack choices (KSP, collectAsStateWithLifecycle, WindowCompat edge-to-edge), task ordering, and safety guards confirmed correct.
- **Round 3:** [N/A]

## Final Status (Code Review)

- **Round 1:** [N/A]
- **Round 2:** [N/A]
- **Round 3:** [N/A]
