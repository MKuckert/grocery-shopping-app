# Plan: Bug Fixes, Hygiene & Feature Sprint

## Objective

Fix critical soft-delete bugs, add missing database timestamps, remove dead code, improve product management UX (auto-save, deletion, barcode linking), extract all hardcoded strings for i18n with German translation, fix camera overlap and autofill bugs, and establish project tooling (.editorconfig, portrait lock).

## Requirements & Decisions

- **Frameworks:** Jetpack Compose (Material 3), Hilt DI, PowerSync + Supabase, CameraX + ML Kit, Ktor
- **Chosen Libraries:** No new libraries required. All tasks use existing dependencies. `kotlinx.coroutines.flow.debounce` (already available via coroutines-core) for auto-save.
- **Error Handling Strategy:**
  - **Soft-delete bugs:** Queries must always filter `deleted_at IS NULL` for active data. A deleted product found by barcode or ID must not appear in UI. Fail loud: if a query returns a deleted record due to a missed filter, it's a bug — never silently display stale data.
  - **Auto-save (debounced):** Network/DB errors during auto-save must surface a Snackbar with retry option. Never silently discard user changes. Use `try/catch` around `repository.updateProductKind()` and emit `SnackbarMessage` on failure.
  - **Barcode linking (duplicate barcode):** PowerSync will reject duplicate `(household_id, barcode_number)`. Catch the exception in `ScannerProcessor.linkBarcodeToProduct()`, play failure sound, and show clear error: "This barcode is already linked to another product."
  - **Product group creation:** Empty or blank group names must be rejected client-side before DB write. Duplicate names within the same household should show a warning.
  - **Product deletion:** Use existing soft-delete (`deleted_at = datetime('now')`). Show confirmation dialog before deletion. Provide undo via Snackbar with `restoreProductKind()`.
  - **Schema migration (created_at/updated_at):** Existing rows will have NULL timestamps. Supabase migration must backfill with `CURRENT_TIMESTAMP`. Client code uses `datetime('now')` for new writes.
  - **Camera overlap:** Fix is in `CameraPreviewComposable.kt` — replace `MATCH_PARENT` layout params with `0` (to let Compose control sizing via modifiers). Apply `Modifier.clipToBounds()` on camera containers.
  - **Autofill:** Add `AutofillType.EmailAddress` and `AutofillType.Password` semantics, keyboard options with `imeAction`, and `KeyboardActions` for field navigation.

## Implementation Steps

> Status Markers: [ ] Open, [/] In Progress, [x] Completed (set after accepted review only!)
>
> **Dependency chain:** Tasks 10, 13, and 14 add new INSERT/UPDATE SQL statements. Task 3 adds `created_at`/`updated_at` to ALL SQL statements. **Task 3 must be completed before Tasks 10, 13, and 14** to ensure new SQL includes timestamps from the start. Task 4 (Supabase migration) should be deployed before or alongside Task 3.

- [x] **Task 1: Fix soft-delete query bugs in GroceryRepositoryImpl**
  - **Description:** Add `WHERE deleted_at IS NULL` filter to `watchProductKind()` (line ~128) and `findByBarcode()` (line ~172). Both currently return deleted products as if they were active.
  - **Files:** `data/repository/GroceryRepositoryImpl.kt`
  - **Review Criteria:**
    - `watchProductKind()` SQL includes `AND deleted_at IS NULL` condition
    - `findByBarcode()` SQL includes `AND pk.deleted_at IS NULL` in the JOIN or WHERE clause
    - Unit tests verify that deleted products are NOT returned by either method
    - No regression in existing queries that already filter correctly

