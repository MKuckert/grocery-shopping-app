  # Plan: Post-Review Consolidation of Opus Rebuild

## Objective

Apply all findings from the comparative code review between `feature/initial-impl` (Original) and `feature/opus-rebuild` (Rebuild). `feature/impl-merge` is the new base, branched of `feature/opus-rebuild`. Six architectural reversions, two restorations, and one new extraction must be performed to combine the best of both codebases. Testing infrastructure must be established.

- initial implementation is at `feature/initial-impl`
- rebuild implementation is at `feature/opus-rebuild`
- merge branch is at `feature/impl-merge`. This is the new working base

## Requirements & Decisions

- **Frameworks:** Jetpack Compose, Hilt, PowerSync + Supabase, CameraX + ML Kit, Ktor (all already present)
- **Chosen Libraries:** No new libraries required. All changes use existing dependencies. `co.touchlab:kermit` (already in deps) must be restored where `android.util.Log` or silent catches exist.
- **Error Handling Strategy:**
  - Every `catch (e: Exception)` block must log via `co.touchlab.kermit.Logger` before any user-facing action.
  - User-facing error messages use the typed `SnackbarMessage` domain model (restored from Original).
  - Scanner pipeline: try-catch on every repository/network call (keep Rebuild's coverage), but emit results via `SharedFlow` (revert to Original's communication pattern).

## Implementation Steps

> Status Markers: [ ] Open, [/] In Progress, [x] Completed

---

- [ ] **Task 1: Restore `SnackbarMessage` domain model**
  - **Description:** Recreate `src/app/src/main/kotlin/de/curlybracket/grocery/domain/model/SnackbarMessage.kt`. The Original's version at `~/private/dev/grocery-shopping-app/feature/initial-impl/src/app/src/main/kotlin/de/curlybracket/grocery/domain/model/SnackbarMessage.kt` is the reference:
    ```kotlin
    package de.curlybracket.grocery.domain.model

    import androidx.compose.runtime.Immutable

    @Immutable
    data class SnackbarMessage(
      val text: String,
      val productId: String,
      val actionLabel: String? = null
    )
    ```
    Then update every ViewModel that currently uses raw `String` or local data classes for snackbar messages:
    - `InventoryViewModel.kt` (lines 26, 70, 87-105) — Remove local `data class SnackbarMessage(productId, productName, newStock)`. Replace `MutableSharedFlow<SnackbarMessage>` (local) with `MutableSharedFlow<de.curlybracket.grocery.domain.model.SnackbarMessage>`. Update `decrementStock()` to emit `SnackbarMessage(text = "${product.name}: $newStock remaining", productId = product.id, actionLabel = "Details")`. Error case: `SnackbarMessage(text = "Failed to decrement stock", productId = product.id)`.
    - `ShoppingViewModel.kt` (lines 74-75) — Change `MutableSharedFlow<String>` to `MutableSharedFlow<SnackbarMessage>`. Update `finishShopping()` (line 82), `fulfillFull()`, `incrementPending()`, `decrementPending()`, `forceAddToCart()` to emit typed messages.
    - `UnloadingViewModel.kt` (lines 52-53) — Change `MutableSharedFlow<String>` to `MutableSharedFlow<SnackbarMessage>`. Update `toggleUnloadOpen()` (line 61), `confirmSubmit()` (line 83), `incrementPending()` (line 96), `decrementPending()` (line 106).
    - `DetailViewModel.kt` (lines 72-73) — Change `MutableSharedFlow<String>` to `MutableSharedFlow<SnackbarMessage>`. Update `saveChanges()` (line 99), `addBarcode()` (line 109), `deleteBarcode()` (line 129).
    - Update corresponding Screen composables to consume `SnackbarMessage.text` and use `SnackbarMessage.actionLabel` where present.
  - **Review Criteria:** No raw `String` snackbar flows remain in any ViewModel. All snackbar emissions use the domain model. `actionLabel = "Details"` is present on stock-change messages. Compiles without error.

---

