---
name: supabase-powersync
description: Use when implementing offline-first data sync with Supabase (PostgreSQL backend) and PowerSync (local SQLite sync engine). Covers initialization, authentication flow, schema design, RLS security, conflict resolution, and common pitfalls. Includes Kotlin/Android-specific patterns.
metadata:
  created: 2026-06-21
  updated: 2026-06-21
  libraries: Supabase v3.6, PowerSync v1.12+, Kotlin KMP, AndroidX
  tags: database, supabase, powersync, offline-first, data-sync, rls, conflict-resolution, kotlin, android, kmp, jetpack-compose
  verified: false
  sources:
    - https://docs.powersync.com/intro/powersync-overview
    - https://docs.powersync.com/integrations/supabase/guide
    - https://docs.powersync.com/llms.txt
    - https://www.postgresql.org/docs/current/ddl-rowsecurity.html
---

# Supabase & PowerSync Integration

## Overview

**Supabase** is a PostgreSQL-based Backend-as-a-Service (BaaS) providing authentication, real-time subscriptions, storage, and serverless functions.

**PowerSync** is an offline-first sync engine that replicates a subset of Postgres data to a local SQLite database on the client, enabling instant reads, reliable writes even when offline, and automatic cloud synchronization.

**Integration Pattern:** Client ↔ PowerSync SDK ↔ PowerSync Service ↔ Supabase (Postgres)

---

## Kotlin/Android Implementation Stack

### SDK & Dependencies

**Official PowerSync Kotlin Multiplatform (KMP) SDK:**
- `com.powersync:core` (v1.12.0+) — Core PowerSync engine
- `com.powersync:connector-supabase` — Supabase-specific connector
- `com.powersync:powersync-compose` — Jetpack Compose reactive integration
- `com.powersync:room-integration` (Beta) — Room ORM bridge
- `com.powersync:sqldelight-integration` (Beta) — SQLDelight ORM support

**build.gradle.kts example:**

```kotlin
dependencies {
    implementation("com.powersync:core:1.12.0")
    implementation("com.powersync:connector-supabase:1.12.0")
    implementation("com.powersync:powersync-compose:1.12.0")
    
    // Supabase
    implementation("io.github.supabase:gotrue-kt:2.0+")
    implementation("io.github.supabase:supabase-kt:2.0+")
    
    // AndroidX / Jetpack
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7+")
    implementation("androidx.compose.runtime:runtime:1.6+")
}
```

### Key Android-Specific Constraints

1. **PowerSync Manages SQLite Lifecycle**: Unlike traditional Room apps where you own the database file, PowerSync manages the local SQLite instance. The synced "tables" are actually **SQLite Views** over PowerSync's internal sync layer. Do NOT attempt standard DDL (CREATE TABLE, DROP TABLE) on synced tables.

2. **Background Sync & WorkManager**: PowerSync SDK supports background sync, but it does NOT automatically integrate with Android's `WorkManager`. For apps that must sync when backgrounded or killed, you must manually schedule PowerSync sync tasks via `WorkManager`.

3. **Token Management in Android Context**: Unlike web/RN, Android's auth state (Supabase AuthClient) is typically tied to the app lifecycle. Implement a `PowerSyncBackendConnector` that fetches credentials on-demand, ensuring the SDK automatically refreshes expired tokens.

4. **View vs. Table**: The most common pitfall in Kotlin implementations is attempting DML (INSERT/UPDATE) operations on what appears to be a table name but is actually a SQLite View. Always use `db.execute()` for DML; never use `db.createTable()` on synced data.

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

---

## Initialization Boilerplate (Kotlin/Android)

**1. Define Schema (Kotlin):**

```kotlin
import com.powersync.core.Schema
import com.powersync.core.Table
import com.powersync.core.Column
import com.powersync.core.ColumnType

// Define the structure of synced tables
val mySchema = Schema(
    tables = listOf(
        Table(
            name = "todos",
            columns = listOf(
                Column("id", ColumnType.TEXT),
                Column("task", ColumnType.TEXT),
                Column("is_completed", ColumnType.INTEGER),
                Column("user_id", ColumnType.TEXT),
                Column("created_at", ColumnType.TEXT),
                Column("updated_at", ColumnType.TEXT)
            )
        )
    )
)
```