- [x] **Task 2: Deduplicate quantity_to_buy recalculation SQL**
  - **Description:** The query `UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?` appears 5 times in `GroceryRepositoryImpl`: once in the existing public `recalculateQuantityToBuy()` method (line ~241, uses `db.execute()`), and 4 more inline duplicates inside `writeTransaction` blocks in `decrementStock()` (line ~207), `submitUnloading()` (line ~268), `updateProductKind()` (line ~297), and `restoreProductKind()` (line ~351) — all using `tx.execute()`.
    Refactor:
    1. Create a `private fun recalculateQuantityToBuyTx(tx: Transaction, productId: String)` that uses `tx.execute()` for the SQL
    2. Replace the 4 inline duplicates with calls to this transaction-aware helper
    3. Rewrite the existing public `recalculateQuantityToBuy()` (interface method, line ~53 in `GroceryRepository.kt`) to delegate: open a `db.writeTransaction` and call the private helper inside it
    This preserves transactional integrity for callers already inside a transaction, and provides a standalone entry point for external callers (e.g., `ScannerProcessor`).
  - **Files:** `data/repository/GroceryRepositoryImpl.kt`, `domain/repository/GroceryRepository.kt` (interface unchanged, just implementation refactored)
  - **Review Criteria:**
    - Private `recalculateQuantityToBuyTx(tx, productId)` helper exists and uses `tx.execute()`
    - All 4 inline duplicates replaced with calls to the private helper, passing their transaction context
    - Public `recalculateQuantityToBuy(productId)` wraps in `db.writeTransaction` and delegates to private helper
    - Total occurrences of the SQL string reduced from 5 to 1
    - Existing behavior unchanged (verify via existing tests)

- [x] **Task 3: Add `created_at` and `updated_at` to PowerSync schema (client-side)**
  - **Description:** Add `Column.text("created_at")` and `Column.text("updated_at")` to all 4 tables in `AppSchema.kt`. Update ALL INSERT statements in `GroceryRepositoryImpl` to include `datetime('now')` for both columns. Update ALL UPDATE statements to set `updated_at = datetime('now')`. Domain models (`ProductKind`, `ProductGroup`, `Household`, `Barcode`) do NOT need these fields exposed yet — they are for sync/audit only.
  - **Files:** `data/db/AppSchema.kt`, `data/repository/GroceryRepositoryImpl.kt`
  - **Review Criteria:**
    - All 4 tables in `AppSchema` include both new columns
    - Every INSERT statement sets both `created_at` and `updated_at` to `datetime('now')`
    - Every UPDATE statement sets `updated_at = datetime('now')`
    - No INSERT/UPDATE statement is missed (grep for `.execute(` to verify)
    - Tests updated to expect new columns in SQL

- [ ] **Task 4: Add `created_at` and `updated_at` to Supabase (server-side)**
  - **Description:** **Has to be performed by different agent than Builder! Stop here and bail out if you are the Builder agent!** Write Supabase SQL migration to add `created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL` and `updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL` to all 4 tables (`households`, `product_groups`, `product_kinds`, `barcodes`). Backfill existing rows. Create a `moddatetime` trigger on each table to auto-update `updated_at` on server-side changes.
  - **Files:** New migration SQL file (document in plan, execute manually in Supabase dashboard)
  - **Review Criteria:**
    - ALTER TABLE statements for all 4 tables with `DEFAULT NOW()` and `NOT NULL`
    - Existing rows are backfilled (DEFAULT handles this for NOT NULL columns)
    - `moddatetime` trigger is created for each table
    - Migration is idempotent (uses `IF NOT EXISTS` or similar guards)

- [ ] **Task 5: Remove signup screen and Route.SignUp**
  - **Description:** Create a bash command to delete `SignUpScreen.kt`, remove `Route.SignUp` from `Routes.kt`, remove the `composable(Route.SignUp.path)` entry from the NavHost in `GroceryApp.kt`. Note: `SignInScreen.kt` does NOT contain a link to signup — no changes needed there. A human will perform the deletion as you have no permissions to do so.
  - **Files:** `ui/screens/SignUpScreen.kt` (delete), `ui/navigation/Routes.kt`, `GroceryApp.kt`
  - **Review Criteria:**
    - `SignUpScreen.kt` is deleted
    - `Route.SignUp` is removed from sealed class
    - No navigation references to signup remain
    - App compiles and sign-in still works

- [ ] **Task 6: Lock to portrait orientation**
  - **Description:** Add `android:screenOrientation="portrait"` to `<activity>` in `AndroidManifest.xml`.
  - **Files:** `AndroidManifest.xml`
  - **Review Criteria:**
    - `android:screenOrientation="portrait"` is present on `MainActivity`
    - App does not rotate to landscape

