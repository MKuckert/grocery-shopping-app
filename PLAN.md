# Plan: Grocery Shopping App

## Objective

Transform the current skeleton PowerSync/Supabase Android app (a todo-list demo) into a full-featured household grocery management app. The finished app must implement the three-state machine (IDLE / SHOPPING / UNLOADING), four screens (InventoryScreen, ShoppingScreen, UnloadingScreen, DetailScreen), barcode scanning with Open Food Facts fallback, animated reactive lists, and full offline-first sync — exactly as specified in `docs/UI.md` and `docs/DATABASE.md`.

---

## Requirements & Decisions

- **Frameworks:** Jetpack Compose (Material 3), Hilt DI, PowerSync 1.13.0 + Supabase 3.6.0, CameraX + ML Kit Barcode Scanning, `navigation-compose`. All already present in `libs.versions.toml`.
- **Chosen Libraries:**
  - `androidx.navigation:navigation-compose` — already declared; use for typed-route NavHost replacing the manual `NavController` / `Screen` sealed class.
  - `powersync-compose` — already declared; use `db.watchFlow(sql)` for reactive streams.
  - Retrofit + OkHttp **not** needed — use `io.ktor:ktor-client-android` (already transitive via Supabase-kt) for the Open Food Facts HTTP call.
  - No new dependencies required beyond what is already declared.
- **Error Handling Strategy:**
  - **DB writes** fail loudly: surface error via `Snackbar`; never silently swallow.
  - **PowerSync sync errors** are surfaced via the existing `SyncStatusData` banner (already in `SyncService`).
  - **Open Food Facts API timeout / offline:** fall back to title = `"Unknown Item"` and block completion until photo captured (per spec §4).
  - **Open Food Facts 503 rate limit:** single exponential-backoff retry (2 s, 4 s), then treat as offline miss.
  - **Barcode not found locally:** play failure boop, switch scanner viewport to `CaptureRequired` state; app remains live (no crash).
  - **Stock decrement below 0:** guarded at the call site (`WHERE current_stock > 0`); DB constraint is a safety net, not the primary check.
  - **Unload submit with open rows:** `AlertDialog` warning; user must explicitly confirm.
  - **Soft-deleted item scanned:** nullify `deleted_at`, resurface with Snackbar "Restored …".

---

## Implementation Steps

> Status Markers: [ ] Open, [/] In Progress, [x] Completed

---

### Task 0: Application Bootstrap & DI Initialization Order

- **Description:**
  Establish the correct initialization order so all Hilt-injected singletons are available before any UI composable or ViewModel accesses them.

  **`GroceryApplication.kt`** — already annotated `@HiltAndroidApp`. No changes needed to the class itself; Hilt generates the component at compile time. The singletons (`SupabaseConnector`, `PowerSyncDatabase`, `GroceryRepository`) are created lazily by Hilt on first injection request.

  **`di/SyncModule.kt`** initialization contract (builder must follow this order of concern):
  1. `SupabaseConnector` is a pure constructor call (no async, no IO). Safe as `@Singleton`.
  2. `PowerSyncDatabase` creates the SQLite file on disk synchronously (no network). Safe as `@Singleton`. Uses `DatabaseDriverFactory(context)`.
  3. `GroceryRepository` depends on `PowerSyncDatabase` and the `householdId` string. `householdId` is read from the live Supabase session (lazy, not at creation time).

  **`household_id` provision strategy** — The `household_id` must NOT be a `@Singleton @Provides` that reads from the session at app start (the user may not be logged in yet). Instead:
  - Do NOT provide `householdId` as a Hilt binding.
  - Instead, expose it from the `AuthViewModel` as a `StateFlow<String?>` derived from `connector.sessionStatus`.
  - ViewModels that need `householdId` inject `AuthViewModel` or receive it as a constructor parameter via assisted injection.
  - Repository functions that require `householdId` accept it as a parameter (already reflected in Task 4 signatures).

  **PowerSync connection lifecycle:**
  - `SyncService` (already exists) handles `database.connect(connector)` when session is `Authenticated` and `database.disconnectAndClear()` on sign-out. No changes needed here.
  - `GroceryApp` composable must NOT call `database.connect()` directly — that is the service's responsibility.

  **`GroceryApplication.kt` minimal change:** Override `onTerminate()` to call `audioFeedback.release()` (requires injecting `AudioFeedback` into the Application class via `@Inject`).

- **Review Criteria:**
  - No `database.connect()` call outside `SyncService`.
  - `householdId` is never a Hilt `@Singleton` binding that reads session eagerly.
  - `GroceryApplication` releases `AudioFeedback.soundPool` in `onTerminate()`.
  - App builds and `SyncService` starts correctly on sign-in.

---

### Task 1: Delete Demo Code & Establish Package Skeleton

- **Description:**
  Remove all files specific to the demo todo-list feature that will not be reused:
  - `powersync/TodoSchema.kt`, `powersync/ListContent.kt`, `powersync/Todo.kt`
  - `ui/screens/HomeScreen.kt`, `ui/screens/TodosScreen.kt`
  - `ui/components/TodoList.kt`, `ui/components/EditDialog.kt`, `ui/components/ListContent.kt`, `ui/components/ListItemRow.kt`, `ui/components/TodoItemRow.kt`, `ui/components/Input.kt`
  - `NavController.kt` (replaced by Compose Navigation)
  - `GroceryApp.kt` (rewritten)

  Create the following empty package directories (stub `package` files are not needed; just ensure the directory exists for subsequent tasks):

  ```
  de.curlybracket.grocery.domain.model
  de.curlybracket.grocery.domain.repository
  de.curlybracket.grocery.data.db
  de.curlybracket.grocery.data.repository
  de.curlybracket.grocery.ui.navigation
  de.curlybracket.grocery.ui.screens.inventory
  de.curlybracket.grocery.ui.screens.shopping
  de.curlybracket.grocery.ui.screens.unloading
  de.curlybracket.grocery.ui.screens.detail
  de.curlybracket.grocery.ui.components
  de.curlybracket.grocery.scanner
  de.curlybracket.grocery.network
  de.curlybracket.grocery.audio
  ```

- **Review Criteria:**
  - Deleted files compile-removed; project still builds (stubs OK).
  - No references to `ListItem`, `TodoItem`, `LISTS_TABLE`, `TODOS_TABLE` remain.

---

### Task 2: Update PowerSync Schema (`AppSchema.kt`)

