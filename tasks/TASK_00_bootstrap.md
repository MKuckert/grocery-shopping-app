# Task 0: Application Bootstrap & DI Initialization Order

> **Depends on:** nothing (foundation task)
> **Status:** [ ]

---

## Description

Establish the correct initialization order so all Hilt-injected singletons are available before any UI composable or ViewModel accesses them.

**`GroceryApplication.kt`** — already annotated `@HiltAndroidApp`. No changes needed to the class itself; Hilt generates the component at compile time. The singletons (`SupabaseConnector`, `PowerSyncDatabase`, `GroceryRepository`) are created lazily by Hilt on first injection request.

**`di/SyncModule.kt`** initialization contract (builder must follow this order of concern):
1. `SupabaseConnector` is a pure constructor call (no async, no IO). Safe as `@Singleton`.
2. `PowerSyncDatabase` creates the SQLite file on disk synchronously (no network). Safe as `@Singleton`. Uses `DatabaseDriverFactory(context)`.
3. `GroceryRepository` depends on `PowerSyncDatabase` and the `householdId` string. `householdId` is read from the live Supabase session (lazy, not at creation time).

**`household_id` provision strategy** — The `household_id` must NOT be a `@Singleton @Provides` that reads from the session at app start (the user may not be logged in yet). Instead:
- Do NOT provide `householdId` as a Hilt binding.
- Instead, expose it from the `AuthViewModel` as a `StateFlow<String?>` derived from `connector.sessionStatus`.
- ViewModels that need `householdId` inject `AuthViewModel` or receive it as a constructor parameter via assisted injection.
- Repository functions that require `householdId` accept it as a parameter (already reflected in Task 4 signatures).

**PowerSync connection lifecycle:**
- `SyncService` (already exists) handles `database.connect(connector)` when session is `Authenticated` and `database.disconnectAndClear()` on sign-out. No changes needed here.
- `GroceryApp` composable must NOT call `database.connect()` directly — that is the service's responsibility.

**`GroceryApplication.kt` minimal change:** `AudioFeedback.release()` must be called when the Application is destroyed. Because `Application.onTerminate()` is emulator-only and never fires on production devices, use a `DisposableEffect` at the composable root instead. The canonical approach (specified in Task 13): inject `AudioFeedback` into a composable and call `release()` inside a `DisposableEffect(Unit) { onDispose { audioFeedback.release() } }` scoped to the root `GroceryApp` composable.

---

## Review Criteria

- No `database.connect()` call outside `SyncService`.
- `householdId` is never a Hilt `@Singleton` binding that reads session eagerly.
- `AudioFeedback.soundPool` released via `DisposableEffect` in `GroceryApp` composable root (not via `Application.onTerminate()`).
- App builds and `SyncService` starts correctly on sign-in.
