# Task 4: Repository Layer

> **Depends on:** Task 2, Task 3
> **Status:** [ ]

---

## Description

Create `GroceryRepository` interface in `domain/repository/GroceryRepository.kt` and implementation `data/repository/GroceryRepositoryImpl.kt`. Inject `PowerSyncDatabase` and `SupabaseConnector` via Hilt. The repository owns all SQL and exposes only typed `Flow`s and `suspend` mutation functions.

---

## PowerSync API Reference

**Reactive queries:**

```kotlin
// Returns Flow<List<T>> that re-emits when any table in the query changes
db.watch(
  sql = "SELECT ...",
  parameters = listOf("param1", "param2")
) { cursor: SqlCursor ->
  /* map SqlCursor to domain object; return T */
}
```

**Single-shot reads:**

```kotlin
db.getAll(sql, params) { cursor -> ... }
db.get(sql, params) { cursor -> ... }   // throws if no row found
```

**Mutations:**

```kotlin
db.execute(sql, params)                         // single statement
db.writeTransaction { execute(...); execute(...) }  // atomic multi-statement
```

**SQLite note:** `MAX(0, expr)` is valid SQLite (multi-arg `max()` is a documented scalar function). Example: `UPDATE product_kinds SET current_stock = MAX(0, current_stock - 1) WHERE id = ?` is correct.

---

## Reactive Flows

```kotlin
// Household state — the root routing signal
fun watchHousehold(): Flow<Household?>

// All active groups (deleted_at IS NULL)
fun watchProductGroups(): Flow<List<ProductGroup>>

// Inventory: active products joined with their group name
// SINGLE denormalized JOIN query; in-memory grouping happens in InventoryViewModel via Flow.map
fun watchProductsWithGroups(householdId: String): Flow<List<ProductWithGroup>>

// Shopping list sections (three independent queries)
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

// Barcode lookup — O(1) single-shot query (use db.get, NOT db.watch)
suspend fun findByBarcode(barcodeNumber: String, householdId: String): ProductKind?
```

---

## SQL Fragments

**`watchHousehold`:**

```sql
SELECT id, current_state, shopping_started_at FROM households LIMIT 1
```

**`watchProductsWithGroups` — denormalized JOIN (the only approach; N+1 multi-query is NOT to be implemented):**

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

**`watchActiveShopping`:**

```sql
SELECT id, household_id, group_id, name, current_stock, minimum_stock,
       quantity_to_buy, pending_stock, image_path, unload_open, deleted_at
FROM product_kinds
WHERE household_id = ? AND deleted_at IS NULL
  AND quantity_to_buy > 0 AND pending_stock < quantity_to_buy
ORDER BY name ASC
```

**`watchStruckThrough`:**

```sql
SELECT ... FROM product_kinds
WHERE household_id = ? AND deleted_at IS NULL
  AND quantity_to_buy > 0 AND pending_stock >= quantity_to_buy
ORDER BY name ASC
```

**`watchImpulseBuys`:**

```sql
SELECT ... FROM product_kinds
WHERE household_id = ? AND deleted_at IS NULL
  AND quantity_to_buy = 0 AND pending_stock > 0
ORDER BY name ASC
```

**`watchUnloadingItems`:**

```sql
SELECT ... FROM product_kinds
WHERE household_id = ? AND deleted_at IS NULL AND pending_stock > 0
ORDER BY name ASC
```

**`watchSearch`:**

```sql
SELECT ... FROM product_kinds
WHERE household_id = ? AND deleted_at IS NULL AND name LIKE ?
ORDER BY name ASC
-- pass "%$query%" as the second parameter
```

**`findByBarcode`** (single-shot `db.get`, not `db.watch`):

```sql
SELECT pk.* FROM product_kinds pk
INNER JOIN barcodes b ON b.product_kind_id = pk.id
WHERE b.household_id = ? AND b.barcode_number = ?
LIMIT 1
```

---

## ViewModel-side grouping for InventoryScreen

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

This is a pure reactive transformation — no mutable cache, no side-effecting merge.

---

## Mutations