- **Description:**
  Rewrite `data/db/AppSchema.kt`. Remove `listsTable` / `todosTable` imports and entries. Add the full grocery schema. Note: PowerSync `Column` type has no `boolean` — use `Column.integer` for boolean columns; 0 = false, 1 = true.

  ```kotlin
  val AppSchema = Schema(
    tables = listOf(
      Table(
        name = "households",
        columns = listOf(
          Column.text("current_state"),          // 'IDLE' | 'SHOPPING' | 'UNLOADING'
          Column.text("shopping_started_at")     // nullable ISO timestamp
        )
      ),
      Table(
        name = "product_groups",
        columns = listOf(
          Column.text("household_id"),
          Column.text("name"),
          Column.text("deleted_at")              // nullable ISO timestamp
        )
      ),
      Table(
        name = "product_kinds",
        columns = listOf(
          Column.text("household_id"),
          Column.text("group_id"),               // FK → product_groups.id (nullable)
          Column.text("name"),
          Column.integer("current_stock"),
          Column.integer("minimum_stock"),
          Column.integer("quantity_to_buy"),
          Column.integer("pending_stock"),
          Column.text("image_path"),             // nullable local URI
          Column.integer("unload_open"),         // 0=false 1=true
          Column.text("deleted_at")              // nullable ISO timestamp
        )
      ),
      Table(
        name = "barcodes",
        columns = listOf(
          Column.text("household_id"),
          Column.text("product_kind_id"),
          Column.text("barcode_number")
        )
      )
    )
  )
  ```

  Also add local SQLite index hints via PowerSync `Table.indexes` parameter as defined in `DATABASE.md §1.2`:
  - `idx_barcodes_lookup`: unique index on `barcodes(household_id, barcode_number)`.
  - `idx_product_kinds_active_group`: index on `product_kinds(household_id, group_id)` with partial filter `deleted_at IS NULL`.
  - `idx_product_kinds_shopping_state`: index on `product_kinds(household_id, quantity_to_buy, pending_stock)` with partial filter `deleted_at IS NULL`.

  PowerSync SDK supports indexes via `Index` and `IndexedColumn` classes. Exact API (PowerSync Kotlin SDK 1.13.x):

  ```kotlin
  import com.powersync.db.schema.Index
  import com.powersync.db.schema.IndexedColumn

  Table(
    name = "barcodes",
    columns = listOf(...),
    indexes = listOf(
      Index(
        name = "idx_barcodes_lookup",
        columns = listOf(
          IndexedColumn.ascending("household_id"),
          IndexedColumn.ascending("barcode_number")
        )
      )
    )
  )
  ```

  Apply the same pattern for `product_kinds` indexes. Note: PowerSync SDK does not support partial index `WHERE` clauses in the `Index` class — omit partial filters; they are defined in Postgres and enforced server-side. The client-side indexes are for query performance only.

- **Review Criteria:**
  - `AppSchema` compiles, no references to `listsTable`/`todosTable`.
  - All four tables present with correct column types.
  - Three indexes declared.

---

### Task 3: Domain Models

- **Description:**
  Create data classes in `domain/model/`:

  **`HouseholdState.kt`**

  ```kotlin
  enum class HouseholdState { IDLE, SHOPPING, UNLOADING }

  @Immutable
  data class Household(
    val id: String,
    val currentState: HouseholdState,
    val shoppingStartedAt: String?
  )
  ```

  **`ProductGroup.kt`**

  ```kotlin
  @Immutable
  data class ProductGroup(
    val id: String,
    val householdId: String,
    val name: String,
    val deletedAt: String?
  )
  ```

  **`ProductKind.kt`**

  ```kotlin
  @Immutable
  data class ProductKind(
    val id: String,
    val householdId: String,
    val groupId: String?,
    val name: String,
    val currentStock: Int,
    val minimumStock: Int,
    val quantityToBuy: Int,
    val pendingStock: Int,
    val imagePath: String?,
    val unloadOpen: Boolean,   // maps from INTEGER 0/1
    val deletedAt: String?
  )
  ```

  **`Barcode.kt`**

  ```kotlin
  @Immutable
  data class Barcode(
    val id: String,
    val householdId: String,
    val productKindId: String,
    val barcodeNumber: String
  )
  ```

  **`GroupWithProducts.kt`** — aggregate for InventoryScreen list sections:

  ```kotlin
  @Immutable
  data class GroupWithProducts(
    val groupName: String,          // from JOIN or "Unsorted" fallback
    val products: List<ProductKind>
  )

  @Immutable
  data class ProductWithGroup(
    val product: ProductKind,
    val groupName: String?          // null when group_id IS NULL or group is deleted
  )
  ```

  Mapping from `SqlCursor` row: every data class must have a companion `fromCursor(row: Map<String, Any?>)` factory or an extension `ResultSet.toXxx()` helper.

- **Review Criteria:**
  - All models are `@Immutable`.
  - `unloadOpen` is `Boolean`, not `Int`, in the domain model.
  - `fromCursor` / mapping helpers compile and correctly parse null TEXT columns as `null`.

---

### Task 4: Repository Layer