- [ ] **Task 2: Revert navigation to callback pattern**
  - **Description:** Remove `NavController` from all screen function signatures. Replace with callback lambdas. Screens must not know about the navigation graph.

    **GroceryApp.kt** (lines 96-108): Change screen invocations from `InventoryScreen(navController = navController)` to callback-based:
    ```kotlin
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
        UnloadingScreen()
    }
    composable(Route.Detail.TEMPLATE) { backStack ->
        val productId = backStack.arguments?.getString("productId")
            ?: error("productId argument required")
        DetailScreen(
            productId = productId,
            onBack = { navController.popBackStack() }
        )
    }
    ```

    **InventoryScreen.kt**: Change signature from `fun InventoryScreen(navController: NavController)` to `fun InventoryScreen(onNavigateToDetail: (String) -> Unit)`. Remove `import androidx.navigation.NavController` and `import de.curlybracket.grocery.ui.navigation.Route`. Note: navigation is driven by `viewModel.navigationEvent` collected in a `LaunchedEffect` (not direct click handlers). Replace the `navController.navigate(Route.Detail(...).path)` call inside the `LaunchedEffect` collector with `onNavigateToDetail(productId)`.

    **ShoppingScreen.kt**: Same pattern. Signature becomes `fun ShoppingScreen(onNavigateToDetail: (String) -> Unit)`.

    **UnloadingScreen.kt**: Change from `fun UnloadingScreen(navController: NavController)` to `fun UnloadingScreen()`. This screen has no detail navigation. Remove NavController import.

    **DetailScreen.kt**: Change from `fun DetailScreen(productId: String, navController: NavController)` to `fun DetailScreen(productId: String, onBack: () -> Unit)`. Replace `navController.popBackStack()` with `onBack()`. Remove NavController import.

    **Also fix the unsafe `!!` in GroceryApp.kt line 106:** `backStack.arguments!!.getString("productId")!!` must become `backStack.arguments?.getString("productId") ?: error("productId argument required")` to produce a readable crash instead of a bare NPE.

  - **Review Criteria:** Zero files import `NavController` except `GroceryApp.kt`. Every screen is a pure composable that receives only data and callbacks. No `Route` imports in screen files. Compiles without error.

---

