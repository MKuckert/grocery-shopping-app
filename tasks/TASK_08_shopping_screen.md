# Task 8: ShoppingScreen

> **Depends on:** Task 6, Task 11 (scanner)
> **Status:** [x]

---

## Description

Create `ui/screens/shopping/ShoppingViewModel.kt` and `ui/screens/shopping/ShoppingScreen.kt`.

### ViewModel

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
  fun forceAddToCart(product: ProductKind)   // sets pending_stock = 1 if currently 0
  ```

### Screen layout (matches spec §3.2)

- `Scaffold` with `TopAppBar`: `"Finish Shopping"` action button.
- Material 3 `SearchBar` anchored at screen top:
  - Expanded state overlays the list full-screen.
  - Displays `searchResults` as a lazy list of `SearchResultCard` items.
  - Each card: product name + `"Force Add"` button (calls `forceAddToCart`) + `"Details"` link.
- When `SearchBar` is collapsed: three `LazyColumn` sections visible, each with header:
  1. `▼ Active Shopping List` → `activeShopping` items.
  2. `▼ Struck-Through Cart Items` → `struckThrough` items (visually struck through, checkmark checked).
  3. `▼ Impulse Buys` → `impulseBuys` items.
- Each row: checkbox (ticked when struck-through or impulse-bought), product name + fraction (`pending/qty` or just `pending` for impulse), `[-]` and `[+]` steppers.
- **Full-fulfillment shortcut:** tap anywhere on the row body (not the steppers) → `fulfillFull`.
- **Barcode scanner:** FAB or toolbar icon → opens `ShoppingScannerSheet` (same `BarcodeScannerBottomSheet` from Task 11, but with `ScannerMode.Shopping` — increments `pending_stock` instead of decrementing `current_stock`; new barcode spawns an Unsorted impulse buy).

### Navigation

- Replace the corresponding TODO comment for `Route.Shopping.path` route in `GroceryApp.kt` with a navigation to `ShoppingScreen`.

---

## Review Criteria

- Three sections are driven by three independent DB queries (separate `watchFlow` calls), not a single filtered list partitioned in the ViewModel.
- `SearchBar` uses Material 3 `SearchBar`; search triggers a DB `LIKE` query on `name`.
- `fulfillFull` uses `repository.fulfillFull` which sets `pending_stock = quantity_to_buy`.
- Row tap vs. stepper tap events do not interfere.
- Scanner reuses `ScannerProcessor` from Task 11 with `ScannerMode.Shopping`.