**2. Implement PowerSyncBackendConnector (Supabase Integration):**

```kotlin
import com.powersync.core.PowerSyncBackendConnector
import com.powersync.core.PowerSyncCredentials
import io.github.supabase.gotrue.SupabaseAuthClient

class SupabasePowerSyncConnector(
    private val supabaseAuth: SupabaseAuthClient,
    private val powerSyncUrl: String
) : PowerSyncBackendConnector {
    
    override suspend fun fetchCredentials(): PowerSyncCredentials {
        // Get the current session and JWT from Supabase
        val session = supabaseAuth.currentSession
            ?: throw IllegalStateException("User not authenticated")
        
        return PowerSyncCredentials(
            url = powerSyncUrl,
            token = session.accessToken,
            expiresAt = session.expiresAt
        )
    }
    
    override suspend fun uploadData(data: ByteArray): Result<Unit> {
        // Custom upload logic if needed; default is usually sufficient
        return Result.success(Unit)
    }
}
```

**3. Initialize PowerSync Database (in Repository or ViewModel):**

```kotlin
import com.powersync.core.PowerSyncDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TodoRepository(
    private val connector: SupabasePowerSyncConnector,
    private val schema: Schema
) : ViewModel() {
    
    private lateinit var db: PowerSyncDatabase
    
    suspend fun initialize() {
        // PowerSync manages the SQLite instance internally
        db = PowerSyncDatabase(
            schema = schema,
            connector = connector
        )
        
        // Establish connection to PowerSync service
        db.connect()
    }
    
    // Reactive Query: Returns a Flow<List<Todo>> for Compose
    fun observeTodos(userId: String): Flow<List<Todo>> {
        return db.query(
            "SELECT id, task, is_completed, created_at FROM todos WHERE user_id = ? ORDER BY created_at DESC",
            listOf(userId)
        ).asFlow()
            .map { result ->
                result.map { row ->
                    Todo(
                        id = row.getString("id"),
                        task = row.getString("task"),
                        isCompleted = row.getInt("is_completed") != 0,
                        createdAt = row.getString("created_at")
                    )
                }
            }
    }
    
    // DML Operation: Inserting data (automatically syncs to Supabase)
    suspend fun addTodo(userId: String, task: String) {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis().toString()
        
        db.execute(
            sql = "INSERT INTO todos (id, user_id, task, is_completed, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            parameters = listOf(id, userId, task, 0, now, now)
        )
        // PowerSync detects the insert and queues it for upload
    }
    
    // Update Operation
    suspend fun updateTodo(id: String, isCompleted: Boolean) {
        val now = System.currentTimeMillis().toString()
        
        db.execute(
            sql = "UPDATE todos SET is_completed = ?, updated_at = ? WHERE id = ?",
            parameters = listOf(if (isCompleted) 1 else 0, now, id)
        )
    }
    
    // Watch sync status (for UI feedback)
    fun observeSyncStatus(): Flow<SyncStatus> {
        return db.watchStatus()
            .map { status ->
                SyncStatus(
                    isConnected = status.connected,
                    uploadQueueSize = status.uploadQueue.size,
                    dataFlowStatus = status.dataFlowStatus
                )
            }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Disconnect when ViewModel is cleared
        viewModelScope.launch {
            db.disconnect()
        }
    }
}

data class Todo(
    val id: String,
    val task: String,
    val isCompleted: Boolean,
    val createdAt: String
)

data class SyncStatus(
    val isConnected: Boolean,
    val uploadQueueSize: Int,
    val dataFlowStatus: String
)
```