- **Description:**
  Create `GroceryRepository` interface in `domain/repository/GroceryRepository.kt` and implementation `data/repository/GroceryRepositoryImpl.kt`. Inject `PowerSyncDatabase` and `SupabaseConnector` via Hilt. The repository owns all SQL and exposes only typed `Flow`s and `suspend` mutation functions.

  **Key reactive flows (use `db.watchFlow(sql, parameters)` for live queries):**

  The PowerSync `watch` API signature is:

  ```kotlin
  // Returns Flow<List<T>> that re-emits when any table in the query changes
  db.watch(
    sql = "SELECT ...",
    parameters = listOf("param1", "param2")
  ) { cursor: SqlCursor ->
    /* map SqlCursor to domain object; return T */
  }
  ```

  `SqlCursor` column access: `cursor.getString(index)`, `cursor.getLong(index)`, `cursor.getDouble(index)`. Column order follows `SELECT` clause order.

  **SQLite `MAX(0, expr)` is valid.** SQLite's multi-argument `max(X, Y)` is a documented scalar function (not the aggregate variant) and is fully valid in `UPDATE SET` clauses. Example: `UPDATE product_kinds SET current_stock = max(0, current_stock - 1) WHERE id = ?` is correct SQLite syntax.

  All `watchXxx` flows use `db.watch(sql, params) { cursor -> ... }`. All single-shot reads use `db.getAll(sql, params) { cursor -> ... }` or `db.get(sql, params) { cursor -> ... }`.

  ```kotlin
  // Household state — the root routing signal
  fun watchHousehold(): Flow<Household?>

  // All active groups (deleted_at IS NULL)
  fun watchProductGroups(): Flow<List<ProductGroup>>

  // Inventory: active products joined with their group name
  // Uses a SINGLE denormalized JOIN query; in-memory grouping happens in the ViewModel via Flow.map
  fun watchProductsWithGroups(householdId: String): Flow<List<ProductWithGroup>>

  // Shopping list sections
  fun watchActiveShopping(householdId: String): Flow<List<ProductKind>>
  fun watchStruckThrough(householdId: String): Flow<List<ProductKind>>
  fun watchImpulseBuys(householdId: String): Flow<List<ProductKind>>

  // Unloading: products where pending_stock > 0
  fun watchUnloadingItems(householdId: String): Flow<List<ProductKind>>

  // Detail screen
  fun watchProductKind(productId: String): Flow<ProductKind?>

  // Barcode list for detail screen
  fun watchBarcodes(productKindId: String): Flow<List<Barcode>>

  // Global search (used by SearchBar in ShoppingScreen)
  fun watchSearch(query: String, householdId: String): Flow<List<ProductKind>>

  // Barcode lookup — O(1) single-shot query
  suspend fun findByBarcode(barcodeNumber: String, householdId: String): ProductKind?
  ```

  **`watchProductsWithGroups` — denormalized JOIN (single reactive query):**

  ```sql
  SELECT
    pk.id, pk.household_id, pk.group_id, pk.name,
    pk.current_stock, pk.minimum_stock, pk.quantity_to_buy,
    pk.pending_stock, pk.image_path, pk.unload_open, pk.deleted_at,
    pg.name AS group_name
  FROM product_kinds pk
  LEFT JOIN product_groups pg ON pk.group_id = pg.id AND pg.deleted_at IS NULL
  WHERE pk.household_id = ? AND pk.deleted_at IS NULL
  ORDER BY pg.name ASC NULLS LAST, pk.name ASC
  ```

  Emit as `Flow<List<ProductWithGroup>>` where:

  ```kotlin
  @Immutable
  data class ProductWithGroup(
    val product: ProductKind,
    val groupName: String?  // null = no group / orphaned group
  )
  ```

  In `InventoryViewModel`, apply `Flow.map` to group into `List<GroupWithProducts>`:

  ```kotlin
  val groupsWithProducts: StateFlow<List<GroupWithProducts>> =
    repository.watchProductsWithGroups(householdId)
      .map { rows ->
        rows.groupBy { it.groupName ?: "Unsorted" }
          .map { (groupName, items) ->
            GroupWithProducts(groupName = groupName, products = items.map { it.product })
          }
          .sortedBy { it.groupName }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  ```

  This is a **pure reactive transformation** (no mutable cache, no side-effecting merge). The single SQL query is reactive — any change to `product_kinds` or `product_groups` re-emits the full stream automatically.

  **SQL fragments (complete):**
  - `watchHousehold`: `SELECT id, current_state, shopping_started_at FROM households LIMIT 1`
  - `watchGroupsWithProducts`: Two queries — first `SELECT id, household_id, name, deleted_at FROM product_groups WHERE household_id = ? AND deleted_at IS NULL ORDER BY name ASC`, then for each group `SELECT * FROM product_kinds WHERE household_id = ? AND group_id = ? AND deleted_at IS NULL ORDER BY name ASC`. Merge in-memory into `GroupWithProducts`. Also include a "no group" catch-all: `SELECT * FROM product_kinds WHERE household_id = ? AND (group_id IS NULL OR group_id NOT IN (SELECT id FROM product_groups WHERE deleted_at IS NULL)) AND deleted_at IS NULL ORDER BY name ASC`.
  - Shopping active: `SELECT id, household_id, group_id, name, current_stock, minimum_stock, quantity_to_buy, pending_stock, image_path, unload_open, deleted_at FROM product_kinds WHERE household_id = ? AND deleted_at IS NULL AND quantity_to_buy > 0 AND pending_stock < quantity_to_buy ORDER BY name ASC`
  - Shopping struck: `SELECT ... FROM product_kinds WHERE household_id = ? AND deleted_at IS NULL AND quantity_to_buy > 0 AND pending_stock >= quantity_to_buy ORDER BY name ASC`
  - Impulse buys: `SELECT ... FROM product_kinds WHERE household_id = ? AND deleted_at IS NULL AND quantity_to_buy = 0 AND pending_stock > 0 ORDER BY name ASC`
  - Unloading: `SELECT ... FROM product_kinds WHERE household_id = ? AND deleted_at IS NULL AND pending_stock > 0 ORDER BY name ASC`
  - Search: `SELECT ... FROM product_kinds WHERE household_id = ? AND deleted_at IS NULL AND name LIKE ? ORDER BY name ASC` where `?` = `"%$query%"`
  - `findByBarcode`: `SELECT pk.* FROM product_kinds pk INNER JOIN barcodes b ON b.product_kind_id = pk.id WHERE b.household_id = ? AND b.barcode_number = ? LIMIT 1` (single-shot `db.execute`, not `watchFlow`)

  **Mutations (use `db.execute(sql, params)` for single statements; `db.writeTransaction { ... }` for atomic multi-statement blocks):**

  ```kotlin
  // Household state transitions
  // SQL: UPDATE households SET current_state = ? WHERE id = (SELECT id FROM households LIMIT 1)
  suspend fun setHouseholdState(state: HouseholdState)

  // Inventory consumption — guard at call site, DB constraint is safety net
  // Atomic: writeTransaction { execute("UPDATE product_kinds SET current_stock = MAX(0, current_stock - 1) WHERE id = ?", listOf(id)); execute("UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?", listOf(id)) }
  suspend fun decrementStock(productId: String)

  // Shopping adjustments
  // SQL: UPDATE product_kinds SET pending_stock = MAX(0, ?) WHERE id = ?
  suspend fun setPendingStock(productId: String, value: Int)
  // SQL: UPDATE product_kinds SET pending_stock = pending_stock + 1 WHERE id = ?
  suspend fun incrementPendingStock(productId: String)
  // SQL: UPDATE product_kinds SET pending_stock = MAX(0, pending_stock - 1) WHERE id = ?
  suspend fun decrementPendingStock(productId: String)
  // SQL: UPDATE product_kinds SET pending_stock = quantity_to_buy WHERE id = ?
  suspend fun fulfillFull(productId: String)

  // quantity_to_buy recalculation — always called within the same writeTransaction as the stock change
  // SQL: UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?
  suspend fun recalculateQuantityToBuy(productId: String)

  // Unloading
  // SQL: UPDATE product_kinds SET unload_open = ? WHERE id = ?  (1 = open, 0 = closed)
  suspend fun setUnloadOpen(productId: String, open: Boolean)

  // Atomic transaction — single writeTransaction block:
  // Step 1: UPDATE product_kinds SET current_stock = current_stock + pending_stock, pending_stock = 0, unload_open = 0 WHERE household_id = ? AND pending_stock > 0
  // Step 2: UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE household_id = ? AND deleted_at IS NULL
  // Step 3: UPDATE households SET current_state = 'IDLE' WHERE id = (SELECT id FROM households LIMIT 1)
  suspend fun submitUnloading(householdId: String)

  // Detail screen edits — atomic writeTransaction:
  // UPDATE product_kinds SET name = ?, group_id = ?, minimum_stock = ?, current_stock = ?, image_path = ? WHERE id = ?
  // then recalculateQuantityToBuy(productId)
  suspend fun updateProductKind(productId: String, name: String, groupId: String?,
                                minimumStock: Int, currentStock: Int, imagePath: String?)

  // SQL: INSERT INTO barcodes (id, household_id, product_kind_id, barcode_number) VALUES (?, ?, ?, ?)
  suspend fun addBarcode(productKindId: String, barcodeNumber: String, householdId: String)
  // SQL: DELETE FROM barcodes WHERE id = ?
  suspend fun deleteBarcode(barcodeId: String)

  // New product creation — atomic writeTransaction:
  // INSERT INTO product_kinds (id, household_id, group_id, name, current_stock, minimum_stock, quantity_to_buy, pending_stock, image_path, unload_open, deleted_at) VALUES (newUuid, ?, ?, ?, 0, 1, 1, 0, null, 0, null)
  // INSERT INTO barcodes (id, household_id, product_kind_id, barcode_number) VALUES (newUuid, ?, newProductId, ?)
  // returns new product id
  suspend fun createProductKind(
    householdId: String, name: String, groupId: String,
    minimumStock: Int = 1, barcodeNumber: String
  ): String

  // SQL: UPDATE product_kinds SET deleted_at = NULL WHERE id = ?
  // then recalculateQuantityToBuy(productId)
  suspend fun restoreProductKind(productId: String)

  // SELECT id FROM product_groups WHERE household_id = ? AND name = 'Unsorted' AND deleted_at IS NULL LIMIT 1
  // If result is empty: INSERT INTO product_groups (id, household_id, name, deleted_at) VALUES (newUuid, ?, 'Unsorted', NULL)
  // Returns the id either way
  suspend fun ensureUnsortedGroup(householdId: String): String
  ```

- **Review Criteria:**
  - All `watchXxx` return `Flow<T>` backed by `db.watchFlow`.
  - All mutations use `db.execute` or `db.writeTransaction`.
  - No Supabase client used for reads (local SQLite only).
  - `recalculateQuantityToBuy` is invoked in every code path that changes `current_stock` or `minimum_stock`.
  - `submitUnloading` is a single atomic transaction.
  - `ensureUnsortedGroup` is idempotent (SELECT first, INSERT only if absent).

