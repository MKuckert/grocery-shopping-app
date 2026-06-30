# Task 2: Update PowerSync Schema (`AppSchema.kt`)

> **Depends on:** Task 1
> **Status:** [/]

---

## Description

Rewrite `data/db/AppSchema.kt`. Remove `listsTable` / `todosTable` imports and entries. Ensure to include the full grocery schema.

**Note:** PowerSync `Column` type has no `boolean` — use `Column.integer` for boolean columns; 0 = false, 1 = true.

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

Also add local SQLite index hints via PowerSync `Table.indexes` parameter as defined in `src/DATABASE.md` §1.2:

- `idx_barcodes_lookup`: unique index on `barcodes(household_id, barcode_number)`.
- `idx_product_kinds_active_group`: index on `product_kinds(household_id, group_id)`.
- `idx_product_kinds_shopping_state`: index on `product_kinds(household_id, quantity_to_buy, pending_stock)`.

**PowerSync SDK index API (1.13.x):**

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

Apply the same pattern for the `product_kinds` indexes.

**Note:** PowerSync SDK does not support partial index `WHERE` clauses in the `Index` class — omit partial filters; they are defined in Postgres and enforced server-side. The client-side indexes are for query performance only.

---

## Review Criteria

- `AppSchema` compiles; no references to `listsTable` / `todosTable`.
- All four tables present with correct column types.
- Three indexes declared.