**4. Use in Jetpack Compose UI:**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TodoScreen(repository: TodoRepository = viewModel()) {
    val userId = "current-user-id" // Get from auth
    
    // Observe todos as reactive state
    val todos = repository.observeTodos(userId).collectAsState(initial = emptyList()).value
    val syncStatus = repository.observeSyncStatus().collectAsState(initial = null).value
    
    Column {
        // Show sync status
        syncStatus?.let {
            Text(text = if (it.isConnected) "✓ Synced" else "⟳ Syncing")
            Text(text = "Queue: ${it.uploadQueueSize}")
        }
        
        // List todos
        LazyColumn {
            items(todos) { todo ->
                TodoItem(
                    todo = todo,
                    onToggle = { 
                        repository.updateTodo(todo.id, !todo.isCompleted)
                    }
                )
            }
        }
        
        // Add todo button
        Button(onClick = {
            repository.addTodo(userId, "New Task")
        }) {
            Text("Add Todo")
        }
    }
}

@Composable
fun TodoItem(todo: Todo, onToggle: () -> Unit) {
    // Render individual todo item
}
```

---

## Authentication Integration Patterns

### Pattern 1: Token Refresh on Expiry (Kotlin/Android)

Supabase sessions expire. You **must** refresh the JWT before PowerSync attempts to sync with an expired token. In Android, use Supabase's auth state listener to automatically refresh PowerSync connection:

```kotlin
import io.github.supabase.gotrue.SupabaseAuthClient
import io.github.supabase.gotrue.AuthState
import kotlinx.coroutines.flow.collectLatest

class AuthStateManager(
    private val supabaseAuth: SupabaseAuthClient,
    private val powerSyncDb: PowerSyncDatabase,
    private val connector: SupabasePowerSyncConnector
) {
    
    suspend fun observeAuthStateChanges() {
        supabaseAuth.authStateFlow.collectLatest { state ->
            when (state) {
                is AuthState.SignedIn -> {
                    // User signed in or token refreshed
                    // PowerSync will auto-fetch new credentials via connector
                    powerSyncDb.reconnect()
                }
                is AuthState.SignedOut -> {
                    // User signed out
                    powerSyncDb.disconnect()
                }
                else -> {
                    // Handle other states (loading, etc.)
                }
            }
        }
    }
}
```

The `PowerSyncBackendConnector.fetchCredentials()` is called automatically by the SDK when:
- The connection is initially established
- The JWT is near expiry (typically before 5 min)
- The app is backgrounded and resumes

**No manual token passing needed.** The connector pattern eliminates the need to re-call `db.connect()` manually.

---

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

**Bad:** Including large binary data in synced tables

```kotlin
// ❌ DO NOT sync large blobs
val schema = Schema(
    tables = listOf(
        Table("posts", listOf(
            Column("id", ColumnType.TEXT),
            Column("content", ColumnType.TEXT),
            Column("image_blob", ColumnType.BLOB)  // Large binary data
        ))
    )
)
```

**Good:** Store only metadata and URLs

```kotlin
// ✅ Use URLs instead
val schema = Schema(
    tables = listOf(
        Table("posts", listOf(
            Column("id", ColumnType.TEXT),
            Column("content", ColumnType.TEXT),
            Column("image_url", ColumnType.TEXT)  // URL only, fetch from Supabase Storage
        ))
    )
)
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

**Client-Side Query (Kotlin):**

```kotlin
val lists = db.execute(
    "SELECT * FROM lists WHERE user_id = ?",
    listOf(userId)
)
val todos = db.execute(
    "SELECT * FROM todos WHERE user_id = ?",
    listOf(userId)
)

// Join in memory
data class ListWithTodos(val list: List, val todos: List<Todo>)
val listsWithTodos = lists.map { list ->
    ListWithTodos(
        list = list,
        todos = todos.filter { t -> t.list_id == list.id }
    )
}
```

---

## Common Pitfalls & Solutions

### Pitfall 1: Using Supabase Client Instead of PowerSync for Queries

❌ **Wrong:**

```kotlin
// This will fail when offline - never query Supabase directly in offline-first app
val response = supabase.from("todos").select("*").execute()
```

✅ **Right:**

```kotlin
// Always query local SQLite via PowerSync
val result = db.execute(
    "SELECT * FROM todos WHERE user_id = ?",
    listOf(userId)
)
```

### Pitfall 2: Not Using PowerSync Connector for Token Management

The `PowerSyncBackendConnector` handles token refresh automatically. Do NOT pass tokens manually.