---

### Task 5: Hilt DI Wiring

- **Description:**
  Update `di/SyncModule.kt`:
  - Remove `listsTable`/`todosTable` imports (schema now clean).
  - Add a `@Singleton @Provides` for `GroceryRepository` binding `GroceryRepositoryImpl`.

  Create `di/AppModule.kt` with `@Provides` for:
  - `OpenFoodFactsClient` singleton (Task 12).
  - `AudioFeedback` singleton (Task 13).
  - A dedicated `HttpClient` singleton for Open Food Facts (see Task 12 for configuration).

  **`household_id` is NOT a Hilt binding.** It is read lazily from `SupabaseConnector.sessionStatus` in `AuthViewModel` and passed as a parameter to repository functions. This avoids the session-not-ready-at-app-start problem.

  **`AuthViewModel`** (already exists) must be updated to expose:

  ```kotlin
  val householdId: StateFlow<String?> = sessionStatus
    .map { status ->
      when (status) {
        is SessionStatus.Authenticated ->
          status.session.user?.appMetadata?.get("household_id")
            ?.jsonPrimitive?.contentOrNull
        else -> null
      }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
  ```

  This `householdId` is then passed down to screen-level ViewModels via constructor or assisted injection.

- **Review Criteria:**
  - `GroceryRepository` is injectable anywhere.
  - `householdId` string is injected (not fetched inline in ViewModels).
  - No circular dependencies.

---

### Task 6: Root Navigation (NavHost)

- **Description:**
  Replace `GroceryApp.kt` and the custom `NavController` / `Screen` sealed class with a Compose Navigation `NavHost`.

  **Route definitions** in `ui/navigation/Routes.kt`:

  ```kotlin
  sealed class Route(val path: String) {
    // Auth
    data object SignIn : Route("sign_in")
    data object SignUp : Route("sign_up")
    // Main screens (within the household state machine)
    data object Inventory : Route("inventory")
    data object Shopping : Route("shopping")
    data object Unloading : Route("unloading")
    // Detail (accessible from any main screen)
    data class Detail(val productId: String) : Route("detail/{productId}") {
      companion object { const val TEMPLATE = "detail/{productId}" }
    }
  }
  ```

  **`GroceryApp.kt`** rewritten:

  ```kotlin
  @Composable
  fun GroceryApp(supabase: SupabaseConnector, database: PowerSyncDatabase) {
    val navController = rememberNavController()
    val householdState by repository.watchHousehold().collectAsStateWithLifecycle(null)

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
      composable(Route.SignIn.path) { SignInScreen(...) }
      composable(Route.SignUp.path) { SignUpScreen(...) }
      composable(Route.Inventory.path) { InventoryScreen(navController) }
      composable(Route.Shopping.path) { ShoppingScreen(navController) }
      composable(Route.Unloading.path) { UnloadingScreen(navController) }
      composable(Route.Detail.TEMPLATE) { backStack ->
        val productId = backStack.arguments!!.getString("productId")!!
        DetailScreen(productId, navController)
      }
    }
  }
  ```

  **Navigation rule:** DetailScreen is reachable from all three main screens. Back navigation returns the user to wherever they came from (default back stack behavior).

- **Review Criteria:**
  - `LaunchedEffect` driven by `householdState` is the only trigger for root-level screen switches.
  - DetailScreen route receives `productId` as a route argument (not global state).
  - Auth screens navigate to Inventory only after `householdState` stream emits.
  - No residual references to the old `NavController` or `Screen` sealed class.

---

### Task 7: InventoryScreen

- **Description:**
  Create `ui/screens/inventory/InventoryViewModel.kt` and `ui/screens/inventory/InventoryScreen.kt`.

  **ViewModel:**
  - Injects `GroceryRepository`, `householdId: String`.
  - Exposes:
    ```kotlin
    val groupsWithProducts: StateFlow<List<GroupWithProducts>>
    val snackbarMessage: SharedFlow<SnackbarMessage>  // product name + id for detail navigation
    ```
  - Functions:
    ```kotlin
    fun startShopping()                                         // repository.setHouseholdState(SHOPPING)
    fun decrementStock(product: ProductKind)                    // repository.decrementStock(id); emit snackbar
    fun navigateToDetail(product: ProductKind)                  // emit navigation event
    ```

  **Screen layout:**
  - `Scaffold` with `TopAppBar`:
    - Title: app name.
    - Action: `"Start Shopping"` `TextButton` (top-right).
  - Body: `LazyColumn` with `stickyHeader` per group (uses `ProductGroup.name` as header text).
  - Each product row:
    - `Modifier.animateItem()` for animated reordering.
    - Horizontal swipe gesture (right-to-left) triggers `decrementStock`.
    - `Modifier.hapticFeedback(HapticFeedbackType.TextHandleMove)` on swipe trigger.
    - Tap anywhere on row → `navigateToDetail`.
  - **FAB** (bottom-right): camera icon → opens `InventoryScannerBottomSheet`.
  - `SnackbarHost` at bottom shows: `"[Name]: [newStock] remaining"` with action `"Details"` → navigate to DetailScreen.

  **`InventoryScannerBottomSheet`** (nested composable in same package):
  - Full-width `ModalBottomSheet` with `CameraPreview` + barcode overlay box.
  - On barcode detected → delegate to `ScannerProcessor` (Task 11); result surfaced via Snackbar.
  - **Throttling state** is managed in the BottomSheet's scope via a `BarcodeScanThrottle` helper (Task 11).

- **Review Criteria:**
  - `groupsWithProducts` is a `collectAsStateWithLifecycle` flow — no mutable list caches.
  - `stickyHeader` renders for each group even when product list is empty.
  - Swipe gesture does not block tap; both coexist on the row.
  - FAB opens bottom sheet without navigating away.
  - Snackbar action correctly navigates to DetailScreen.

---

### Task 8: ShoppingScreen

- **Description:**
  Create `ui/screens/shopping/ShoppingViewModel.kt` and `ui/screens/shopping/ShoppingScreen.kt`.

  **ViewModel:**
  - Injects `GroceryRepository`, `householdId`.
  - Exposes three typed flows:
    ```kotlin
    val activeShopping: StateFlow<List<ProductKind>>
    val struckThrough: StateFlow<List<ProductKind>>
    val impulseBuys: StateFlow<List<ProductKind>>
    val searchQuery: StateFlow<String>
    val searchResults: StateFlow<List<ProductKind>>
    ```
  - Functions:
    ```kotlin
    fun finishShopping()
    fun fulfillFull(product: ProductKind)
    fun incrementPending(product: ProductKind)
    fun decrementPending(product: ProductKind)
    fun updateSearchQuery(q: String)
    fun forceAddToCart(product: ProductKind)     // sets pending_stock = 1 if currently 0
    ```

  **Screen layout** (matches spec §3.2 ASCII diagram):
  - `Scaffold` with `TopAppBar`: `"Finish Shopping"` action button.
  - Integrated `SearchBar` anchored at screen top (Material 3 `SearchBar` composable):
    - Expanded state overlays the list full-screen.
    - Displays `searchResults` as a lazy list of `SearchResultCard` items.
    - Each card: product name + `"Force Add"` button (calls `forceAddToCart`) + `"Details"` link.
  - When `SearchBar` is collapsed: three `LazyColumn` sections visible, each with header:
    1. `▼ Active Shopping List` → `activeShopping` items.
    2. `▼ Struck-Through Cart Items` → `struckThrough` items (visually struck through, checkmark checked).
    3. `▼ Impulse Buys` → `impulseBuys` items.
  - Each row: checkbox (ticked when struck-through or impulse-bought), product name + fraction (`pending/qty` or just `pending` for impulse), `[-]` and `[+]` steppers.
  - **Full-fulfillment shortcut:** tap anywhere on the row body (not the steppers) → `fulfillFull`.
  - **Barcode scanner:** FAB or toolbar icon → opens `ShoppingScannerSheet` (same pattern as InventoryScreen but increments `pending_stock` instead of decrementing `current_stock`; new barcode spawns an Unsorted impulse buy).