- [ ] **Task 7: Add .editorconfig**
  - **Description:** Create `.editorconfig` at project root with Kotlin/Android conventions: 4-space indent, UTF-8, LF line endings, max line length 120, trailing whitespace trimmed, final newline enforced. Include sections for `*.kt`, `*.kts`, `*.xml`, `*.json`.
  - **Files:** `.editorconfig` (new file at project root)
  - **Review Criteria:**
    - File exists at project root
    - Kotlin section uses 4-space indent and 120 char line length
    - XML section uses 4-space indent
    - Root directive is `root = true`

- [ ] **Task 8: Fix camera preview overlapping bottom sheet**
  - **Description:** In `CameraPreviewComposable.kt`, replace `ViewGroup.LayoutParams.MATCH_PARENT` for both width and height with `0` (or remove explicit layout params entirely and let Compose modifiers control sizing). The `AndroidView` modifier from callers already specifies dimensions. Additionally, add `Modifier.clipToBounds()` to the camera `Box` containers in `BarcodeScannerBottomSheet.kt` (`ScanningContent` at ~line 182 and `CaptureRequiredContent` at ~line 253) to prevent any overflow.
  - **Files:** `scanner/CameraPreviewComposable.kt`, `scanner/BarcodeScannerBottomSheet.kt`
  - **Review Criteria:**
    - `PreviewView` no longer uses `MATCH_PARENT` layout params
    - Camera containers use `clipToBounds()` modifier
    - Camera preview respects Compose height constraints (200.dp in scanning, 200.dp in capture)
    - No visual overflow on either `ScanningContent` or `CaptureRequiredContent` states
    - Bottom sheet buttons/fields are not obscured by camera preview

