# Task 9: UnloadingScreen

> **Depends on:** Task 6
> **Status:** [ ]

---

## Description

Create `ui/screens/unloading/UnloadingViewModel.kt` and `ui/screens/unloading/UnloadingScreen.kt`.

### ViewModel

- Exposes:
  ```kotlin
  val items: StateFlow<List<ProductKind>>      // WHERE pending_stock > 0
  val showWarningDialog: StateFlow<Boolean>
  ```
- Functions:
  ```kotlin
  fun toggleUnloadOpen(product: ProductKind, checked: Boolean)
  fun requestSubmit()    // checks for any unload_open == true; sets showWarningDialog
  fun confirmSubmit()    // calls repository.submitUnloading
  fun dismissDialog()
  ```

### Screen layout (matches spec §3.3)

- `Scaffold` with `TopAppBar`: `"Submit Unloading"` action button.
- `LazyColumn` of audit rows.
- Each row:
  ```
  [ Checkbox ] Product Name    current + pending = total    [-] [+]
  ```
  - `current + pending = total` rendered as `Text("${current} + ${pending} = ${current + pending}")`.
  - Checkbox: `checked = !product.unloadOpen`. Ticking (→ `unload_open = false`) disables steppers and greys row.
  - Steppers: `enabled = product.unloadOpen` (true = unlocked for adjustment).
  - Steppers adjust `pending_stock` via `repository.setPendingStock`.
- **Warning `AlertDialog`:** shown when `showWarningDialog == true`; lists count of unchecked items; two buttons:
  - `"Go Back"` → `dismissDialog()`
  - `"Submit Anyway"` → `confirmSubmit()`
- On `confirmSubmit`: `repository.submitUnloading` → household state becomes `IDLE` → `LaunchedEffect` in `GroceryApp` re-routes to `InventoryScreen` automatically.

### Navigation

- Replace the corresponding TODO comment for `Route.Unloading.path` route in `GroceryApp.kt` with a navigation to `UnloadingScreen`.

---

## Review Criteria

- Checkbox state is derived from `unload_open` DB field — no local boolean state.
- Steppers are `enabled = product.unloadOpen` (true = unlocked).
- `submitUnloading` in the repository is the single atomic transaction (Task 4).
- Dialog warns explicitly and requires two taps to bypass.