- **Review Criteria:**
  - The three sections are driven by three independent DB queries (separate `watchFlow` calls), not a single filtered list partitioned in the ViewModel.
  - `SearchBar` uses Material 3 `SearchBar`; search triggers a DB `LIKE` query on `name`.
  - `fulfillFull` uses `repository.fulfillFull` which sets `pending_stock = quantity_to_buy`.
  - Row tap vs. stepper tap events do not interfere.
  - Scanner reuses the same `ScannerProcessor` from Task 11 with a different `onScanAction` lambda.

---

### Task 9: UnloadingScreen

- **Description:**
  Create `ui/screens/unloading/UnloadingViewModel.kt` and `ui/screens/unloading/UnloadingScreen.kt`.

  **ViewModel:**
  - Exposes:
    ```kotlin
    val items: StateFlow<List<ProductKind>>      // WHERE pending_stock > 0
    val showWarningDialog: StateFlow<Boolean>
    ```
  - Functions:
    ```kotlin
    fun toggleUnloadOpen(product: ProductKind, checked: Boolean)
    fun requestSubmit()       // checks for any unload_open == true; sets showWarningDialog
    fun confirmSubmit()       // calls repository.submitUnloading
    fun dismissDialog()
    ```

  **Screen layout** (matches spec §3.3):
  - `Scaffold` with `TopAppBar`: `"Submit Unloading"` action button.
  - `LazyColumn` of audit rows.
  - Each row:
    ```
    [ Checkbox ] Product Name    current + pending = total    [-] [+]
    ```
    - `current + pending = total` rendered as `Text("${current} + ${pending} = ${current + pending}")`.
    - Checkbox: `checked = !product.unloadOpen`. Ticking (→ `unload_open = false`) disables steppers and greys row.
    - Steppers: disabled when `unloadOpen == false` (`enabled = product.unloadOpen`).
    - Steppers adjust `pending_stock` via `repository.setPendingStock`.
  - **Warning AlertDialog:** shown when `showWarningDialog == true`; lists count of unchecked items; two buttons: "Go Back" (dismiss) and "Submit Anyway" (confirm).
  - On `confirmSubmit`: `repository.submitUnloading` → household state becomes `IDLE` → `LaunchedEffect` in `GroceryApp` re-routes to `InventoryScreen`.

- **Review Criteria:**
  - Checkbox state is derived from `unload_open` DB field — no local boolean state.
  - Steppers are `enabled = product.unloadOpen` (true = unlocked).
  - `submitUnloading` in the repository is the single atomic transaction (Task 4).
  - Dialog warns explicitly and requires two taps to bypass.

---

### Task 10: DetailScreen

- **Description:**
  Create `ui/screens/detail/DetailViewModel.kt` and `ui/screens/detail/DetailScreen.kt`.

  **ViewModel:**
  - Receives `productId: String` via Hilt's `SavedStateHandle`.
  - Exposes:
    ```kotlin
    val product: StateFlow<ProductKind?>
    val groups: StateFlow<List<ProductGroup>>   // for group picker dropdown
    val barcodes: StateFlow<List<Barcode>>      // current barcode list
    val isSaving: StateFlow<Boolean>
    ```
  - Functions:
    ```kotlin
    fun updateName(name: String)
    fun updateGroup(groupId: String?)
    fun updateCurrentStock(value: Int)
    fun updateMinimumStock(value: Int)
    fun addBarcode(barcodeNumber: String)
    fun deleteBarcode(barcode: Barcode)
    fun saveChanges()          // calls repository.updateProductKind with current field values
    ```

  **Screen layout:**
  - Full-page `Scaffold` with `TopAppBar` ("Product Detail") and Back arrow.
  - Fields:
    - `OutlinedTextField` for `name` (live edit, saves on `saveChanges`).
    - `ExposedDropdownMenuBox` for `group` (shows all active `ProductGroup` names).
    - Integer stepper or `OutlinedTextField` for `currentStock`.
    - Integer stepper or `OutlinedTextField` for `minimumStock`.
    - `image_path` preview: if non-null, display thumbnail with `AsyncImage` (Coil — already transitively available or add `coil-compose`). If null, show placeholder.
    - Barcode list: chips with delete button; `+` button opens input dialog to add a new barcode string.
  - `"Save"` FAB or `TopAppBar` action.
  - **Accessibility:** All steppers and fields have content descriptions.
  - **Absolute Mutability Policy:** This screen has no phase-lock. It is always fully editable regardless of `household.current_state`.

  **Coil dependency:** Add to `libs.versions.toml`:

  ```toml
  [versions]
  coil = "3.2.0"

  [libraries]
  coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
  ```

  Add to `src/app/build.gradle.kts`:

  ```kotlin
  implementation(libs.coil.compose)
  ```

  **Local file URI handling for `AsyncImage`:** `image_path` stores a raw file-system path (e.g., `/data/user/0/de.curlybracket.grocery/cache/photos/img_123.jpg`). Convert to a content URI before passing to Coil:

  ```kotlin
  val imageUri = remember(product.imagePath) {
    product.imagePath?.let { path ->
      FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        File(path)
      )
    }
  }
  AsyncImage(model = imageUri, contentDescription = "Product image")
  ```

- **Review Criteria:**
  - `product` is driven by `repository.watchProductKind(productId)` — live stream.
  - Saving calls `repository.updateProductKind`; if `currentStock` changed, `recalculateQuantityToBuy` is triggered (inside the repository, not the ViewModel).
  - Screen is navigable from all three main screens.
  - Back navigation works correctly (pops back stack).

---

### Task 11: Barcode Scanner Infrastructure (`scanner/`)

