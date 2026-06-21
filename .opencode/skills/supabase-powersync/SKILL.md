---
name: supabase-powersync
description: Use when implementing offline-first data sync with Supabase (PostgreSQL backend) and PowerSync (local SQLite sync engine). Covers initialization, authentication flow, schema design, RLS security, conflict resolution, and common pitfalls.
metadata:
  created: 2026-06-21
  libraries: Supabase v3.6, PowerSync v1.13
  tags: database, supabase, powersync, offline-first, data-sync, rls, conflict-resolution
  verified: false
---

# Supabase & PowerSync Integration

## Overview

**Supabase** is a PostgreSQL-based Backend-as-a-Service (BaaS) providing authentication, real-time subscriptions, storage, and serverless functions.

**PowerSync** is an offline-first sync engine that replicates a subset of Postgres data to a local SQLite database on the client, enabling instant reads, reliable writes even when offline, and automatic cloud synchronization.

**Integration Pattern:** Client ↔ PowerSync SDK ↔ PowerSync Service ↔ Supabase (Postgres)

---

## Critical Concepts for LLM Implementation

### 1. Authentication Flow (Non-Negotiable)

The security model ties JWT tokens to sync access control. **Do not skip or simplify this.**

1. User authenticates via Supabase Auth → receives Supabase JWT
2. Client passes this JWT to PowerSync SDK via `.connect(url, { token: jwt })`
3. PowerSync Service validates the JWT against Supabase's JWKS endpoint
4. PowerSync Server applies Sync Rules to determine which rows the user can sync
5. All subsequent syncs are guarded by the JWT and sync rules

**Key Constraint:** PowerSync relies on the JWT's `sub` claim (user ID) and custom claims (tenant, role, etc.) to filter data. If JWT validation fails, the client disconnects from PowerSync.

**Common Failure:** Passing wrong token type, expired tokens, or not refreshing the JWT before expiry.

### 2. Row Level Security (RLS) is Mandatory

Supabase RLS policies filter data at the database layer. PowerSync respects this but does NOT replace it.

- Define RLS policies in Supabase to enforce who can read/write what
- PowerSync's Sync Rules are an additional filter on top of RLS
- **Both must align.** If a user's JWT claim says they can see a record but RLS denies it, the write will fail when synced

Example RLS policy (SQL):

```sql
CREATE POLICY "users_can_edit_own_tasks"
ON tasks FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);
```

PowerSync Sync Rule (pseudo-code in dashboard):

```
bucket('user_tasks') {
  upload tasks;
  check(task.user_id == request.auth.sub);
}
```

### 3. Data Flow Semantics

**Reads:** Supabase → PowerSync Service → Local SQLite → Client queries SQLite directly. (Near-zero latency)

**Writes:** Client writes to local SQLite → PowerSync queues the write → PowerSync Service uploads → Supabase applies RLS/Postgres constraints → success or conflict

**Connection loss:** Writes stay in local SQLite queue. When connection resumes, PowerSync automatically retries uploads.

### 4. Sync Rules Define the Data Subset

Sync Rules are NOT SQL; they are declarative rules that partition the database per user. You must define them in the PowerSync Dashboard (or API) because they control:

- Which tables to replicate
- Which rows each user can access (usually filtered by user_id or tenant_id)
- Upload vs. download permissions

**Misunderstanding:** Sync Rules do NOT replace WHERE clauses in queries. They define the initial sync boundary.

### 5. Conflict Resolution: Last-Write-Wins

PowerSync uses a last-write-wins (LWW) strategy by default:

- If two clients write different values to the same column, the write with the latest timestamp wins
- At the row level, not column level
- To implement custom conflict logic, use Postgres triggers or Supabase Edge Functions to rewrite conflicting values

### 6. Schema Alignment

Your local PowerSync schema must mirror the tables and columns you are syncing. Mismatch will cause sync errors.

```typescript
// PowerSync expects a schema object mapping table names to column definitions
const schema = {
  todos: {
    columns: [
      { name: "id", type: "text", isPrimary: true },
      { name: "user_id", type: "text" },
      { name: "title", type: "text" },
      { name: "completed", type: "boolean" },
      { name: "created_at", type: "text" },
    ],
  },
};
```

