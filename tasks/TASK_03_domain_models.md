# Task 3: Domain Models

> **Depends on:** Task 1
> **Status:** [/]

---

## Description

Create data classes in `domain/model/`.

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

**Mapping from `SqlCursor`:** every data class must have a companion `fromCursor(cursor: SqlCursor)` factory (or equivalent extension). Column order follows the `SELECT` clause order used in Task 4 queries. `SqlCursor` column access: `cursor.getString(index)`, `cursor.getLong(index)`, `cursor.getDouble(index)`.

---

## Review Criteria

- All models are `@Immutable`.
- `unloadOpen` is `Boolean`, not `Int`, in the domain model.
- `fromCursor` helpers compile and correctly parse null TEXT columns as `null`.
