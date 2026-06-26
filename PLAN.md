# Plan: Grocery Shopping App — Shared Reference

## Objective

Transform the current skeleton PowerSync/Supabase Android app (a todo-list demo) into a full-featured household grocery management app. The finished app must implement the three-state machine (IDLE / SHOPPING / UNLOADING), four screens (InventoryScreen, ShoppingScreen, UnloadingScreen, DetailScreen), barcode scanning with Open Food Facts fallback, animated reactive lists, and full offline-first sync — exactly as specified in `docs/UI.md` and `docs/DATABASE.md`.

---

## Requirements & Decisions

- **Frameworks:** Jetpack Compose (Material 3), Hilt DI, PowerSync 1.13.0 + Supabase 3.6.0, CameraX + ML Kit Barcode Scanning, `navigation-compose`. All already present in `libs.versions.toml`.
- **Chosen Libraries:**
  - `androidx.navigation:navigation-compose` — already declared; use for typed-route NavHost replacing the manual `NavController` / `Screen` sealed class.
  - `powersync-compose` — already declared; use `db.watch(sql)` for reactive streams.
  - Retrofit + OkHttp **not** needed — use `io.ktor:ktor-client-android` (already transitive via Supabase-kt) for the Open Food Facts HTTP call.
  - **`androidx.camera:camera-camera2`** — must be added. The existing camerax bundle (`camera-core`, `camera-lifecycle`, `camera-view`) lacks the Camera2 implementation module; `ProcessCameraProvider.getInstance()` will fail at runtime without it. Add to `libs.versions.toml`:
    ```toml
    androidx-camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "cameraX" }
    ```
    Add to the `camerax` bundle in `libs.versions.toml` and to `build.gradle.kts`.
  - No other new dependencies required beyond those already declared or listed here.
- **Supabase package:** `io.github.jan.supabase` (NOT `io.github.jan_tennert.supabase`).

---

## Error Handling Strategy

- **DB writes** fail loudly: surface error via `Snackbar`; never silently swallow.
- **PowerSync sync errors** are surfaced via the existing `SyncStatusData` banner (already in `SyncService`).
- **Open Food Facts API timeout / offline:** fall back to title = `"Unknown Item"` and block completion until photo captured (per spec §4).
- **Open Food Facts 503 rate limit:** single exponential-backoff retry (2 s), then treat as offline miss.
- **Barcode not found locally:** play failure boop, switch scanner viewport to `CaptureRequired` state; app remains live (no crash).
- **Stock decrement below 0:** guarded at the call site (`WHERE current_stock > 0`); DB constraint is a safety net, not the primary check.
- **Unload submit with open rows:** `AlertDialog` warning; user must explicitly confirm.
- **Soft-deleted item scanned:** nullify `deleted_at`, resurface with Snackbar "Restored …".

---

## Implementation Steps

> Status Markers: [ ] Open, [/] In Progress, [x] Completed

| Status | File                                 | Task                                            |
| ------ | ------------------------------------ | ----------------------------------------------- |
| [x]    | `tasks/TASK_00_bootstrap.md`         | Application Bootstrap & DI Initialization Order |
| [/]    | `tasks/TASK_01_delete_demo.md`       | Delete Demo Code & Establish Package Skeleton   |
| [ ]    | `tasks/TASK_02_schema.md`            | Update PowerSync Schema                         |
| [ ]    | `tasks/TASK_03_domain_models.md`     | Domain Models                                   |
| [ ]    | `tasks/TASK_04_repository.md`        | Repository Layer                                |
| [ ]    | `tasks/TASK_05_hilt_di.md`           | Hilt DI Wiring                                  |
| [ ]    | `tasks/TASK_06_navigation.md`        | Root Navigation (NavHost)                       |
| [ ]    | `tasks/TASK_07_inventory_screen.md`  | InventoryScreen                                 |
| [ ]    | `tasks/TASK_08_shopping_screen.md`   | ShoppingScreen                                  |
| [ ]    | `tasks/TASK_09_unloading_screen.md`  | UnloadingScreen                                 |
| [ ]    | `tasks/TASK_10_detail_screen.md`     | DetailScreen                                    |
| [ ]    | `tasks/TASK_11_scanner.md`           | Barcode Scanner Infrastructure                  |
| [ ]    | `tasks/TASK_12_openfoodfacts.md`     | Open Food Facts Integration                     |
| [ ]    | `tasks/TASK_13_audio.md`             | Audio Feedback                                  |
| [ ]    | `tasks/TASK_14_convergence.md`       | Automated Convergence Engine                    |
| [ ]    | `tasks/TASK_15_camera_permission.md` | Camera Permission Handling                      |
| [ ]    | `tasks/TASK_16_auth_screens.md`      | Auth Screens (Minimal Updates)                  |
| [ ]    | `tasks/TASK_17_qa.md`                | End-to-End Integration & Manual QA              |