---

## Initialization Boilerplate

### Supabase Client Setup

```typescript
import { createClient } from "@supabase/supabase-js";

const supabase = createClient(
  process.env.SUPABASE_URL || "https://your-project.supabase.co",
  process.env.SUPABASE_ANON_KEY || "your-anon-key",
);
```

### PowerSync Client Setup (Web)

```typescript
import { PowerSyncDatabase } from "@powersync/web";
import { IndexedDBStorage } from "@powersync/web/storage/indexeddb";

// 1. Define schema
const schema = {
  todos: {
    columns: [
      { name: "id", type: "text", isPrimary: true },
      { name: "user_id", type: "text" },
      { name: "title", type: "text" },
      { name: "completed", type: "boolean" },
      { name: "created_at", type: "text" },
    ],
  },
};

// 2. Initialize database
const db = new PowerSyncDatabase({
  schema,
  database: {
    storage: new IndexedDBStorage("my_app"),
  },
});

// 3. Connect with Supabase JWT
async function connectPowerSync() {
  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (!session) {
    throw new Error("Not authenticated");
  }

  await db.connect(
    process.env.POWERSYNC_URL || "https://your-powersync.supabase.co",
    {
      token: session.access_token,
    },
  );

  console.log("PowerSync connected");
}

// 4. Use local database for queries
async function getTodos() {
  const result = await db.execute("SELECT * FROM todos WHERE user_id = ?", [
    supabase.auth.user()?.id,
  ]);
  return result.rows;
}

// 5. Insert into local database (auto-syncs to Supabase)
async function addTodo(title) {
  const userId = supabase.auth.user()?.id;
  await db.execute(
    "INSERT INTO todos (id, user_id, title, completed, created_at) VALUES (?, ?, ?, ?, ?)",
    [crypto.randomUUID(), userId, title, false, new Date().toISOString()],
  );
  // PowerSync detects the change and queues it for upload
}
```

### PowerSync Client Setup (Mobile - React Native / Flutter)

The pattern is identical except storage backend:

**React Native:**

```typescript
import { PowerSyncDatabase } from "@powersync/react-native";
import { SQLiteStorage } from "@powersync/react-native/storage/sqlite";

const db = new PowerSyncDatabase({
  schema,
  database: {
    storage: new SQLiteStorage("my_app"),
  },
});
```

**Flutter:**

```dart
import 'package:powersync/powersync.dart';

final db = PowerSyncDatabase(
  schema: schema,
  storage: SqliteStorage('my_app'),
);

await db.connect(
  url: 'https://your-powersync.supabase.co',
  token: jwtToken,
);
```

---

## Authentication Integration Patterns

### Pattern 1: Token Refresh on Expiry

Supabase sessions expire. You **must** refresh the JWT before PowerSync attempts to sync with an expired token.

```typescript
// Listen for Supabase auth state changes
supabase.auth.onAuthStateChange((event, session) => {
  if (event === "SIGNED_IN" || event === "TOKEN_REFRESHED") {
    // Re-connect PowerSync with new token
    db.connect(POWERSYNC_URL, {
      token: session.access_token,
    });
  } else if (event === "SIGNED_OUT") {
    // Disconnect PowerSync
    db.disconnect();
  }
});
```

### Pattern 2: Custom Claims in JWT (For Multi-Tenant)

Supabase allows custom claims in the JWT via the `auth.users` metadata:

```sql
-- In Supabase SQL editor, set custom claim on user
UPDATE auth.users
SET raw_user_meta_data = jsonb_set(raw_user_meta_data, '{tenant_id}', '"org-123"')
WHERE id = 'user-id';
```

Then reference it in PowerSync Sync Rules:

```
bucket('org_data') {
  upload todos;
  check(todos.tenant_id == request.auth.claim('tenant_id'));
}
```

---

## Schema Design Best Practices

### 1. Keep Local Schema Lean

Avoid syncing large BLOBs or files. Instead, sync metadata and URLs.