- [ ] **Task 3: Revert ScannerProcessor to Flow-based API with error handling**
  - **Description:** Merge the Original's Flow-based communication with the Rebuild's try-catch coverage. The result is a Hilt-injectable class that emits to `SharedFlow` properties and wraps every operation in error handling.

    Replace `scanner/ScannerProcessor.kt` entirely:
    ```kotlin
    package de.curlybracket.grocery.scanner

    import android.content.Context
    import co.touchlab.kermit.Logger
    import de.curlybracket.grocery.audio.AudioFeedback
    import de.curlybracket.grocery.domain.model.ProductKind
    import de.curlybracket.grocery.domain.repository.GroceryRepository
    import de.curlybracket.grocery.network.OFResult
    import de.curlybracket.grocery.network.OpenFoodFactsClient
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import java.io.File
    import javax.inject.Inject

    data class OpenFoodFactsLookupResult(
        val barcode: String,
        val prefillName: String,
    )

    class ScannerProcessor @Inject constructor(
        private val repository: GroceryRepository,
        private val audioFeedback: AudioFeedback,
        private val openFoodFactsClient: OpenFoodFactsClient,
    ) {

        private val _scanResultFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
        val scanResultFlow = _scanResultFlow.asSharedFlow()

        private val _openFoodFactsResultFlow =
            MutableSharedFlow<OpenFoodFactsLookupResult>(extraBufferCapacity = 1)
        val openFoodFactsResultFlow = _openFoodFactsResultFlow.asSharedFlow()

        suspend fun processScan(barcode: String, mode: ScannerMode) {
            val householdId = when (mode) {
                is ScannerMode.Inventory -> mode.householdId
                is ScannerMode.Shopping -> mode.householdId
            }

            val product = try {
                repository.findByBarcode(barcode, householdId)
            } catch (e: Exception) {
                Logger.e("findByBarcode failed", e)
                null
            }

            when {
                product != null && product.deletedAt != null -> {
                    try {
                        repository.restoreProductKind(product.id)
                        repository.recalculateQuantityToBuy(product.id)
                        audioFeedback.playSuccess()
                        _scanResultFlow.emit(
                            ScanResult.Restored(product.copy(deletedAt = null))
                        )
                    } catch (e: Exception) {
                        Logger.e("restoreProductKind failed", e)
                        audioFeedback.playFailure()
                    }
                }

                product != null -> {
                    try {
                        when (mode) {
                            is ScannerMode.Inventory ->
                                repository.decrementStock(product.id)
                            is ScannerMode.Shopping ->
                                repository.incrementPendingStock(product.id)
                        }
                        audioFeedback.playSuccess()
                        _scanResultFlow.emit(ScanResult.Hit(product))
                    } catch (e: Exception) {
                        Logger.e("mode-specific mutation failed", e)
                        audioFeedback.playFailure()
                    }
                }

                else -> {
                    audioFeedback.playFailure()
                    val prefillName = try {
                        when (val result =
                            openFoodFactsClient.lookupBarcode(barcode)) {
                            is OFResult.Hit -> result.productName
                            else -> "Unknown Item"
                        }
                    } catch (e: Exception) {
                        Logger.e("Open Food Facts lookup failed", e)
                        "Unknown Item"
                    }
                    _openFoodFactsResultFlow.emit(
                        OpenFoodFactsLookupResult(barcode, prefillName)
                    )
                    _scanResultFlow.emit(ScanResult.Miss(barcode))
                }
            }
        }

        suspend fun createNewProduct(
            context: Context,
            barcode: String,
            productName: String,
            householdId: String,
            photoPath: String?,
        ): ProductKind? {
            return try {
                val groupId = repository.ensureUnsortedGroup(householdId)

                repository.createProductKind(
                    householdId = householdId,
                    name = productName,
                    groupId = groupId,
                    minimumStock = 1,
                    barcodeNumber = barcode,
                )

                val permanentPath: String? = photoPath?.let { cachePath ->
                    try {
                        val cacheFile = File(cachePath)
                        val destDir = context.filesDir
                            .resolve("product_images").also { it.mkdirs() }
                        val destFile = File(destDir, cacheFile.name)
                        cacheFile.copyTo(destFile, overwrite = true)
                        destFile.absolutePath
                    } catch (e: Exception) {
                        Logger.e("Photo move failed", e)
                        null
                    }
                }

                val newProduct = repository.findByBarcode(barcode, householdId)

                if (permanentPath != null && newProduct != null) {
                    try {
                        repository.updateProductKind(
                            productId = newProduct.id,
                            name = productName,
                            groupId = groupId,
                            minimumStock = 1,
                            currentStock = 0,
                            imagePath = permanentPath,
                        )
                    } catch (e: Exception) {
                        Logger.e("updateProductKind (imagePath) failed", e)
                    }
                }

                newProduct
            } catch (e: Exception) {
                Logger.e("createNewProduct failed", e)
                null
            }
        }
    }
    ```

    **BarcodeScannerBottomSheet.kt** must be updated to consume `processor.scanResultFlow` and `processor.openFoodFactsResultFlow` via `LaunchedEffect` collectors (matching Original's pattern) instead of callback lambdas. The processor must be injected (not created via `remember {}`).

  - **Review Criteria:** `ScannerProcessor` is `@Inject`-constructed and Hilt-managed. Communication is via `scanResultFlow` and `openFoodFactsResultFlow` SharedFlows. Every repository/network call is wrapped in try-catch with `Logger`. No callback lambdas on `processScan()`. Photo handling from Rebuild is preserved. Compiles without error.

---

- [ ] **Task 4: Delete `ScannerViewModel` — inject `ScannerProcessor` directly**
  - **Description:** The `ScannerViewModel` (15 lines) exists solely because the Rebuild moved to passing raw dependencies. With `ScannerProcessor` restored as a Hilt-injectable class, the ViewModel is redundant.

    Delete `scanner/ScannerViewModel.kt`.

    Update all files that reference `ScannerViewModel`:
    - `InventoryScreen.kt`: Replace `val scannerViewModel: ScannerViewModel = hiltViewModel()` with `ScannerProcessor` received as a parameter.
    - `ShoppingScreen.kt`: Same change.
    - Remove `scannerViewModel.repository`, `scannerViewModel.audioFeedback`, `scannerViewModel.openFoodFactsClient` references — the `ScannerProcessor` encapsulates all three.

    The cleanest approach: `ScannerProcessor` is `@Singleton` scoped (it holds no mutable state, just references to other singletons). Screens receive it as a parameter from `GroceryApp.kt`, or it is provided via the `AppViewModel` (matching the Original's pattern where `AppViewModel` held the `ScannerProcessor`).

    If the `AppViewModel` approach is used, add to `AppViewModel`:
    ```kotlin
    @HiltViewModel
    class AppViewModel @Inject constructor(
        repository: GroceryRepository,
        val scannerProcessor: ScannerProcessor,
    ) : ViewModel() { ... }
    ```
    Then screens access it via `appViewModel.scannerProcessor`.

  - **Review Criteria:** `ScannerViewModel.kt` is deleted. No file imports it. `ScannerProcessor` is obtained via Hilt DI (not constructed locally). Compiles without error.

---

- [ ] **Task 5: Restore `Logger` in all catch blocks lacking developer logging**
  - **Description:** The Rebuild's ViewModel and Screen catch blocks emit user-facing messages (snackbar/errorMessage) but lack `Logger` calls for developer observability. Only `ScannerProcessor`, `SyncService`, and `AuthViewModel` log properly. `OpenFoodFactsClient.kt` is the only truly silent catch (returns `OFResult.Miss` with no logging). Every catch block must add a `Logger.e()` or `Logger.w()` call before any user-facing action.

    Files and locations to fix (all in the Rebuild):

    | File | Catch locations (approx. lines) | Required log |
    |---|---|---|
    | `DetailViewModel.kt` | 99, 109, 129 | `Logger.e("Failed to save changes", e)`, `Logger.e("Failed to add barcode", e)`, `Logger.e("Failed to delete barcode", e)` |
    | `ShoppingViewModel.kt` | 81, 91, 101, 111, 127 | `Logger.e("Failed to finish shopping", e)`, `Logger.e("Failed to fulfill item", e)`, `Logger.e("Failed to increment pending", e)`, `Logger.e("Failed to decrement pending", e)`, `Logger.e("Failed to force-add item", e)` |
    | `InventoryViewModel.kt` | 79, 99 | `Logger.e("Failed to start shopping", e)`, `Logger.e("Failed to decrement stock", e)` |
    | `UnloadingViewModel.kt` | 61, 82, 96, 106 | `Logger.e("Failed to toggle unload state", e)`, `Logger.e("Failed to submit unloading", e)`, `Logger.e("Failed to increment pending", e)`, `Logger.e("Failed to decrement pending", e)` |
    | `OpenFoodFactsClient.kt` | 27 | `Logger.w("Open Food Facts lookup failed", e)` |
    | `SignInScreen.kt` | 89 (generic catch) | `Logger.e("Sign-in failed", e)` |
    | `SignUpScreen.kt` | 88 (generic catch) | `Logger.e("Sign-up failed", e)` |

    Add `import co.touchlab.kermit.Logger` to each file that lacks it.

    Also in `ScannerProcessor.kt` (from Task 3): replace any remaining `android.util.Log` calls with `Logger` for consistency. The entire codebase must use Kermit, not `android.util.Log`.

  - **Review Criteria:** `grep -rn "catch (e:" src/app/src/main/kotlin/` returns zero blocks without a `Logger.e()` or `Logger.w()` call immediately inside. No file imports `android.util.Log`. Compiles without error.

---

- [ ] **Task 6: Extract `householdIdFlow` to a shared utility**
  - **Description:** The household ID extraction from `SupabaseConnector.sessionStatus` is duplicated in 4 locations:
    - `AuthViewModel.kt` (line 36)
    - `InventoryViewModel.kt` (line 35)
    - `ShoppingViewModel.kt` (line 33)
    - `UnloadingViewModel.kt` (line 32)

    Each repeats:
    ```kotlin
    connector.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated ->
                    status.session.user?.appMetadata?.get("household_id")
                        ?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    ```

    Create a Kotlin extension function in a new file `auth/HouseholdIdExt.kt`:
    ```kotlin
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
    ```

    Then replace all 4 usages:
    ```kotlin
    // Before (in each ViewModel):
    val householdIdFlow: StateFlow<String?> = connector.sessionStatus
        .map { ... }.stateIn(...)

    // After:
    val householdIdFlow: StateFlow<String?> =
        connector.householdIdFlow(viewModelScope)
    ```

    In `AuthViewModel.kt`, rename from `householdId` to `householdIdFlow` for consistency with the other ViewModels.

  - **Review Criteria:** The extraction logic appears exactly once in `auth/HouseholdIdExt.kt`. All four ViewModels call `connector.householdIdFlow(viewModelScope)`. No ViewModel imports `jsonPrimitive`, `contentOrNull`, or `SessionStatus` for this purpose. Compiles without error.

---

- [ ] **Task 7: Fix `BarcodeScannerBottomSheet` to consume Flow-based ScannerProcessor**
  - **Description:** This is the UI counterpart to Task 3. The `BarcodeScannerBottomSheet.kt` currently creates a local `ScannerProcessor` via `remember {}` and uses callback lambdas. It must be refactored to:
    1. Accept `ScannerProcessor` as a parameter (Hilt-injected, not locally created).
    2. Subscribe to `processor.scanResultFlow` and `processor.openFoodFactsResultFlow` via `LaunchedEffect` collectors.
    3. Keep the Rebuild's `isProcessing` boolean for UI spinner feedback.
    4. Keep the Rebuild's photo capture logic and `CaptureRequired` state handling.

    New function signature:
    ```kotlin
    @Composable
    fun BarcodeScannerBottomSheet(
        mode: ScannerMode,
        isOpen: Boolean,
        onDismiss: () -> Unit,
        onResult: (ScanResult) -> Unit,
        processor: ScannerProcessor,
    )
    ```

    State observation pattern:
    ```kotlin
    LaunchedEffect(Unit) {
        processor.scanResultFlow.collect { result ->
            isProcessing = false
            onResult(result)
            when (result) {
                is ScanResult.Hit, is ScanResult.Restored -> {
                    scannerState = ScannerState.Scanning
                }
                is ScanResult.Miss -> {
                    // State transition handled by openFoodFactsResultFlow
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        processor.openFoodFactsResultFlow.collect { lookupResult ->
            scannerState = ScannerState.CaptureRequired(
                barcode = lookupResult.barcode,
                prefillName = lookupResult.prefillName,
                photoPath = null,
            )
        }
    }
    ```

    On barcode detected:
    ```kotlin
    onBarcodeDetected = { barcode ->
        if (!isProcessing) {
            isProcessing = true
            scope.launch { processor.processScan(barcode, mode) }
        }
    }
    ```

    On manual product creation (CaptureRequired save):
    ```kotlin
    scope.launch {
        isProcessing = true
        val newProduct = processor.createNewProduct(
            context = context,
            barcode = state.barcode,
            productName = state.prefillName,
            householdId = mode.householdId,
            photoPath = state.photoPath,
        )
        isProcessing = false
        if (newProduct != null) {
            onResult(ScanResult.Hit(newProduct))
        }
        scannerState = ScannerState.Scanning
    }
    ```

    Add a `householdId` property accessor to `ScannerMode` (or extract via `when` as in current code) so `createNewProduct` can access it.

  - **Review Criteria:** `BarcodeScannerBottomSheet` does not import or reference `GroceryRepository`, `AudioFeedback`, or `OpenFoodFactsClient` directly. It receives a `ScannerProcessor` parameter. Flow subscriptions use `LaunchedEffect`. `isProcessing` spinner is preserved. Photo capture and file handling are preserved. Compiles without error.

---

- [ ] **Task 8: Update screen composables for BarcodeScannerBottomSheet signature change**
  - **Description:** After Tasks 2, 3, 4, and 7, the `BarcodeScannerBottomSheet` call sites in `InventoryScreen.kt` and `ShoppingScreen.kt` must be updated.

    Both screens currently pass raw dependencies:
    ```kotlin
    // Current (Rebuild):
    BarcodeScannerBottomSheet(
        mode = ScannerMode.Inventory(hid),
        repository = scannerViewModel.repository,
        audioFeedback = scannerViewModel.audioFeedback,
        openFoodFactsClient = scannerViewModel.openFoodFactsClient,
        onResult = { ... },
        onDismiss = { ... },
    )
    ```

    Must become:
    ```kotlin
    // Target:
    BarcodeScannerBottomSheet(
        mode = ScannerMode.Inventory(hid),
        isOpen = showScanner,
        onDismiss = { showScanner = false },
        onResult = { result ->
            when (result) {
                is ScanResult.Hit -> { /* snackbar */ }
                is ScanResult.Restored -> { /* snackbar */ }
                is ScanResult.Miss -> { /* handled by sheet internally */ }
            }
        },
        processor = appViewModel.scannerProcessor,
    )
    ```

    Both screens need to receive `ScannerProcessor` as a parameter from `GroceryApp.kt`.

    The `InventoryScreen` signature becomes:
    ```kotlin
    fun InventoryScreen(
        onNavigateToDetail: (String) -> Unit,
        scannerProcessor: ScannerProcessor,
    )
    ```

    The `ShoppingScreen` signature becomes:
    ```kotlin
    fun ShoppingScreen(
        onNavigateToDetail: (String) -> Unit,
        scannerProcessor: ScannerProcessor,
    )
    ```

    And `GroceryApp.kt` passes both from `AppViewModel`:
    ```kotlin
    composable(Route.Inventory.path) {
        val appViewModel: AppViewModel = hiltViewModel()
        InventoryScreen(
            onNavigateToDetail = { productId ->
                navController.navigate(Route.Detail(productId).path)
            },
            scannerProcessor = appViewModel.scannerProcessor,
        )
    }
    ```

  - **Review Criteria:** `InventoryScreen` and `ShoppingScreen` do not import `ScannerViewModel`, `GroceryRepository`, `AudioFeedback`, or `OpenFoodFactsClient`. They receive `ScannerProcessor` as a parameter. `BarcodeScannerBottomSheet` is called with the new signature including `isOpen` and `processor`. Compiles without error.

---

- [ ] **Task 9: Write unit tests for critical business logic**
  - **Description:** Both codebases have 0% test coverage. Establish the testing foundation with unit tests for the highest-risk code paths.

    Add test dependencies to `libs.versions.toml` (under `[versions]` and `[libraries]`) and reference them via `libs.*` in `app/build.gradle.kts`:
    - `kotlinx-coroutines-test` (1.11.0)
    - `turbine` (1.2.0) — for Flow testing
    - `mockk` (1.13.16) — for mocking

    Create the following test files in `src/app/src/test/java/de/curlybracket/grocery/`:

    **a) `scanner/ScannerProcessorTest.kt`** — Highest priority. Test:
    - `processScan()` with known barcode emits `ScanResult.Hit` and calls mode-specific mutation
    - `processScan()` with unknown barcode emits `ScanResult.Miss` and triggers OFF lookup
    - `processScan()` with soft-deleted barcode emits `ScanResult.Restored` and calls `restoreProductKind`
    - `processScan()` when `findByBarcode` throws returns gracefully (no crash)
    - `createNewProduct()` creates product and returns it
    - `createNewProduct()` handles photo move failure gracefully
    - Audio feedback: `playSuccess()` on Hit/Restored, `playFailure()` on Miss

    **b) `scanner/BarcodeAnalyzerThrottleTest.kt`** — Test the throttle state machine:
    - Same barcode within 3000ms cooldown is suppressed
    - Same barcode after 3000ms fires again
    - No barcode for 400ms clears cooldown map
    - Different barcodes have independent cooldowns

    **c) `auth/HouseholdIdExtTest.kt`** — Test the extracted extension:
    - Authenticated session with valid `household_id` metadata returns the ID
    - Authenticated session without `household_id` returns null
    - `NotAuthenticated` session returns null
    - Non-string `household_id` value returns null

    **d) `data/repository/GroceryRepositoryImplTest.kt`** — Test mutation logic:
    - `decrementStock` does not go below 0
    - `submitUnloading` atomically applies pending stock and resets state
    - `recalculateQuantityToBuy` computes `max(0, minimumStock - currentStock)`
    - `createProductKind` in transaction creates both product and barcode

    Delete the placeholder `ExampleUnitTest.kt`.

  - **Review Criteria:** All test files compile. `./gradlew test` passes. Each test file has at least 4 test methods. Mock setup is clean (no redundant mocking). Flow tests use Turbine's `test {}` block or `first()`. Coroutine tests use `runTest {}`.