- **Description:**
  Create reusable scanner components consumed by InventoryScreen, ShoppingScreen, and the new-item onboarding pipeline.

  **`scanner/CameraPreviewComposable.kt`:**
  - Wraps `PreviewView` (CameraX) in an `AndroidView` composable.
  - Sets up `ProcessCameraProvider`, `Preview` use case, `ImageAnalysis` use case, and `ImageCapture` use case (needed for photo capture in CaptureRequired state).
  - The `ImageAnalysis` analyzer is injected as a lambda `(ImageProxy) -> Unit`.
  - **Lifecycle binding — exact pattern:**
    ```kotlin
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(lifecycleOwner) {
      val listener = Runnable {
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis, imageCapture)
      }
      cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
      onDispose { cameraProviderFuture.get().unbindAll() }
    }
    ```
  - `ProcessCameraProvider` is created once via `remember`; `DisposableEffect` binds/unbinds on lifecycle events. Never recreated on recomposition.

  **`scanner/BarcodeAnalyzer.kt`:**
  - `ImageAnalysis.Analyzer` implementation using `BarcodeScanning.getClient()`.
  - On successful detection, invokes `onBarcodeDetected(rawValue: String)` callback.
  - Implements the dual-gate throttle per spec §3.1:
    - **Cooldown Gate:** once a barcode `rawValue` is detected, lock that string for 3 seconds via a `HashMap<String, Long>`.
    - **Clear-Frame Gate:** if no barcode detected for 400 ms (tracked by timestamp), reset all locks immediately.
  - Thread-safe via `@GuardedBy` or `AtomicReference` for the lock map + timestamps.

  **`scanner/ScannerProcessor.kt`:**
  - Orchestrates what happens after a barcode is detected. Accepts a sealed `ScannerMode`:
    ```kotlin
    sealed class ScannerMode {
      data class Inventory(val householdId: String) : ScannerMode()
      data class Shopping(val householdId: String) : ScannerMode()
    }
    ```
  - Flow:
    1. `repository.findByBarcode(rawValue, householdId)` — O(1) lookup.
    2. **Hit:** play success beep; apply mode-specific mutation (decrement stock for Inventory / increment pending for Shopping); emit `ScanResult.Hit(product)`.
    3. **Miss:** play failure boop; switch internal `ScannerState` to `CaptureRequired`; start Open Food Facts lookup (Task 12); emit `ScanResult.Miss(barcode)`.
  - Soft-delete resurrection: if found product has `deletedAt != null`, call `repository.restoreProductKind(id)`; play success beep; emit `ScanResult.Restored(product)`.

  **`scanner/ScannerState.kt`:**

  ```kotlin
  sealed class ScannerState {
    data object Scanning : ScannerState()
    data class CaptureRequired(
      val barcode: String,
      val prefillName: String,   // from Open Food Facts or "Unknown Item"
      val photoPath: String?
    ) : ScannerState()
  }
  ```

  **`scanner/BarcodeScannerBottomSheet.kt`:**
  - Shared composable used by InventoryScreen and ShoppingScreen.
  - Accepts `mode: ScannerMode` and `onResult: (ScanResult) -> Unit`.
  - Internal `scannerState: MutableState<ScannerState>` managed within the composable scope via `remember { mutableStateOf(ScannerState.Scanning) }`.

  **State machine (explicit transitions):**

  ```
  Scanning
    ├─ barcode hit (local lookup) → play beep → apply DB mutation → show Snackbar → stay Scanning
    ├─ barcode miss (no local match) → play boop → switch to CaptureRequired(barcode, prefillName="…", photoPath=null)
    └─ barcode hit (soft-deleted) → restore → play beep → show "Restored" Snackbar → stay Scanning

  CaptureRequired(barcode, prefillName, photoPath=null)
    ├─ user edits name field → update prefillName in state
    ├─ user taps shutter button → capture photo → CaptureRequired(barcode, prefillName, photoPath="/cache/…")
    ├─ user taps Save (photoPath required if prefillName=="Unknown Item") → commitNewProduct → Scanning
    └─ user taps Cancel → Scanning
  ```

  **`filepaths.xml` verification:** The manifest already declares a `FileProvider`. Verify that `src/main/res/xml/filepaths.xml` contains both a `<cache-path>` entry (for temporary scanner photos) and a `<files-path>` entry (for permanent product images). If the file only has a `<files-path>` entry (common default), add the `<cache-path>` entry:

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <paths>
    <files-path name="product_images" path="product_images/" />
    <cache-path name="scanner_photos" path="scanner_photos/" />
  </paths>
  ```

  **Photo capture file path generation:**

  ```kotlin
  val photoFile = File(
    context.cacheDir.resolve("scanner_photos").also { it.mkdirs() },
    "scan_${System.currentTimeMillis()}.jpg"
  )
  val photoUri = FileProvider.getUriForFile(
    context,
    "${BuildConfig.APPLICATION_ID}.fileprovider",
    photoFile
  )
  ```

  After successful capture, store `photoFile.absolutePath` in `ScannerState.CaptureRequired.photoPath`.

  **Directory cleanup:** On bottom sheet dismiss, call `context.cacheDir.resolve("scanner_photos").deleteRecursively()` to avoid cache bloat. The final saved image path (moved to permanent location inside `commitNewProduct`) is not deleted.

  **`commitNewProduct` flow (inside `ScannerProcessor`):**
  1. Call `repository.ensureUnsortedGroup(householdId)` to get group id.
  2. If `photoPath != null`, move file from `cacheDir/scanner_photos/` to `filesDir/product_images/` (permanent storage).
  3. Call `repository.createProductKind(householdId, name, groupId, minimumStock=1, barcodeNumber)`.
  4. Emit `ScanResult.Hit` with the new product.

  Snackbar displayed within the bottom sheet on each successful scan.

- **Review Criteria:**
  - `BarcodeAnalyzer` correctly implements both throttle gates (cooldown + clear-frame).
  - `ScannerProcessor` handles hit / miss / soft-delete resurrection in all branches.
  - Bottom sheet does not recreate `ProcessCameraProvider` on recomposition (use `remember` + `DisposableEffect` for lifecycle).
  - Photo capture uses the existing `FileProvider` (`filepaths.xml` already configured in manifest).

---

### Task 12: Open Food Facts Integration (`network/`)

- **Description:**
  Create `network/OpenFoodFactsClient.kt` and `network/OFModels.kt`.

  **Dedicated `HttpClient` for Open Food Facts** — do NOT reuse the Supabase internal Ktor client (it has different timeout/interceptor config). Create a separate singleton in `di/AppModule.kt`:

  ```kotlin
  @Provides
  @Singleton
  @Named("offs")  // qualifier to distinguish from any Supabase-internal client
  fun provideOpen Food FactsHttpClient(): HttpClient = HttpClient(Android) {
    install(ContentNegotiation) {
      json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(HttpTimeout) {
      requestTimeoutMillis = 10_000
      connectTimeoutMillis = 10_000
      socketTimeoutMillis = 10_000
    }
  }
  ```

  This requires adding `io.ktor:ktor-client-content-negotiation` and `io.ktor:ktor-serialization-kotlinx-json` to `libs.versions.toml`. The Ktor version is already transitively pinned by Supabase-kt 3.6.0 — Supabase-kt 3.x uses **Ktor 3.x**. Add to `libs.versions.toml`:

  ```toml
  ktor = "3.1.3"   # must match Supabase-kt 3.6.0 transitive dep; verify with ./gradlew dependencies | grep ktor

  ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
  ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
  ```

  **`network/OFModels.kt`** — Kotlin serializable response DTOs:

  ```kotlin
  @Serializable
  data class OFResponse(
    val status: String,      // "ok" or "not found"
    val product: OFProduct? = null
  )

  @Serializable
  data class OFProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("brands") val brands: String? = null
  )
  ```

  **`network/OpenFoodFactsClient.kt`:**

  ```kotlin
  class OpenFoodFactsClient @Inject constructor(
    @Named("offs") private val httpClient: HttpClient
  ) {
    private val baseUrl = "https://world.openfoodfacts.org/api/v3.6/product"
    private val userAgent = "GroceryShoppingApp/0.1.0 (grocery@curlybracket.de)"

    suspend fun lookupBarcode(barcode: String): OFResult {
      return try {
        val result = attemptLookup(barcode)
        if (result is OFResult.RateLimit) {
          delay(2_000)
          attemptLookup(barcode).let {
            if (it is OFResult.RateLimit) OFResult.Miss else it
          }
        } else result
      } catch (e: Exception) {
        OFResult.Miss
      }
    }

    private suspend fun attemptLookup(barcode: String): OFResult {
      val response = httpClient.get("$baseUrl/$barcode.json") {
        header(HttpHeaders.UserAgent, userAgent)
      }
      return when (response.status.value) {
        200 -> {
          val body = response.body<OFResponse>()
          if (body.status == "ok") {
            val name = body.product?.productName?.takeIf { it.isNotBlank() }
              ?: body.product?.brands?.takeIf { it.isNotBlank() }
              ?: "Unknown Item"
            OFResult.Hit(name)
          } else OFResult.Miss
        }
        503 -> OFResult.RateLimit
        else -> OFResult.Miss
      }
    }
  }

  sealed class OFResult {
    data class Hit(val productName: String) : OFResult()
    data object Miss : OFResult()
    internal data object RateLimit : OFResult()  // internal; resolved in lookupBarcode
  }
  ```

  - On `OFResult.Miss` or exception: `prefillName = "Unknown Item"` → `ScannerState.CaptureRequired`; user must press shutter before Save is enabled.
  - On `OFResult.Hit`: `prefillName = productName` → `ScannerState.CaptureRequired`; Save button is immediately enabled (shutter optional).

- **Review Criteria:**
  - Dedicated `@Named("offs")` `HttpClient` with `ContentNegotiation` and `HttpTimeout` plugins.
  - Custom `User-Agent` header on every request.
  - 503 triggers a single 2 s delayed retry; further 503 → `OFResult.Miss`.
  - All exceptions caught inside `lookupBarcode`; never propagate to UI.
  - `OFResult.Miss` path: Save button disabled until photo captured (shutter pressed).

---

### Task 13: Audio Feedback (`audio/`)

- **Description:**
  Create `audio/AudioFeedback.kt` using `android.media.SoundPool`. SoundPool loads audio asynchronously; a play call before loading completes is silently ignored — guard with loaded flags:

  ```kotlin
  @Singleton
  class AudioFeedback @Inject constructor(@ApplicationContext context: Context) {
    private val soundPool = SoundPool.Builder()
      .setMaxStreams(3)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      .build()

    private var successId = 0
    private var failureId = 0
    private var successLoaded = false
    private var failureLoaded = false

    init {
      soundPool.setOnLoadCompleteListener { _, sampleId, status ->
        if (status == 0) {
          when (sampleId) {
            successId -> successLoaded = true
            failureId -> failureLoaded = true
          }
        }
      }
      successId = soundPool.load(context, R.raw.beep_success, 1)
      failureId = soundPool.load(context, R.raw.beep_failure, 1)
    }

    fun playSuccess() { if (successLoaded) soundPool.play(successId, 1f, 1f, 1, 0, 1f) }
    fun playFailure() { if (failureLoaded) soundPool.play(failureId, 0.7f, 0.7f, 1, 0, 0.8f) }
    fun release() = soundPool.release()
  }
  ```

  Add two short audio files to `src/main/res/raw/`:
  - `beep_success.mp3` — high-pitched short beep (~200 ms). Builder must source a royalty-free file.
  - `beep_failure.mp3` — lower-pitched short boop (~200 ms).

- **Review Criteria:**
  - `SoundPool` loaded asynchronously; guard against playing before load completes using `SoundPool.OnLoadCompleteListener`.
  - `AudioFeedback` released in `GroceryApplication.onTerminate()` or via a `DisposableEffect`.

---

### Task 14: Automated Convergence Engine

- **Description:**
  Per spec §5.1: `quantity_to_buy = MAX(0, minimum_stock - current_stock)` must be recalculated every time `current_stock` changes.

  This is handled **entirely client-side** (no Postgres trigger is involved in the PowerSync flow). The enforcement points are:
  1. `repository.decrementStock` — calls `recalculateQuantityToBuy` after decrement.
  2. `repository.updateProductKind` — calls `recalculateQuantityToBuy` if `currentStock` or `minimumStock` changed.
  3. `repository.submitUnloading` transaction — recalculates for all affected rows after stock merge.

  Additionally, wrap each of these three paths in a PowerSync `writeTransaction` so the recalculation and the stock change are atomic from the sync queue's perspective.

- **Review Criteria:**
  - No path that modifies `current_stock` or `minimum_stock` skips `recalculateQuantityToBuy`.
  - The recalculation SQL is: `UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?`
  - All three enforcement points are inside a single `writeTransaction`.

---

### Task 15: Camera Permission Handling

- **Description:**
  The `CAMERA` permission is already declared in `AndroidManifest.xml`. Runtime permission must be requested before opening the `BarcodeScannerBottomSheet`. Implement a reusable `CameraPermissionHandler` composable:

  ```kotlin
  @Composable
  fun CameraPermissionHandler(
    onGranted: @Composable () -> Unit
  ) {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

    when {
      permissionState.status.isGranted -> onGranted()

      permissionState.status.shouldShowRationale -> {
        // User denied once; show explanation
        AlertDialog(
          onDismissRequest = {},
          title = { Text("Camera Required") },
          text = { Text("The camera is needed to scan barcodes. Please grant camera access.") },
          confirmButton = {
            TextButton(onClick = { permissionState.launchPermissionRequest() }) {
              Text("Grant")
            }
          },
          dismissButton = {
            TextButton(onClick = { /* close bottom sheet */ }) { Text("Cancel") }
          }
        )
      }

      else -> {
        // Not yet requested OR permanently denied
        val context = LocalContext.current
        if (!permissionState.status.isGranted && !permissionState.status.shouldShowRationale) {
          // Permanently denied — redirect to app settings
          AlertDialog(
            onDismissRequest = {},
            title = { Text("Camera Permission Required") },
            text = { Text("Camera access was denied. Open Settings to grant it.") },
            confirmButton = {
              TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
              }) { Text("Open Settings") }
            }
          )
        } else {
          LaunchedEffect(Unit) { permissionState.launchPermissionRequest() }
        }
      }
    }
  }
  ```

  **Usage:** Wrap `BarcodeScannerBottomSheet` content with `CameraPermissionHandler { /* scanner content */ }`.

  **Dependency:** Add `accompanist-permissions` or use the `rememberPermissionState` from `com.google.accompanist:accompanist-permissions`. Check if it is already in `libs.versions.toml`; if not, add:

  ```toml
  [versions]
  accompanist = "0.36.0"

  [libraries]
  accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }
  ```

  Add `implementation(libs.accompanist.permissions)` to `build.gradle.kts`.

- **Review Criteria:**
  - Camera is only opened after permission is granted.
  - First denial shows rationale dialog with "Grant" button.
  - Permanent denial shows settings redirect dialog.
  - No crash on cold start with permission denied.

---

### Task 16: Auth Screens (Minimal Updates)

- **Description:**
  The existing `SignInScreen.kt` and `SignUpScreen.kt` are functionally correct but reference the old `NavController`. Update them to use the new `NavHostController` from Task 6. No visual redesign required. After successful sign-in, the `householdState` flow in `GroceryApp` will automatically route to `InventoryScreen`.

  **`households` row creation on first sign-up:** This is handled by a Supabase Postgres trigger on `auth.users` insert — it is a backend concern, not a client concern. The builder must verify (or document that the backend team must set up) a trigger such as:

  ```sql
  CREATE OR REPLACE FUNCTION create_household_for_new_user()
  RETURNS TRIGGER AS $$
  BEGIN
    INSERT INTO public.households (id, current_state)
    VALUES ('00000000-0000-0000-0000-000000000001', 'IDLE')
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
  END;
  $$ LANGUAGE plpgsql SECURITY DEFINER;

  CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE PROCEDURE create_household_for_new_user();
  ```

  If the trigger is not present and `households` is empty, `watchHousehold()` returns `null`, the root `LaunchedEffect` does nothing, and the user is stuck on the SignIn screen after login. The error should surface as a visible Snackbar: `"Setup incomplete: contact support"` in `GroceryApp` when `householdState == null` for more than 5 seconds post-auth.

- **Review Criteria:**
  - No references to the old `NavController` / `Screen` sealed class.
  - Successful auth does not programmatically call `navController.navigate`; routing is entirely driven by the `householdState` `LaunchedEffect` in `GroceryApp`.
  - Snackbar shown when household state is null after 5 s post-login.

---

### Task 17: End-to-End Integration & Manual QA Checklist

- **Description:**
  Wire everything together and verify functional correctness across the full user journey:

  1. **Auth flow:** Sign up → JWT contains `household_id` in `app_metadata` → PowerSync connects → Inventory loads.
  2. **Inventory:** Items appear grouped with sticky headers; swipe decrements stock; FAB opens scanner.
  3. **Scanner (inventory):** Known barcode → beep + decrement + Snackbar. Unknown barcode → boop → Open Food Facts lookup → `CaptureRequired` overlay. Soft-deleted barcode → resurrect + Snackbar "Restored".
  4. **Start Shopping:** button updates `households.current_state = 'SHOPPING'` → all devices switch to ShoppingScreen.
  5. **Shopping sections:** Active / Struck-through / Impulse Buys reflect DB state in real time. Full-fulfillment tap moves item to struck-through immediately.
  6. **Scanner (shopping):** Unknown → spawns Unsorted impulse buy.
  7. **SearchBar:** Expands, filters `product_kinds` live; force-add works.
  8. **Finish Shopping:** → `UNLOADING` state.
  9. **Unloading:** Rows show formula; checkbox locks/unlocks steppers; submit with unchecked items shows dialog; confirm submits transaction → `IDLE`.
  10. **Detail Screen:** Edits persist; reachable from all three main screens; back works.
  11. **Offline:** Writes queue locally; sync resumes on reconnect.
  12. **Multi-device:** State change on one device updates all others via PowerSync replication.

  **Offline-first scenarios:** 13. **Airplane mode during shopping:** Turn off network → make stepper adjustments → verify writes queue in PowerSync → re-enable network → verify sync uploads and remote DB matches. 14. **Open Food Facts 503 during scan:** Use a proxy or mock to return 503 → verify single retry fires → verify fallback to `"Unknown Item"` + `CaptureRequired` overlay shown. 15. **Long offline period:** App stays on ShoppingScreen offline for 5+ minutes → reconnects → verify household state sync resolves correctly without duplicate writes. 16. **Multi-device state sync:** Device A taps "Start Shopping" → verify Device B automatically navigates to ShoppingScreen within sync latency window (typically < 5 s on LTE).

- **Review Criteria:**
  - All 16 QA scenarios pass.
  - No uncaught exceptions in logcat.
  - `quantity_to_buy` is always `MAX(0, minimum_stock - current_stock)` after any stock change.
  - PowerSync upload queue drains to 0 after reconnect in offline scenario.

---

## Edge Case & Safety Checklist

- **Empty product group:** `stickyHeader` still renders the group name even if its product list is empty.
- **Stock at zero on swipe/scan:** `WHERE current_stock > 0` guard prevents negative stock; DB constraint is a safety net.
- **`pending_stock` at zero on `[-]` stepper in Shopping/Unloading:** guarded at call site; `MAX(0, pending_stock - 1)`.
- **`submitUnloading` with all items checked:** no dialog shown; proceeds immediately.
- **`submitUnloading` with zero pending items:** no-op (transaction UPDATE affects 0 rows); household state set to `IDLE`.
- **Open Food Facts call during airplane mode:** 10 s timeout; caught; falls through to `OFResult.Miss`; user sees `CaptureRequired` overlay.
- **Open Food Facts 503 retry loop:** single retry; not a recursive retry that could loop.
- **Barcode scan of a product with `deleted_at != null`:** resurrection path — `deleted_at = NULL`, `minimum_stock` restored to 1, `recalculateQuantityToBuy` called.
- **Simultaneous scan of same barcode on two devices:** last-write-wins via PowerSync; stock decrement is idempotent directionally (can decrement below user's intent if both fire simultaneously) — acceptable per spec (no custom conflict logic required).
- **`ensureUnsortedGroup` race condition (two devices creating Unsorted at same time):** Supabase will accept both INSERTs with different UUIDs. Workaround: use `SELECT` first (if synced, no INSERT needed); if both insert, two "Unsorted" groups appear. Not critical for v1; document as known limitation.
- **Product with no group assigned (`group_id = NULL`):** Inventory screen must have a catch-all section (e.g., "Unsorted") for products where `group_id IS NULL` or references a deleted group.
- **Camera permission denied:** show a `RationaleDialog` on first denial; on permanent denial, redirect to app settings.
- **`household_id` missing from JWT:** `AuthViewModel.householdId` emits `null`; InventoryViewModel/ShoppingViewModel receive `null` and emit empty lists; UI shows empty state with a Snackbar warning. Does not crash (lazy evaluation per Task 0 design).
- **Background sync:** The existing `SyncService` is a foreground service and runs while the app is in the foreground/background until explicitly stopped. PowerSync SDK does not auto-restart sync after the service is killed; WorkManager integration for deep-background sync is out of scope for v1 (document as known limitation). For household use, foreground sync is sufficient.
- **Navigation route format:** Uses string-based sealed class `Route` (Task 6). The `@Serializable` type-safe nav API requires Compose Navigation 2.8+; verify the version in `libs.versions.toml` before using type-safe routes. If `navigation-compose` is below 2.8, use string routes as specified.
- **DetailScreen opened during `UNLOADING`:** fully editable (per spec §3.4 Absolute Mutability Policy).
- **`AuthViewModel` re-routes after detail navigation:** `LaunchedEffect` for `householdState` uses `popUpTo(0)` to clear back stack — ensures DetailScreen is dismissed when state changes.
- **Coil image loading for `image_path`:** local `file://` URIs require `FileProvider`-based URI — use `FileProvider.getUriForFile` before passing to `AsyncImage`; grant read permission.