**Bad:**

```typescript
const schema = {
  posts: {
    columns: [
      { name: "id", type: "text", isPrimary: true },
      { name: "content", type: "text" },
      { name: "image_blob", type: "blob" }, // ❌ Large binary data
    ],
  },
};
```

**Good:**

```typescript
const schema = {
  posts: {
    columns: [
      { name: "id", type: "text", isPrimary: true },
      { name: "content", type: "text" },
      { name: "image_url", type: "text" }, // ✅ URL only, fetch from Supabase Storage
    ],
  },
};
```

### 2. Include Timestamp Columns

PowerSync uses timestamps for conflict resolution. Always include `created_at` and `updated_at`.

```sql
CREATE TABLE todos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id),
  title TEXT NOT NULL,
  completed BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
);

-- Trigger to auto-update timestamp
CREATE TRIGGER update_todos_timestamp
BEFORE UPDATE ON todos
FOR EACH ROW
EXECUTE FUNCTION moddatetime(updated_at);
```

### 3. Normalize Relations Carefully

PowerSync syncs tables, not joins. If you need related data, sync both tables and perform joins locally.

**Database Schema:**

```sql
CREATE TABLE lists (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  title TEXT NOT NULL,
);

CREATE TABLE todos (
  id UUID PRIMARY KEY,
  list_id UUID NOT NULL REFERENCES lists(id),
  user_id UUID NOT NULL,
  title TEXT NOT NULL,
);
```

**PowerSync Sync Rule:**

```
bucket('user_lists_and_todos') {
  upload lists;
  upload todos;
  check(lists.user_id == request.auth.sub);
  check(todos.user_id == request.auth.sub);
}
```

**Client-Side Query:**

```typescript
const lists = await db.execute("SELECT * FROM lists WHERE user_id = ?", [
  userId,
]);
const todos = await db.execute("SELECT * FROM todos WHERE user_id = ?", [
  userId,
]);

// Join in memory
const listsWithTodos = lists.rows._array.map((list) => ({
  ...list,
  todos: todos.rows._array.filter((t) => t.list_id === list.id),
}));
```

---

## Common Pitfalls & Solutions

### Pitfall 1: Using Supabase Client Instead of PowerSync for Queries

❌ **Wrong:**

```typescript
// This will fail when offline
const { data } = await supabase.from("todos").select("*");
```

✅ **Right:**

```typescript
// Query local SQLite
const result = await db.execute("SELECT * FROM todos WHERE user_id = ?", [
  userId,
]);
```

### Pitfall 2: Forgetting to Refresh JWT Before Sync

❌ **Wrong:**

```typescript
// JWT might be expired
await db.connect(url, { token: session.access_token });
```

✅ **Right:**

```typescript
// Refresh first
const {
  data: { session },
} = await supabase.auth.refreshSession();
await db.connect(url, { token: session.access_token });
```

### Pitfall 3: RLS and Sync Rules Misalignment

❌ **Wrong:**

```sql
-- RLS allows user to read
CREATE POLICY "view_all" ON todos FOR SELECT USING (TRUE);
```

```
-- Sync rule restricts
bucket('my_todos') {
  check(todos.user_id == request.auth.sub);
}
```

This creates inconsistency: the user can read all todos via Supabase API but only sync their own via PowerSync.

✅ **Right:**

```sql
-- RLS restricts
CREATE POLICY "users_own_todos" ON todos FOR SELECT USING (user_id = auth.uid());
```

```
-- Sync rules also restrict the same way
bucket('my_todos') {
  check(todos.user_id == request.auth.sub);
}
```

### Pitfall 4: Attempting to Manually Manage Sync Queue

❌ **Wrong:** Writing custom sync logic, trying to detect conflicts manually.

✅ **Right:** Let PowerSync handle sync queue and conflict detection. Use Postgres triggers or Edge Functions for complex business logic conflicts.

### Pitfall 5: Syncing Entire Database Without Filtering

❌ **Wrong:**

```
bucket('everything') {
  upload *;
}
```

This replicates all tables to all users, destroying privacy and wasting bandwidth.

