# Task 14: Automated Convergence Engine

> **Depends on:** Task 4 (Repository Layer)
> **Status:** [ ]

---

## Description

Per spec §5.1: `quantity_to_buy = MAX(0, minimum_stock - current_stock)` must be recalculated every time `current_stock` or `minimum_stock` changes.

This is handled **entirely client-side** (no Postgres trigger is involved in the PowerSync flow).

The three enforcement points are:
1. `repository.decrementStock` — calls `recalculateQuantityToBuy` after decrement.
2. `repository.updateProductKind` — calls `recalculateQuantityToBuy` if `currentStock` or `minimumStock` changed.
3. `repository.submitUnloading` transaction — recalculates for all affected rows after stock merge.

Each of these three paths must be wrapped in a PowerSync `writeTransaction` so the recalculation and the stock change are atomic from the sync queue's perspective.

---

## Review Criteria

- No path that modifies `current_stock` or `minimum_stock` skips `recalculateQuantityToBuy`.
- The recalculation SQL is: `UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?`
- All three enforcement points are inside a single `writeTransaction`.
