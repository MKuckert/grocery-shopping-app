# Database Specification

This document provides the definitive database architecture, schema constraints, security configurations, and synchronization layout for the application. It serves as the single source of truth for both the Supabase (PostgreSQL) cloud backend and the PowerSync (SQLite) client-side embedded engines.

---

## 1. Entity-Relationship Diagram & Schema Breakdown

The storage footprint utilizes a highly optimized, flat structure designed to minimize relationship joins, thereby preventing transaction processing bottlenecks during high-frequency synchronization phases.

```
  ┌─────────────────────────┐             ┌─────────────────────────┐
  │     households          │             │     product_groups      │
  ├─────────────────────────┤             ├─────────────────────────┤
  │ PK  id (UUID)           │             │ PK  id (UUID)           │
  │     current_state (TEXT)│             │     household_id (UUID) │
  │     started_at (TZ)     │             │     name (TEXT)         │
  └─────────────────────────┘             │     deleted_at (TZ)     │
                                          └────────────┬────────────┘
                                                       │ 1
                                                       │
                                                       │ 0..*
  ┌─────────────────────────┐             ┌────────────▼────────────┐
  │        barcodes         │             │      product_kinds      │
  ├─────────────────────────┤             ├─────────────────────────┤
  │ PK  id (UUID)           │             │ PK  id (UUID)           │
  │     household_id (UUID) │ 0..*      1 │     household_id (UUID) │
  │ FK  product_kind_id ────┼────────────►│ FK  group_id (UUID)     │
  │     barcode_number(TEXT)│             │     name (TEXT)         │
  └─────────────────────────┘             │     current_stock (INT) │
                                          │     minimum_stock (INT) │
                                          │     quantity_to_buy(INT)│
                                          │     pending_stock (INT) │
                                          │     image_path (TEXT)   │
                                          │     unload_open (BOOL)  │
                                          │     deleted_at (TZ)     │
                                          └─────────────────────────┘
```

### 1.1 Data Dictionary & Column Specifications

#### Table: `public.households`

Tracks the global state machine configuration for the entire household namespace.

- `id`: `UUID` (PRIMARY KEY). Enforced to a static singleton ID per tenant (e.g., namespace configuration), or unique if tracking multiple distinct setups. Defaults to `'00000000-0000-0000-0000-000000000001'::uuid` for this single-tenant deployment bootstrap.
- `current_shopping_state`: `TEXT` (NOT NULL, DEFAULT `'IDLE'`). State machine restriction boundary. Validated through Postgres check constraint: `IN ('IDLE', 'SHOPPING', 'UNLOADING')`.
- `shopping_started_at`: `TIMESTAMP WITH TIME ZONE` (NULL). Automatically stamped with server `NOW()` when moving to `SHOPPING`. Useful to determine if a cart entry was abandoned weeks ago.

#### Table: `public.product_groups`

Defines operational layout categories (e.g., "Pantry", "Fridge", "Dog Treats") for sorting queries.

- `id`: `UUID` (PRIMARY KEY, DEFAULT `gen_random_uuid()`).
- `household_id`: `UUID` (NOT NULL). Tenant isolation key.
- `name`: `TEXT` (NOT NULL). Category header label.
- `deleted_at`: `TIMESTAMP WITH TIME ZONE` (NULL). Soft-deletion marker column.

#### Table: `public.product_kinds`

The single monolithic table representing both physical inventory presence and dynamic checkout list targets.

- `id`: `UUID` (PRIMARY KEY, DEFAULT `gen_random_uuid()`).
- `household_id`: `UUID` (NOT NULL). Tenant isolation key.
- `group_id`: `UUID` (NULL, FOREIGN KEY references `product_groups.id` ON DELETE SET NULL).
- `name`: `TEXT` (NOT NULL). Descriptive item text.
- `current_stock`: `INTEGER` (NOT NULL, DEFAULT `0`). Enforced non-negative constraint. Tracks actual physical containers resting in storage bins.
- `minimum_stock`: `INTEGER` (NOT NULL, DEFAULT `0`). Safety buffer threshold limit.
- `quantity_to_buy`: `INTEGER` (NOT NULL, DEFAULT `0`). Calculated target depletion requirement metric. Client derived loop target.
- `pending_stock`: `INTEGER` (NOT NULL, DEFAULT `0`). The virtual cart metric tracking items chosen at the market but not yet unboxed.
- `image_path`: `TEXT` (NULL). Remote bucket URI pointing to local or remote images.
- `unload_open`: `BOOLEAN` (NOT NULL, DEFAULT `FALSE`). Unloading state marker for the "Unloading" workflow phase.
- `deleted_at`: `TIMESTAMP WITH TIME ZONE` (NULL). Soft-deletion marker column.

#### Table: `public.barcodes`

A lean associative matrix separating unique barcode strings from generic product identities. Allows multiple physical variations to resolve down to a unified product definition.