---

## Edge Case & Safety Checklist

- [ ] `householdIdFlow` returns `null` before authentication completes — all `flatMapLatest` chains must emit `emptyList()` for null household
- [ ] `findByBarcode` can throw on database timeout — catch block must prevent scan loop crash
- [ ] `backStack.arguments?.getString("productId")` can be null if deep link is malformed — use `?: error()` not `!!`
- [ ] `SnackbarMessage.actionLabel` being null must not cause crash in screen collectors — use `?.let` for action handling
- [ ] Scanner `isProcessing` flag must be reset on both success AND failure paths to prevent UI deadlock
- [ ] Photo cache files in `cacheDir/scanner_photos/` must be cleaned up on sheet dismiss regardless of save outcome
- [ ] `ScannerProcessor.createNewProduct` photo move failure must not prevent product creation (photo is optional)
- [ ] `DetailViewModel._userEditing` flag must be reset after successful save to allow stream sync to resume
- [ ] `Logger` import must be `co.touchlab.kermit.Logger`, never `android.util.Log` — enforce consistency
- [ ] Thread safety: `BarcodeAnalyzer.state` uses `AtomicReference.getAndUpdate` — do not introduce mutable vars alongside it
- [ ] `BarcodeScannerBottomSheet` must guard against double-processing (check `isProcessing` before launching coroutine)