❌ **Wrong (Manual token passing):**

```kotlin
// This loses token refresh capability
val session = supabaseAuth.currentSession
db.connect(credentials = PowerSyncCredentials(token = session.accessToken))
```

✅ **Right:**

```kotlin
// Implement PowerSyncBackendConnector; SDK auto-refreshes tokens
class MyConnector(private val auth: SupabaseAuthClient) : PowerSyncBackendConnector {
    override suspend fun fetchCredentials(): PowerSyncCredentials {
        val session = auth.currentSession ?: throw IllegalStateException("Not auth")
        return PowerSyncCredentials(token = session.accessToken)
    }
}

db = PowerSyncDatabase(schema = schema, connector = MyConnector(auth))
db.connect() // SDK handles token refresh automatically
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

### Pitfall 6: Attempting DDL (CREATE TABLE) on Synced Tables in Kotlin

❌ **Wrong (Common Kotlin Mistake):**

```kotlin
// This WILL FAIL! Synced tables are SQLite Views, not real tables
db.execute("CREATE TABLE todos (id TEXT PRIMARY KEY, title TEXT)")
```

✅ **Right:**

```kotlin
// Only use DML: INSERT, UPDATE, DELETE, SELECT
db.execute("INSERT INTO todos (id, title) VALUES (?, ?)", listOf(id, title))
db.execute("UPDATE todos SET title = ? WHERE id = ?", listOf(newTitle, id))
db.execute("DELETE FROM todos WHERE id = ?", listOf(id))

// Synced table schema is defined once during Schema initialization
val schema = Schema(
    tables = listOf(
        Table(
            name = "todos",
            columns = listOf(
                Column("id", ColumnType.TEXT),
                Column("title", ColumnType.TEXT)
            )
        )
    )
)
```

### Pitfall 4: Ignoring Flow vs. Coroutine Scope

❌ **Wrong (Not collecting Flow in correct scope):**

```kotlin
// This leaks if Fragment/Activity is destroyed
val todos = repository.observeTodos(userId) // Not collected!
```

✅ **Right:**

```kotlin
// Collect Flow within lifecycle-aware scope
val todos = repository.observeTodos(userId)
    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
    .collectAsState(initial = emptyList())
```

Or in ViewModel:

```kotlin
val todos: Flow<List<Todo>> = repository.observeTodos(userId)
// Collect in Compose or lifecycle-aware observer
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

### Checking Sync Status (Kotlin/Android)

```kotlin
// Observe sync status as a Flow
viewModelScope.launch {
    repository.observeSyncStatus().collect { status ->
        println("Connected: ${status.isConnected}")
        println("Upload queue size: ${status.uploadQueueSize}")
        println("Data flow: ${status.dataFlowStatus}")
    }
}

// Or in Compose UI (reactive):
@Composable
fun SyncStatusBadge(repository: TodoRepository) {
    val syncStatus = repository.observeSyncStatus()
        .collectAsState(initial = SyncStatus(false, 0, "idle")).value
    
    Text(
        text = if (syncStatus.isConnected) "✓ Synced" else "⟳ Syncing",
        color = if (syncStatus.isConnected) Color.Green else Color.Orange
    )
}
```

### Common Error Scenarios

| Error                  | Cause                               | Solution                                                      |
| ---------------------- | ----------------------------------- | ------------------------------------------------------------- |
| `JWT invalid`          | Token expired or wrong secret       | Ensure JWT is fresh and signing secret matches Supabase config |
| `RLS policy violation` | User lacks permission in Supabase   | Check RLS policy in Supabase console; ensure JWT claims match |
| `Sync rule mismatch`   | User not allowed by Sync Rule       | Verify `request.auth.sub` in sync rule matches JWT sub claim  |
| `Schema mismatch`      | Local schema doesn't match Postgres | Ensure all synced columns are defined in PowerSync schema     |
| `Offline queue stuck`  | Upload fails repeatedly             | Check Postgres constraints; ensure RLS allows the write       |

---

## Supabase + PowerSync Version Compatibility