---

## Review Log (Plan Review)

- **Round 1:** REJECTED — 6 critical blockers + 4 high-priority issues identified by PlanReviewer. Key gaps: missing Task 0 (bootstrap order), incomplete Ktor HttpClient setup (Task 12), missing Coil version + FileProvider URI (Task 10), incomplete SQL (Task 4), missing camera permission task, missing SoundPool load guard (Task 13). High-priority: vague photo capture flow (Task 11), missing households trigger documentation (Task 15/16), vague index API (Task 2), background sync undocumented.
- **Round 2:** All 6 critical blockers addressed. Round 2 PlanReviewer flagged: (1) PowerSync API `watchFlow` vs `watch` — resolved: all reactive queries now use `db.watch(sql, params) { cursor -> ... }`; (2) SQLite `MAX(0, expr)` validity — confirmed valid in SQLite (multi-arg `max()` is a documented scalar function, not aggregate); clarifying note added to plan; (3) `watchGroupsWithProducts` in-memory merge violates pure stream requirement — resolved: replaced with single denormalized `LEFT JOIN` query (`watchProductsWithGroups`) + pure `Flow.map` transformation in ViewModel. Also: Ktor version pinned to 3.1.3 (Supabase-kt 3.x transitive); `filepaths.xml` verification and `<cache-path>` entry specification added to Task 11.
- **Round 3:** N/A

## Final Status (Code Review)

- **Round 1:** N/A
- **Round 2:** N/A
- **Round 3:** N/A