---

## Edge Case & Safety Checklist

- **Empty product group:** The `watchProductsWithGroups` JOIN query starts from `product_kinds`, so groups with zero active products produce zero rows and no map key — they do **not** appear as headers in the InventoryScreen. This is the accepted v1 behavior: empty groups are invisible in the inventory list. They remain selectable in the Detail Screen group picker via `watchProductGroups()`. Documenting as known v1 limitation; no "phantom header" rendering is required.
- **Stock at zero on swipe/scan:** `WHERE current_stock > 0` guard prevents negative stock; DB constraint is a safety net.
- **`pending_stock` at zero on `[-]` stepper in Shopping/Unloading:** guarded at call site; `MAX(0, pending_stock - 1)`.
- **`submitUnloading` with all items checked:** no dialog shown; proceeds immediately.
- **`submitUnloading` with zero pending items:** no-op (transaction UPDATE affects 0 rows); household state set to `IDLE`.
- **Open Food Facts call during airplane mode:** 10 s timeout; caught; falls through to `OFResult.Miss`; user sees `CaptureRequired` overlay.
- **Open Food Facts 503 retry loop:** single retry; not a recursive retry that could loop.
- **Barcode scan of a product with `deleted_at != null`:** resurrection path — `deleted_at = NULL`, `minimum_stock` restored to 1, `recalculateQuantityToBuy` called.
- **Simultaneous scan of same barcode on two devices:** last-write-wins via PowerSync; stock decrement is idempotent directionally (can decrement below user's intent if both fire simultaneously) — acceptable per spec (no custom conflict logic required).
- **`ensureUnsortedGroup` race condition (two devices creating Unsorted at same time):** Supabase will accept both INSERTs with different UUIDs. Workaround: use `SELECT` first (if synced, no INSERT needed); if both insert, two "Unsorted" groups appear. Not critical for v1; document as known limitation.
- **Product with no group assigned (`group_id = NULL`):** Inventory screen must have a catch-all section (e.g., "Unsorted") for products where `group_id IS NULL` or references a deleted group.
- **Camera permission denied:** show a `RationaleDialog` on first denial; on permanent denial, redirect to app settings.
- **`household_id` missing from JWT:** `AuthViewModel.householdId` emits `null`; InventoryViewModel/ShoppingViewModel receive `null` and emit empty lists; UI shows empty state with a Snackbar warning. Does not crash (lazy evaluation per Task 0 design).
- **Background sync:** The existing `SyncService` is a foreground service and runs while the app is in the foreground/background until explicitly stopped. PowerSync SDK does not auto-restart sync after the service is killed; WorkManager integration for deep-background sync is out of scope for v1 (document as known limitation). For household use, foreground sync is sufficient.
- **Navigation route format:** Uses string-based sealed class `Route` (Task 6). The `@Serializable` type-safe nav API requires Compose Navigation 2.8+; verify the version in `libs.versions.toml` before using type-safe routes. If `navigation-compose` is below 2.8, use string routes as specified.
- **DetailScreen opened during `UNLOADING`:** fully editable (per spec §3.4 Absolute Mutability Policy).
- **`AuthViewModel` re-routes after detail navigation:** `LaunchedEffect` for `householdState` uses `popUpTo(0)` to clear back stack — ensures DetailScreen is dismissed when state changes.
- **Coil image loading for `image_path`:** local `file://` URIs require `FileProvider`-based URI — use `FileProvider.getUriForFile` before passing to `AsyncImage`; grant read permission.

---

## Review Log (Plan Review)

- **Round 1:** REJECTED — 6 critical blockers + 4 high-priority issues identified by PlanReviewer. Key gaps: missing Task 0 (bootstrap order), incomplete Ktor HttpClient setup (Task 12), missing Coil version + FileProvider URI (Task 10), incomplete SQL (Task 4), missing camera permission task, missing SoundPool load guard (Task 13). High-priority: vague photo capture flow (Task 11), missing households trigger documentation (Task 15/16), vague index API (Task 2), background sync undocumented.
- **Round 2:** All 6 critical blockers addressed. Round 2 PlanReviewer flagged: (1) PowerSync API `watchFlow` vs `watch` — resolved: all reactive queries now use `db.watch(sql, params) { cursor -> ... }`; (2) SQLite `MAX(0, expr)` validity — confirmed valid in SQLite (multi-arg `max()` is a documented scalar function, not aggregate); clarifying note added to plan; (3) `watchGroupsWithProducts` in-memory merge violates pure stream requirement — resolved: replaced with single denormalized `LEFT JOIN` query (`watchProductsWithGroups`) + pure `Flow.map` transformation in ViewModel. Also: Ktor version pinned to 3.1.3 (Supabase-kt 3.x transitive); `filepaths.xml` verification and `<cache-path>` entry specification added to Task 11.
- **Round 3:** REJECTED (10 issues: 4 critical, 4 high, 2 low). All resolved: (1) `GroceryApp` now uses `AppViewModel` (`@HiltViewModel`) instead of bare `repository` — compiles correctly; (2) `CameraPermissionHandler` fixed with `rationaleShown` state variable — `launchPermissionRequest` now fires on first use; (3) `camera-camera2` added as explicit dependency; (4) `filepaths.xml` spec updated to preserve existing `<external-files-path>` and add both `<files-path>` and `<cache-path>`; (5) `findByBarcode` corrected to `db.get()`; (6) obsolete N+1 `watchGroupsWithProducts` description struck-through with explicit "do not implement" note; (7) `AuthViewModel` refactor to `@HiltViewModel` now explicitly specified in Task 5; (8) `setHouseholdState` SQL updated to stamp `shopping_started_at`; (9) `onTerminate()` removed from Task 0 review criteria; (10) 5-second null-household Snackbar given explicit `LaunchedEffect` + `delay` implementation.
- **Round 4:** REJECTED (9 issues: 2 critical, 4 high, 3 low). All resolved: (1) `hilt-navigation-compose` added as explicit required dependency — `hiltViewModel()` now resolvable; (2) sign-out routing added to `GroceryApp` via `LaunchedEffect(authState)` → `navigate(Route.SignIn.path) { popUpTo(0) }`; (3) `lifecycle-runtime-compose` added as explicit required dependency — `collectAsStateWithLifecycle()` now resolvable; (4) screen ViewModel `householdId` DI pattern corrected — `SupabaseConnector` injected directly, no cross-`@HiltViewModel` injection; (5) Task 5 `AuthViewModel` snippet corrected to `connector.sessionStatus.map { ... }`; (6) empty-group edge case requirement removed and replaced with explicit v1 known-limitation note consistent with the LEFT JOIN SQL; (7) `provideOpenFoodFactsHttpClient` typo fixed; (8) stale `db.watchFlow` section header in Task 4 corrected to `db.watch()`; (9) `fromCursor(Map<String,Any?>)` in Task 3 replaced with index-based `SqlCursor` API reference.

## Final Status (Code Review)

- **Round 1:** N/A
- **Round 2:** N/A
- **Round 3:** N/A