- [ ] **Task 9: Fix sign-in autofill for password managers**
  - **Description:** Update `SignInScreen.kt` to enable proper password manager integration. The correct Compose API (1.6+, SDK 35+) is `Modifier.semantics { contentType = ContentType.xxx }` — there is no `Modifier.autofill()` or `AutofillHint` in Compose.
    1. Email field: Add `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)`, add `keyboardActions = KeyboardActions(onNext = { /* focus password field */ })`, add autofill hint via `Modifier.semantics { contentType = ContentType.EmailAddress + ContentType.Username }`
    2. Password field: Add `imeAction = ImeAction.Done` to existing `keyboardOptions`, add `keyboardActions = KeyboardActions(onDone = { /* trigger sign-in */ })`, add autofill hint via `Modifier.semantics { contentType = ContentType.Password }`. Consider using `SecureTextField` (Material 3) instead of `TextField` with `PasswordVisualTransformation` for better platform integration.
    3. Use `FocusRequester` to chain email → password → submit flow
    4. On sign-in button click, call `LocalAutofillManager.current?.commit()` to save credentials to the password manager
    5. Imports: `androidx.compose.ui.semantics.contentType`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.autofill.ContentType`
  - **Files:** `ui/screens/SignInScreen.kt`
  - **Review Criteria:**
    - Email field has `KeyboardType.Email`, `ImeAction.Next`, and `ContentType.EmailAddress + ContentType.Username` semantics
    - Password field has `ImeAction.Done` triggering sign-in and `ContentType.Password` semantics
    - `FocusRequester` chains field navigation correctly
    - `LocalAutofillManager.current?.commit()` called on successful sign-in
    - Password managers can detect and fill both fields

- [ ] **Task 10: Add product deletion (soft-delete) to detail screen**
  - **Depends on:** Task 3 (timestamps must be in place for new SQL)
  - **Description:** Add a delete action to `DetailScreen`. Implementation:
    1. Add `deleteProductKind(productId: String)` to `GroceryRepository` interface
    2. Implement in `GroceryRepositoryImpl`: `UPDATE product_kinds SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?`
    3. **Barcode cascade decision:** Barcodes are NOT cascade-deleted. Task 1's `findByBarcode()` fix already filters by `pk.deleted_at IS NULL`, so barcodes linked to deleted products won't resolve. If the product is restored via undo, its barcodes remain intact.
    4. Add a delete button "delete product" under all existing elements in the detail screen.
    5. Show confirmation `AlertDialog` on tap: "Delete {productName}? This can be undone."
    6. On confirm: call `viewModel.deleteProduct()` → `repository.deleteProductKind(productId)`
    7. Navigate back to inventory after deletion, passing the deleted product ID via `savedStateHandle` navigation result (use `previousBackStackEntry.savedStateHandle.set("deleted_product_id", productId)`)
    8. In `InventoryViewModel` or `InventoryScreen`, observe the navigation result. On receiving a deleted product ID, show Snackbar with "Undo" action that calls `repository.restoreProductKind(productId)`. Clear the result after handling.
    9. Unit test for `deleteProductKind()` verifying `deleted_at` is set
  - **Files:** `ui/screens/detail/DetailScreen.kt`, `ui/screens/detail/DetailViewModel.kt`, `domain/repository/GroceryRepository.kt`, `data/repository/GroceryRepositoryImpl.kt`, `ui/screens/inventory/InventoryScreen.kt` (observe navigation result), `ui/screens/inventory/InventoryViewModel.kt` (undo logic)
  - **Review Criteria:**
    - `deleteProductKind()` exists in interface and implementation
    - SQL sets both `deleted_at` and `updated_at`
    - Barcodes are NOT deleted (documented design decision)
    - Delete button visible in detail TopAppBar
    - Confirmation dialog shown before deletion
    - Navigation returns to inventory with product ID via `savedStateHandle`
    - Undo Snackbar appears on inventory screen and successfully restores product
    - Deleted product no longer appears in inventory list
    - Unit test covers the delete method

- [ ] **Task 11: Auto-save on product detail changes (remove FAB)**
  - **Description:** Replace the explicit FAB save with debounced auto-save:
    1. In `DetailViewModel`, combine all editable state flows (`_name`, `_groupId`, `_currentStock`, `_minimumStock`) using `combine().drop(1).debounce(800)` and call `saveChanges()` on emission. Launch this in `init {}` inside `viewModelScope`.
    2. Remove the FAB from `DetailScreen.kt`
    3. Remove `_isSaving` state (no longer needed for button)
    4. Keep save error handling: on failure, show Snackbar with error message
    5. Add a subtle save indicator (e.g., small "Saved" text or checkmark that briefly appears after successful save)
    6. **Race condition guard for `_userEditing`:** Do NOT simply set `_userEditing = false` after save. Instead, after a successful save, snapshot the saved values. Only reset `_userEditing = false` if the current state flow values still match the saved snapshot (i.e., the user didn't edit during save). If they differ, leave `_userEditing = true` — the debounce will fire again for the new changes.
    7. **`onCleared()` immediate save:** Override `onCleared()` in `DetailViewModel`. Cancel any pending debounce job and trigger an immediate `saveChanges()` call (blocking via `runBlocking` if needed to ensure write completes before ViewModel is destroyed).
  - **Files:** `ui/screens/detail/DetailScreen.kt`, `ui/screens/detail/DetailViewModel.kt`
  - **Review Criteria:**
    - FAB is removed from detail screen
    - Changes auto-save after 800ms of inactivity
    - `drop(1)` prevents saving the initial state load
    - Save errors surface as Snackbar (fail loud)
    - Visual feedback confirms save occurred
    - Rapid sequential edits only trigger one save (debounce works correctly)
    - `_userEditing` reset only occurs when saved values match current values (no race)
    - `onCleared()` triggers immediate save of pending changes
    - Unit test: verify debounce triggers save, verify `onCleared()` triggers save

- [ ] **Task 12: Add product group creation in detail screen**
  - **Depends on:** Task 3 (new INSERT must include `created_at`/`updated_at`)
  - **Description:** Add ability to create new product groups from the detail screen's group dropdown:
    1. Add "Create new group..." option at the bottom of the `GroupDropdown` menu
    2. On tap, show `AlertDialog` with text field for group name
    3. Validate: non-blank, trimmed
    4. Check for duplicate group name within the same household: compare against the existing groups list (already available via `watchProductGroups()` flow). If duplicate, show inline error text "A group with this name already exists" and disable the confirm button. This is a client-side check only — no DB constraint.
    5. Add `createProductGroup(householdId: String, name: String): String` to `GroceryRepository` interface (returns new group ID)
    6. Implement in `GroceryRepositoryImpl`: INSERT into `product_groups` with new UUID, household_id, name, `created_at = datetime('now')`, `updated_at = datetime('now')`, `deleted_at = null`
    7. After creation, auto-select the new group in the dropdown
  - **Files:** `ui/screens/detail/DetailScreen.kt`, `ui/screens/detail/DetailViewModel.kt`, `domain/repository/GroceryRepository.kt`, `data/repository/GroceryRepositoryImpl.kt`
  - **Review Criteria:**
    - "Create new group" option visible in dropdown
    - Dialog validates non-blank input
    - Duplicate group name shows inline error and disables confirm button
    - New group is persisted in database with correct household_id and timestamps
    - New group auto-selected after creation
    - Group list refreshes reactively (via existing `watchProductGroups()` flow)

- [ ] **Task 13a: Inventory barcode linking — state & processor logic**
  - **Depends on:** Task 3 (new INSERT must include timestamps)
  - **Description:** Backend logic for linking an unknown barcode to an existing product:
    1. Add `ScannerState.LinkToExisting(val barcode: String)` to `ScannerState` sealed class
    2. Add `ScanResult.Linked(val product: ProductKind)` to `ScanResult` sealed class (distinct from `Hit` to allow different UI feedback)
    3. Add `suspend fun linkBarcodeToProduct(barcode: String, productId: String, householdId: String)` to `ScannerProcessor`. Implementation: call `repository.addBarcode(productId, barcode, householdId)`, play success sound, emit `ScanResult.Linked(product)`. Catch exceptions (duplicate barcode): play failure sound, log error, re-throw for UI to handle.
    4. The existing `repository.addBarcode()` method is reused — no new repository method needed.
  - **Files:** `scanner/ScannerState.kt`, `scanner/ScanResult.kt`, `scanner/ScannerProcessor.kt`
  - **Review Criteria:**
    - `ScannerState.LinkToExisting` added to sealed class
    - `ScanResult.Linked` added to sealed class
    - `ScannerProcessor.linkBarcodeToProduct()` handles success and error paths
    - Duplicate barcode exception is caught, logged, and re-thrown
    - Unit test for `linkBarcodeToProduct()` happy path and duplicate error

- [ ] **Task 13b: Inventory barcode linking — UI**
  - **Depends on:** Task 13a
  - **Description:** UI for the barcode-to-existing-product linking flow:
    1. In `BarcodeScannerBottomSheet`, in the `CaptureRequired` state composable, add a "Link to Existing" `OutlinedButton` next to the "Save" button
    2. Tapping "Link to Existing" transitions state to `ScannerState.LinkToExisting(barcode)`
    3. New `LinkToExistingContent` composable within the bottom sheet:
       - Search `TextField` at the top
       - `LazyColumn` of matching products from `repository.watchSearch(query, householdId)` — manage the flow via a local `MutableStateFlow` for the query, collected with `collectAsStateWithLifecycle()`
       - Each product item is tappable
       - On product tap: call `processor.linkBarcodeToProduct(barcode, productId, householdId)` in a coroutine. On success: transition back to `Scanning` state. On error: show inline error text.
       - "Cancel" button returns to `CaptureRequired` state
    4. Handle `ScanResult.Linked` in existing scan result collectors (InventoryScreen, ShoppingScreen) — show Snackbar "Barcode linked to {productName}"
  - **Files:** `scanner/BarcodeScannerBottomSheet.kt`, `ui/screens/inventory/InventoryScreen.kt`, `ui/screens/shopping/ShoppingScreen.kt`
  - **Review Criteria:**
    - "Link to Existing" button visible in `CaptureRequired` state
    - Product search works reactively
    - Selected product gets barcode linked
    - Duplicate barcode error shown inline, not crash
    - Success returns to scanning state with feedback
    - Snackbar shown on inventory/shopping screens for `ScanResult.Linked`
    - Flow works in both inventory and shopping scanner modes

- [ ] **Task 14: Extract all hardcoded strings to strings.xml**
  - **Description:** Move all ~80+ hardcoded user-facing strings to `res/values/strings.xml`. This includes:
    - Screen titles ("Sign In", "Product Detail", "Shopping", "Inventory", "Unloading")
    - Button labels ("Save", "Cancel", "Add", "Submit", etc.)
    - Text field labels ("Email", "Password", "Name", "Barcode number", etc.)
    - Dialog titles and messages
    - Snackbar messages (use string templates with `%s` / `%d` for dynamic values)
    - Content descriptions for icons and images
    - Error messages
    Use `stringResource(R.string.xxx)` in Composables. Use `context.getString(R.string.xxx)` in ViewModels/non-Composable code. Follow Android naming convention: `screen_component_description` (e.g., `sign_in_title`, `detail_btn_save`, `inventory_snackbar_remaining`).
  - **Files:** `res/values/strings.xml`, ALL Composable files in `ui/screens/`, `scanner/BarcodeScannerBottomSheet.kt`, `scanner/CameraPermissionHandler.kt`
  - **Review Criteria:**
    - Zero hardcoded user-facing strings remain in Kotlin Composable files
    - All strings use `stringResource()` or `context.getString()`
    - String names follow consistent naming convention
    - Format strings use proper `%s`/`%d` placeholders
    - App behavior is unchanged (visual verification)

- [ ] **Task 15: Add German translation**
  - **Description:** Create `res/values-de/strings.xml` with German translations for all strings extracted in Task 14. Translate professionally — not machine-literal. Use formal "Sie" form. Ensure format placeholders (`%s`, `%d`) are preserved.
  - **Files:** `res/values-de/strings.xml` (new file)
  - **Review Criteria:**
    - All strings from `values/strings.xml` have German counterparts
    - Translations are natural German, not machine-literal
    - Format placeholders match exactly (same count and order)
    - Switching device locale to German shows German strings

## Edge Case & Safety Checklist

- [ ] `watchProductKind()` with a deleted product ID returns empty/null, not the deleted product
- [ ] `findByBarcode()` with a barcode linked to a deleted product returns null
- [ ] Scanner restoring a soft-deleted product correctly recalculates `quantity_to_buy`
- [ ] Auto-save debounce: rapidly typing a name triggers only one DB write after 800ms idle
- [ ] Auto-save: navigating away mid-debounce still saves (handle `onCleared()` in ViewModel)
- [ ] Auto-save: initial load of product data does NOT trigger a save (use `drop(1)`)
- [ ] Product deletion while offline queues the soft-delete locally and syncs later
- [ ] Undo-delete Snackbar timeout: if user doesn't tap undo, deletion stands
- [ ] Linking barcode to product that already has that barcode shows error, not crash
- [ ] Creating a product group with blank name is rejected
- [ ] Camera preview respects Compose modifiers after fix (verify on different screen sizes)
- [ ] CameraPreview in bottom sheet doesn't prevent bottom sheet dismiss gesture
- [ ] Portrait lock doesn't break existing camera functionality
- [ ] String extraction preserves dynamic values (product names, stock counts) via format args
- [ ] German translation handles pluralization correctly where applicable
- [ ] `created_at` is set once on INSERT, never updated; `updated_at` is updated on every UPDATE
- [ ] Supabase migration is safe to run on a database with existing data (non-destructive)

## Review Log (Plan Review)

- **Round 1:** NOT APPROVED — Revision Required (2026-07-10)
  - B1 (Task 2 transaction integrity): Fixed — added private `recalculateQuantityToBuyTx(tx, productId)` helper, public method wraps in transaction
  - B2 (Task 10 SQL + barcode cascade): Fixed — specified exact SQL, documented barcode cascade decision, added cross-screen undo mechanism via `savedStateHandle`
  - B3 (Task ordering): Fixed — added dependency chain note at top + explicit `Depends on` markers on Tasks 10, 13, 14a
  - C1 (Task 2 count + existing helper): Fixed — rewritten to acknowledge 5 occurrences and existing public method
  - C2 (Task 11 race): Fixed — `_userEditing` reset now conditional on saved values matching current values
  - C3 (Task 11 `onCleared()`): Fixed — added explicit step 7 for `onCleared()` immediate save
  - C4 (Task 10 cross-screen Snackbar): Fixed — specified `previousBackStackEntry.savedStateHandle` navigation result approach
  - C5 (Task 12 simplified mode): Fixed — replaced with new `BarcodeScanOnlySheet` composable instead of reusing complex `BarcodeScannerBottomSheet`
  - C6 (Task 14 splitting): Fixed — split into Task 14a (state + processor) and Task 14b (UI)
  - C7 (Task 13 duplicate name): Fixed — added client-side duplicate check with inline error in dialog
  - C8 (Task 5 signup link): Fixed — removed inaccurate reference, noted SignInScreen needs no changes

- **Round 2:** APPROVED (2026-07-10)
  - All 3 blockers (B1–B3) verified resolved against codebase. Explorer confirmed all code claims accurate.
  - All 8 concerns (C1–C8) verified resolved. No regressions introduced.
  - Dependency chain is sound: Tasks 1,2 independent; Task 3 gates 10,13,14a; Task 14b gates on 14a; Task 16 gates on 15.
  - Minor note (non-blocking): Task 11 Step 7 `runBlocking` in `onCleared()` — builder should add comment explaining safety (local SQLite, sub-ms).
  - Minor note (non-blocking): Task 15 should be executed after all UI tasks (8–14b) to capture all new strings in one sweep.
- **Round 3:** APPROVED (2026-07-11)
  - Verified by a human
  - Dropped **Task 12: Add barcode scanning to product detail screen** to leave it for a later refactor of the scanner UI.

## Final Status (Code Review)

- **Round 1:** APPROVED — Task 1 (2026-07-11)
  - `watchProductKind()`: `AND deleted_at IS NULL` confirmed at line 134. Pass.
  - `findByBarcode()`: `AND pk.deleted_at IS NULL` confirmed at line 180. Pass.
  - Tests: 3 tests cover SQL filter verification and null-return behavior. Pass.
  - Regression audit: All 12 `db.watch()`/`db.getOptional()` calls reviewed — no missing soft-delete filters. Pass.
- **Round 2:** APPROVED — Task 2 (2026-07-11)
  - Private `recalculateQuantityToBuyTx(tx: PowerSyncTransaction, productId: String)` confirmed at line 368. Uses `tx.execute()`. Pass.
  - 3 of 4 claimed inline duplicates replaced with helper calls: `decrementStock` (L207), `updateProductKind` (L291), `restoreProductKind` (L342). Pass.
  - `submitUnloading` (L260–267) retains inline SQL — uses `WHERE household_id = ? AND deleted_at IS NULL` (batch recalc for entire household). Structurally different query; cannot use the by-ID helper without semantic/performance regression. Acceptable deviation.
  - Public `recalculateQuantityToBuy()` wraps in `db.writeTransaction` and delegates to private helper (L239–241). Pass.
  - SQL string `...WHERE id = ?` appears exactly 1 time (L370, inside helper). Down from 4. Pass.
  - Tests verify transactional delegation (`decrementStock`, `recalculateQuantityToBuy`) and `submitUnloading` independently. No tests removed. Pass.
  - No regressions in other methods. Interface unchanged.
- **Round 3:** APPROVED — Task 3 (2026-07-11)
  - Schema: All 4 tables (`households`, `product_groups`, `product_kinds`, `barcodes`) include `Column.text("created_at")` and `Column.text("updated_at")`. Pass.
  - INSERTs: All 4 INSERT statements (`addBarcode`, `createProductKindWithBarcode` ×2, `ensureUnsortedGroup`) set both `created_at` and `updated_at` to `datetime('now')`. Pass.
  - UPDATEs: All 13 UPDATE statements include `updated_at = datetime('now')`. `created_at` is never modified on UPDATE. Pass.
  - Completeness: 18 `.execute()` calls total = 4 INSERTs + 13 UPDATEs + 1 DELETE (`deleteBarcode`). DELETE correctly excluded from timestamp updates. No missed statements. Pass.
  - Tests: All SQL assertions updated to expect `updated_at = datetime('now')` in UPDATE statements, and INSERT tests verify presence of timestamp columns. Pass.
  - Domain models remain unchanged — timestamps are sync/audit only. Pass.