✅ **Right:**

```
bucket('user_data') {
  upload todos;
  upload lists;
  check(todos.user_id == request.auth.sub);
  check(lists.user_id == request.auth.sub);
}
```

---

## Conflict Resolution Strategies

### Last-Write-Wins (Default)

PowerSync applies the timestamp-based last-write-wins (LWW) strategy. The write with the latest `updated_at` timestamp wins.

### Custom Conflict Resolution via Postgres Triggers

For complex scenarios (e.g., incrementing a counter), implement a trigger:

```sql
CREATE FUNCTION resolve_sync_conflict()
RETURNS TRIGGER AS $$
BEGIN
  -- If conflict is detected (NEW.updated_at == OLD.updated_at but values differ)
  -- Apply custom logic, e.g., sum values for counters
  IF NEW.count IS DISTINCT FROM OLD.count THEN
    NEW.count = NEW.count + OLD.count; -- Sum instead of overwrite
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER before_update_on_counter
BEFORE UPDATE ON counter_table
FOR EACH ROW
EXECUTE FUNCTION resolve_sync_conflict();
```

### Custom Conflict Resolution via Edge Functions

Supabase Edge Functions can hook into PowerSync's API to intercept writes and apply custom logic before persisting.

---

## Monitoring & Debugging

### Checking Sync Status

```typescript
// Observe sync status
db.watchStatus((status) => {
  console.log("Sync status:", status.connected ? "connected" : "disconnected");
  console.log("Upload queue:", status.uploadQueue);
  console.log("Download progress:", status.dataFlowStatus);
});
```

### Common Error Scenarios

| Error                  | Cause                               | Solution                                                      |
| ---------------------- | ----------------------------------- | ------------------------------------------------------------- |
| `JWT invalid`          | Token expired or wrong secret       | Refresh JWT with `supabase.auth.refreshSession()`             |
| `RLS policy violation` | User lacks permission in Supabase   | Check RLS policy in Supabase console; ensure JWT claims match |
| `Sync rule mismatch`   | User not allowed by Sync Rule       | Verify `request.auth.sub` in sync rule matches JWT sub claim  |
| `Schema mismatch`      | Local schema doesn't match Postgres | Ensure all synced columns are defined in PowerSync schema     |
| `Offline queue stuck`  | Upload fails repeatedly             | Check Postgres constraints; ensure RLS allows the write       |

---

## Supabase + PowerSync Version Compatibility

| Supabase | PowerSync Web | PowerSync RN | Notes                                     |
| -------- | ------------- | ------------ | ----------------------------------------- |
| v1.0+    | v1.0+         | v1.0+        | Stable. JWKS validation is critical.      |
| —        | v1.5+         | v1.5+        | Added support for custom claim filtering. |

**Key Breaking Changes:**

- Supabase rotates JWKS keys periodically. PowerSync Service must fetch keys dynamically, not cache them.
- Upgrading Postgres extensions (e.g., pgvector) can change index structures; re-test sync rules.

---

## Recommended Workflow for Implementation

1. **Define Database Schema** in Supabase (tables, columns, constraints, RLS policies)
2. **Mirror Schema** in PowerSync client (define exactly which tables/columns to sync)
3. **Define Sync Rules** in PowerSync Dashboard (specify user-data filtering logic)
4. **Implement Authentication** (Supabase Auth → JWT → PowerSync connect)
5. **Add Token Refresh** (hook into auth state changes to re-connect PowerSync)
6. **Test Offline** (simulate connection loss to verify local reads/queued writes work)
7. **Test Conflict Scenarios** (two clients writing same row, one goes offline, both retry)
8. **Monitor Sync Queue** (observe upload queue in development)

---

## References

- [Supabase Documentation](https://supabase.com/docs)
- [PowerSync Documentation](https://docs.powersync.com/intro/powersync-overview)
- [PowerSync Documentation, LLM Index](https://docs.powersync.com/llms.txt)
- [PowerSync + Supabase Integration Guide](https://docs.powersync.com/integrations/supabase/guide)
- [Postgres Row Level Security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