## Review Log (Plan Review)

- **Round 1:** Approved with notes (2026-07-09)

  **Status: Approved**

  The plan is structurally sound, technically feasible, and all referenced files, types, methods, and fields exist in the codebase. All nine tasks are well-scoped with clear review criteria. No blockers found. The following notes are for the Builder's awareness:

  1. **Task 5 — "14 silent catches" is inaccurate.** The table lists 17 catch blocks (3+5+2+4+1+1+1). None of the ViewModel or Screen catches are truly "silent" — they all emit user-facing error messages via `_snackbarMessage.emit()` or set `errorMessage`. They lack `Logger` calls for developer observability. Only `OpenFoodFactsClient.kt:27` is genuinely silent (returns `OFResult.Miss` with no logging or user feedback). The intent to add `Logger` to all is correct; the Builder should not be confused by the "silent" label.

  2. **Task 9 — Test file path is wrong.** The plan says `src/test/java/de/curlybracket/grocery/`. The actual test root is `src/app/src/test/java/de/curlybracket/grocery/` (where `ExampleUnitTest.kt` already lives). The Builder must use the correct path.

  3. **Task 9 — Test dependencies should use version catalog.** The project manages all dependencies via `src/gradle/libs.versions.toml`. The plan specifies raw coordinates (`"app.cash.turbine:turbine:1.2.0"` etc.). The Builder should add `turbine`, `mockk`, and `coroutines-test` entries to `libs.versions.toml` first, then reference via `libs.*` in `build.gradle.kts` for consistency.

  4. **Task 2 — InventoryScreen navigation goes through a ViewModel event flow.** Navigation in `InventoryScreen` is driven by `viewModel.navigationEvent` collected in a `LaunchedEffect` (line 67), not by direct composable calls. The "Replace all `navController.navigate(...)` calls" instruction covers this, but the Builder should be aware the replacement site is inside the `LaunchedEffect` collector, not inline composable click handlers.

- **Round 2:** [N/A]
- **Round 3:** [N/A]

## Final Status (Code Review)

- **Round 1:** [Pending]
- **Round 2:** [N/A]
- **Round 3:** [N/A]