- `id`: `UUID` (PRIMARY KEY, DEFAULT `gen_random_uuid()`).
- `household_id`: `UUID` (NOT NULL). Tenant isolation key.
- `product_kind_id`: `UUID` (NOT NULL, FOREIGN KEY references `product_kinds.id` ON DELETE CASCADE).
- `barcode_number`: `TEXT` (NOT NULL). Raw alphanumeric string read directly off hardware scanner arrays.

## 1.2 Client-Side Indexes

The mobile application runs continuously reactive streams targeting the local embedded database file. The client engine injects the following partial and composite indexes.

```sql
-- 1. Accelerates the instant 1:1 hardware scanning array evaluations
CREATE UNIQUE INDEX idx_barcodes_lookup
ON barcodes (household_id, barcode_number);

-- 2. Optimizes regular catalog rendering paths by dropping soft-deleted bloat entries completely
CREATE INDEX idx_product_kinds_active_group
ON product_kinds (household_id, group_id)
WHERE deleted_at IS NULL;

-- 3. High-frequency reactive query execution path for the Stateless Shopping Matrix views
CREATE INDEX idx_product_kinds_shopping_state
ON product_kinds (household_id, quantity_to_buy, pending_stock)
WHERE deleted_at IS NULL;

-- 4. Ensures rapid sorting of category groups without memory-intensive sorting operations
CREATE INDEX idx_product_groups_active
ON product_groups (household_id, display_order)
WHERE deleted_at IS NULL;
```

---

## 2. Server-Side Data Integrity & Logic Rules

### 2.1 Native Check Constraints

Postgres natively enforces the following engine bounds:

```sql
-- Table: product_kinds
ALTER TABLE public.product_kinds ADD CONSTRAINT chk_current_stock_non_negative CHECK (current_stock >= 0);
ALTER TABLE public.product_kinds ADD CONSTRAINT chk_minimum_stock_non_negative CHECK (minimum_stock >= 0);
ALTER TABLE public.product_kinds ADD CONSTRAINT chk_quantity_to_buy_non_negative CHECK (quantity_to_buy >= 0);
ALTER TABLE public.product_kinds ADD CONSTRAINT chk_pending_stock_non_negative CHECK (pending_stock >= 0);

-- Table: households
ALTER TABLE public.households ADD CONSTRAINT chk_shopping_state CHECK (current_state IN ('IDLE', 'SHOPPING', 'UNLOADING'));
```

### 2.2 Product Resurrection Trigger Architecture

When an item is soft-deleted, it remains inside the local client cache. If an offline device scans a barcode belonging to a dead row, updating its `pending_stock` count triggers a resurrection in the database.

---

## 3. Row Level Security (RLS) & Multi-Tenant Isolation

Multi-tenancy relies on explicit structural verification parsed directly from JSON Web Tokens (JWT). This architecture intentionally eliminates additional relational multi-table join operations.

```
       [ Client Request + JWT ]
                  │
                  ▼
   [ Supabase RLS Policy Check ]
                  │
                  ├─► Extracts Claim: `auth.jwt() -> 'app_metadata' -> 'household_id'`
                  │
                  └─► Validates Execution Target: `WHERE household_id = token_claim_id`
```

### 3.1 Security Policies Definition (Postgres)

```sql
-- Enable security processing layers
ALTER TABLE public.product_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.product_kinds ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.barcodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.households ENABLE ROW LEVEL SECURITY;

-- Restrictive execution isolation policies mapping specific context bounds
CREATE POLICY household_isolation_policy ON public.product_groups
    AS RESTRICTIVE USING (household_id = (auth.jwt() -> 'app_metadata' ->> 'household_id')::uuid);

CREATE POLICY household_isolation_policy ON public.product_kinds
    AS RESTRICTIVE USING (household_id = (auth.jwt() -> 'app_metadata' ->> 'household_id')::uuid);

CREATE POLICY household_isolation_policy ON public.barcodes
    AS RESTRICTIVE USING (household_id = (auth.jwt() -> 'app_metadata' ->> 'household_id')::uuid);

-- Household state security: allows reading only if you belong to that static setup ID
CREATE POLICY households_isolation_policy ON public.households
    AS RESTRICTIVE USING (id = (auth.jwt() -> 'app_metadata' ->> 'household_id')::uuid);
```

---

## 4. PowerSync Replication & Synchronization Topology

PowerSync monitors Postgres Write-Ahead Logs (WAL) via its replication backend, evaluating custom sync pipelines to assemble individualized client SQLite caches.

### 4.1 Sync Rules Specification (`sync_rules.yaml`)

```yaml
bucket_definitions:
  # Unique sync bucket assigned to each distinct household instance
  household_bucket:
    parameters: select household_id from profiles where id = :user.id

pipelines:
  - name: households
    request: |
      SELECT * FROM households
      WHERE id = :user.app_metadata.household_id

  - name: product_groups
    request: |
      SELECT * FROM product_groups 
      WHERE household_id = :user.app_metadata.household_id

  - name: product_kinds
    request: |
      SELECT * FROM product_kinds 
      WHERE household_id = :user.app_metadata.household_id

  - name: barcodes
    request: |
      SELECT * FROM barcodes 
      WHERE household_id = :user.app_metadata.household_id
```