```kotlin
// SQL: UPDATE households
//        SET current_state = ?,
//            shopping_started_at = CASE WHEN ? = 'SHOPPING' THEN datetime('now') ELSE shopping_started_at END
//      WHERE id = (SELECT id FROM households LIMIT 1)
// Pass state.name twice as parameters.
suspend fun setHouseholdState(state: HouseholdState)

// Atomic writeTransaction:
// UPDATE product_kinds SET current_stock = MAX(0, current_stock - 1) WHERE id = ?
// UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?
suspend fun decrementStock(productId: String)

// SQL: UPDATE product_kinds SET pending_stock = MAX(0, ?) WHERE id = ?
suspend fun setPendingStock(productId: String, value: Int)

// SQL: UPDATE product_kinds SET pending_stock = pending_stock + 1 WHERE id = ?
suspend fun incrementPendingStock(productId: String)

// SQL: UPDATE product_kinds SET pending_stock = MAX(0, pending_stock - 1) WHERE id = ?
suspend fun decrementPendingStock(productId: String)

// SQL: UPDATE product_kinds SET pending_stock = quantity_to_buy WHERE id = ?
suspend fun fulfillFull(productId: String)

// SQL: UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?
// Always called within the same writeTransaction as the stock change.
suspend fun recalculateQuantityToBuy(productId: String)

// SQL: UPDATE product_kinds SET unload_open = ? WHERE id = ?  (1 = open, 0 = closed)
suspend fun setUnloadOpen(productId: String, open: Boolean)

// Single writeTransaction:
// Step 1: UPDATE product_kinds SET current_stock = current_stock + pending_stock, pending_stock = 0, unload_open = 0
//         WHERE household_id = ? AND pending_stock > 0
// Step 2: UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock)
//         WHERE household_id = ? AND deleted_at IS NULL
// Step 3: UPDATE households SET current_state = 'IDLE'
//         WHERE id = (SELECT id FROM households LIMIT 1)
suspend fun submitUnloading(householdId: String)

// Atomic writeTransaction:
// UPDATE product_kinds SET name = ?, group_id = ?, minimum_stock = ?, current_stock = ?, image_path = ? WHERE id = ?
// then recalculateQuantityToBuy(productId)
suspend fun updateProductKind(productId: String, name: String, groupId: String?,
                              minimumStock: Int, currentStock: Int, imagePath: String?)

// SQL: INSERT INTO barcodes (id, household_id, product_kind_id, barcode_number) VALUES (?, ?, ?, ?)
suspend fun addBarcode(productKindId: String, barcodeNumber: String, householdId: String)

// SQL: DELETE FROM barcodes WHERE id = ?
suspend fun deleteBarcode(barcodeId: String)

// Atomic writeTransaction:
// INSERT INTO product_kinds (id, household_id, group_id, name, current_stock, minimum_stock,
//   quantity_to_buy, pending_stock, image_path, unload_open, deleted_at)
//   VALUES (newUuid, ?, ?, ?, 0, 1, 1, 0, null, 0, null)
// INSERT INTO barcodes (id, household_id, product_kind_id, barcode_number) VALUES (newUuid, ?, newProductId, ?)
// Returns new product id.
suspend fun createProductKind(
  householdId: String, name: String, groupId: String,
  minimumStock: Int = 1, barcodeNumber: String
): String

// SQL: UPDATE product_kinds SET deleted_at = NULL WHERE id = ?
// then recalculateQuantityToBuy(productId)
suspend fun restoreProductKind(productId: String)

// SELECT id FROM product_groups WHERE household_id = ? AND name = 'Unsorted' AND deleted_at IS NULL LIMIT 1
// If empty: INSERT INTO product_groups (id, household_id, name, deleted_at) VALUES (newUuid, ?, 'Unsorted', NULL)
// Returns the id either way. Must be idempotent (SELECT first, INSERT only if absent).
suspend fun ensureUnsortedGroup(householdId: String): String
```

---

## Review Criteria

- All `watchXxx` return `Flow<T>` backed by `db.watch`.
- All mutations use `db.execute` or `db.writeTransaction`.
- No Supabase client used for reads (local SQLite only).
- `recalculateQuantityToBuy` is invoked in every code path that changes `current_stock` or `minimum_stock`.
- `submitUnloading` is a single atomic transaction.
- `ensureUnsortedGroup` is idempotent (SELECT first, INSERT only if absent).
- `findByBarcode` uses `db.get` (single-shot), not `db.watch`.