| Supabase | PowerSync Web | PowerSync RN | PowerSync Kotlin | Notes                                     |
| -------- | ------------- | ------------ | --------------- | ----------------------------------------- |
| v1.0+    | v1.0+         | v1.0+        | v1.12.0+        | Stable. JWKS validation is critical.      |
| —        | v1.5+         | v1.5+        | v1.12.0+        | Added support for custom claim filtering. |

**Kotlin-Specific Notes:**

- PowerSync Kotlin uses **Kotlin Multiplatform (KMP)**, targeting both Android and iOS/Desktop.
- Room ORM integration is in **Beta**; test thoroughly in production scenarios.
- SQLDelight integration is also **Beta**; standard `db.execute()` is production-ready.
- Android API 24+ recommended for SQLite compatibility.

**Key Breaking Changes:**

- Supabase rotates JWKS keys periodically. PowerSync Service must fetch keys dynamically, not cache them.
- Upgrading Postgres extensions (e.g., pgvector) can change index structures; re-test sync rules.
- PowerSync v1.12+ changed the `PowerSyncBackendConnector` interface; older custom connectors may not compile.

---

## Recommended Workflow for Implementation

### General Workflow (All Platforms)

1. **Define Database Schema** in Supabase (tables, columns, constraints, RLS policies)
2. **Mirror Schema** in PowerSync client (define exactly which tables/columns to sync)
3. **Define Sync Rules** in PowerSync Dashboard (specify user-data filtering logic)
4. **Implement Authentication** (Supabase Auth → JWT → PowerSync connect)
5. **Add Token Refresh** (hook into auth state changes to re-connect PowerSync)
6. **Test Offline** (simulate connection loss to verify local reads/queued writes work)
7. **Test Conflict Scenarios** (two clients writing same row, one goes offline, both retry)
8. **Monitor Sync Queue** (observe upload queue in development)

### Kotlin/Android-Specific Workflow

1. **Set Up Supabase SDK** in Gradle with `io.github.supabase:supabase-kt` and auth module
2. **Create PowerSync Schema** as a `Schema` object with all `Table` and `Column` definitions
3. **Implement `PowerSyncBackendConnector`** with `fetchCredentials()` method tied to Supabase auth
4. **Initialize Repository** with `PowerSyncDatabase`, `schema`, and `connector` in `init {}` block or factory
5. **Expose Flows for UI** using `db.query().asFlow()` for reactive Compose state
6. **Add Auth State Listener** to handle sign-in/sign-out/token refresh
7. **Test DML Operations** (INSERT/UPDATE/DELETE); verify sync queue fills and auto-uploads
8. **Wire Up UI** in Jetpack Compose using `.collectAsState()` for reactive rendering
9. **Handle Background Sync** if needed: integrate with `WorkManager` for periodic background sync (not built-in to SDK)
10. **Verify RLS Policies** match Sync Rules in the PowerSync Dashboard

**Common Integration Points:**

- **Supabase Auth** → `SupabaseAuthClient` from gotrue-kt
- **Local Queries** → `db.execute()` or `db.query()`, no direct SQLite access
- **UI Binding** → Use `Flow<List<Model>>` with `collectAsState()` in Compose
- **Lifecycle** → Disconnect in `ViewModel.onCleared()`; connect in `initialize()` or app startup

---

## References

- [Supabase Documentation](https://supabase.com/docs)
- [PowerSync Documentation](https://docs.powersync.com/intro/powersync-overview)
- [PowerSync Documentation, LLM Index](https://docs.powersync.com/llms.txt)
- [PowerSync + Supabase Integration Guide](https://docs.powersync.com/integrations/supabase/guide)
- [Postgres Row Level Security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [PowerSync Kotlin SDK (KMP)](https://github.com/powersync-ja/powersync-kotlin)
- [Supabase Kotlin Client](https://github.com/supabase-community/supabase-kt)
- [Jetpack Compose State Management](https://developer.android.com/jetpack/compose/state)
- [Android Lifecycle & ViewModels](https://developer.android.com/topic/architecture/data-layer/repository-pattern)
- [WorkManager for Background Tasks](https://developer.android.com/topic/libraries/architecture/workmanager)
