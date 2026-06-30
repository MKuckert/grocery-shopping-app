# Task 7: InventoryScreen

> **Depends on:** Task 6
> **Status:** [ ]

---

## Description

Create `ui/screens/inventory/InventoryViewModel.kt` and `ui/screens/inventory/InventoryScreen.kt`.

### ViewModel

- Injects `GroceryRepository`, `householdId: String`.
- Exposes:
  ```kotlin
  val groupsWithProducts: StateFlow<List<GroupWithProducts>>
  val snackbarMessage: SharedFlow<SnackbarMessage>  // product name + id for detail navigation
  ```
- Functions:
  ```kotlin
  fun startShopping()                          // repository.setHouseholdState(SHOPPING)
  fun decrementStock(product: ProductKind)     // repository.decrementStock(id); emit snackbar
  fun navigateToDetail(product: ProductKind)   // emit navigation event
  ```

### Screen layout

- `Scaffold` with `TopAppBar`:
  - Title: app name.
  - Action: `"Start Shopping"` `TextButton` (top-right).
- Body: `LazyColumn` with `stickyHeader` per group (uses `GroupWithProducts.groupName` as header text).
- Each product row:
  - `Modifier.animateItem()` for animated reordering.
  - Horizontal swipe gesture (right-to-left) triggers `decrementStock`.
  - `Modifier.hapticFeedback(HapticFeedbackType.TextHandleMove)` on swipe trigger.
  - Tap anywhere on row → `navigateToDetail`.
- **FAB** (bottom-right): camera icon → opens `InventoryScannerBottomSheet`.
- `SnackbarHost` at bottom shows: `"[Name]: [newStock] remaining"` with action `"Details"` → navigate to DetailScreen.

### `InventoryScannerBottomSheet` (nested composable in same package)

- Full-width `ModalBottomSheet` with `CameraPreview` + barcode overlay box.
- On barcode detected → delegate to `ScannerProcessor` (Task 11); result surfaced via Snackbar.
- Throttling state managed via `BarcodeAnalyzer` (Task 11).

### Navigation

- Replace the corresponding TODO comment for `Route.Inventory.path` route in `GroceryApp.kt` with a navigation to `InventoryScreen`.

---

## Review Criteria

- `groupsWithProducts` is a `collectAsStateWithLifecycle` flow — no mutable list caches.
- `stickyHeader` renders for each group even when the product list is empty.
- Swipe gesture does not block tap; both coexist on the row.
- FAB opens bottom sheet without navigating away.
- Snackbar action correctly navigates to DetailScreen.
