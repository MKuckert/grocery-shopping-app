# Plan: Housekeeping Batch — Theme, Sync, Audio, UI Refactoring & Backup Rules

## Objective

Modernize the app's infrastructure and UX: replace the foreground service with WorkManager, add Material You dynamic theming, refactor AudioFeedback to use enums, refactor ScannerProcessor with polymorphic commands, configure backup rules, and split oversized screen files.

## Requirements & Decisions

- **Frameworks:** Jetpack Compose (Material3), Hilt, PowerSync, Supabase, CameraX, ML Kit
- **Chosen Libraries:** `androidx.work:work-runtime-ktx` (WorkManager)
- **Dark Theme:** Dynamic colors via `dynamicDarkColorScheme()` / `dynamicLightColorScheme()` (minSdk 35, always available)
- **Sync Strategy:** Hybrid — real-time WebSocket sync while app is foregrounded (via `ProcessLifecycleOwner`), WorkManager periodic (15 min) for background catch-up. FGS removed entirely.
- **Barcode API Research:** Deferred to a future plan (Produktsuche.info unreachable, EAN-Search blocks bots, UPCItemDB limited EU coverage)
- **Error Handling Strategy:**
  - WorkManager: Retry with exponential backoff on network failures; `Result.retry()` on transient errors, `Result.failure()` on auth failures (user must re-login)
  - Theme: No failure modes (system API, always available on SDK 35+)
  - AudioFeedback: Graceful degradation — if sound fails to load, skip playback silently (non-critical UX)
  - ScannerProcessor: Each command handles its own errors and emits appropriate `ScanResult`

## Implementation Steps

> Status Markers: [ ] Open, [/] In Progress, [x] Completed

- [x] **Task 1: Add WorkManager dependency**
  - **Description:** Add `androidx.work:work-runtime-ktx` to `libs.versions.toml` and `build.gradle.kts`. Remove foreground service permission from manifest if no longer needed.
  - **Review Criteria:** Dependency resolves; app compiles without errors.

- [x] **Task 2: Implement foreground sync lifecycle manager**
  - **Description:** Create `SyncLifecycleManager` that observes `ProcessLifecycleOwner`. On `ON_START` (app foreground): call `database.connect(connector)` to establish real-time WebSocket sync. On `ON_STOP` (app background): call `database.disconnect()`. Register as `DefaultLifecycleObserver` and inject via Hilt `@Singleton`. This replaces the FGS's always-on connection with a foreground-only connection.
  - **Review Criteria:** Real-time sync active while app visible; connection dropped when backgrounded; no leaked connections. Auth state checked before connect (skip if not authenticated).

- [x] **Task 3: Implement background SyncWorker**
  - **Description:** Create `BackgroundSyncWorker : CoroutineWorker` with `@HiltWorker`. On `doWork()`: check auth state → if not authenticated, return `Result.failure()`. Otherwise call `database.connect(connector)`, wait for `hasSynced` status (with 30s timeout), then `database.disconnect()` and return `Result.success()`. On timeout or network error, return `Result.retry()`. Schedule as `PeriodicWorkRequest` every 15 min with `NetworkType.CONNECTED` constraint in `Application.onCreate()`.
  - **Review Criteria:** Worker completes finite sync burst; doesn't hold connection indefinitely; retries on transient failures; unit test covers success/retry/failure/timeout paths.

- [x] **Task 4: Remove SyncService**
  - **Description:** Delete `SyncService.kt`. Remove `<service>` declaration and `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` permissions from `AndroidManifest.xml`. Remove any startService/bindService calls. Move `database.disconnectAndClear()` logic to logout flow (already in auth handling). Remove notification channel if no longer needed.
  - **Review Criteria:** App compiles; no references to `SyncService` remain; sync works via `SyncLifecycleManager` (foreground) + `BackgroundSyncWorker` (background).

- [x] **Task 5: Implement Dynamic Theme**
  - **Description:** In `GroceryApp.kt` (or theme wrapper), detect system dark mode via `isSystemInDarkTheme()` and apply `dynamicDarkColorScheme(context)` or `dynamicLightColorScheme(context)`. Remove hardcoded `Theme.Material.Light` from `res/values/themes.xml` and `res/values-night/themes.xml`; use `Theme.Material3.DayNight.NoActionBar` or just let Compose handle it. Update splash screen theme if needed.
  - **Review Criteria:** App respects system dark/light toggle; colors change dynamically; no hardcoded light-only colors in Compose code.

- [x] **Task 6: Refactor AudioFeedback to use enum**
  - **Description:** Create `SoundEffect` enum with properties: `resourceId: Int`, `volume: Float`, `rate: Float`. Values: `SUCCESS(R.raw.beep_success, 1f, 1f)`, `FAILURE(R.raw.beep_failure, 0.7f, 0.8f)`. Refactor `AudioFeedback` to use a `Map<SoundEffect, Int>` for sound IDs and a `Set<SoundEffect>` for loaded state. Replace `playSuccess()`/`playFailure()` with `play(effect: SoundEffect)`. Update all call sites.
  - **Review Criteria:** Enum-based; no named fields for individual sounds; existing behavior preserved; unit test verifies `play()` dispatches correctly.

- [x] **Task 7: Refactor ScannerProcessor with polymorphic commands**
   - **Description:** Extract the `when(mode)` block into a sealed interface `ScanCommand` with implementations: `DecrementStockCommand`, `IncrementPendingStockCommand`. Each command has a `suspend fun execute(repository: GroceryRepository, productId: String)` method. `ScannerProcessor.processScan()` calls `mode.toCommand().execute(...)` instead of branching. Also extract the three top-level branches (restored, hit, miss) into private methods: `handleRestored()`, `handleHit()`, `handleMiss()`.
   - **Review Criteria:** No `when/is` on `ScannerMode` in `ScannerProcessor`; command pattern is testable independently; existing behavior preserved.

- [x] **Task 8: Split large screen files**
  - **Description:** Extract composables into separate files:
    - `DetailScreen.kt` → extract `ImagePreviewSection.kt`, `BarcodeSection.kt`, `StockSection.kt`
    - `ShoppingScreen.kt` → extract `ShoppingProductCard.kt`
    - `InventoryScreen.kt` → extract `InventoryProductRow.kt`
    Keep screen-level composable in original file; extracted components get their own files in same package.
  - **Review Criteria:** No file exceeds ~200 lines; extracted composables are `@Preview`-able; no behavioral changes.

- [x] **Task 9: Configure backup rules**
   - **Description:** Update `backup_rules.xml` to exclude: `sharedpref/` (contains auth tokens), `databases/` (PowerSync local DB, re-syncs on restore). Update `data_extraction_rules.xml` with matching `<exclude>` rules for cloud backup. Rationale: auth tokens are device-specific; local DB rebuilds from server.
   - **Review Criteria:** Both XML files have explicit exclude rules; no sensitive data backed up; app still functions after restore (re-auth + re-sync).

- [/] **Task 10: Add bottom padding for FAB overlap**
  - **Description:** In `ShoppingScreen` and `InventoryScreen`, add `contentPadding` to the `LazyColumn` with `PaddingValues(bottom = 80.dp)` to prevent last item from being obscured by the FAB.
  - **Review Criteria:** Last list item is fully visible when scrolled to bottom; FAB doesn't overlap content.

- [ ] **Task 11: Product delete as button**
  - **Description:** In `DetailScreen`, move the delete action from the `TopAppBar` `IconButton` to a prominent `OutlinedButton` or `TextButton` at the bottom of the detail content (with destructive styling: `MaterialTheme.colorScheme.error`). Remove the icon from the app bar.
  - **Review Criteria:** Delete button visible without accessing overflow/appbar; styled as destructive action; confirmation dialog still works.

## Edge Case & Safety Checklist

- WorkManager: App killed during sync → Worker restarts automatically (built-in)
- WorkManager: No network at periodic trigger → Constraint prevents execution; retries when connected
- WorkManager: User logs out → Cancel all sync work via `WorkManager.cancelAllWorkByTag()`; `SyncLifecycleManager` checks auth before connect
- WorkManager: Background worker timeout → 30s timeout on `hasSynced`, returns `Result.retry()`
- Foreground sync: Rapid foreground/background toggling → debounce connect/disconnect (e.g. 2s delay before disconnect)
- Foreground sync: Auth expires while connected → observe auth state flow, disconnect on logout
- Theme: No edge cases on SDK 35+ (dynamic colors always available)
- AudioFeedback: SoundPool load failure → `play()` is a no-op (check loaded set)
- AudioFeedback: Rapid successive plays → SoundPool handles via `maxStreams(3)`
- ScannerProcessor: Race condition if two scans fire simultaneously → existing `Mutex` or sequential processing in coroutine scope
- Backup restore: User gets fresh login screen (tokens excluded); local DB rebuilds via sync
- FAB padding: Landscape orientation → padding still applies (acceptable)
- File splitting: No logic changes → purely structural refactoring

## Review Log (Plan Review)

- **Round 1:** NOT APPROVED — Issues below must be addressed.

### Reviewer Feedback (Round 1)

**CRITICAL — Real-time sync loss not acknowledged (Tasks 2–4):**

The current `SyncService` maintains a **persistent WebSocket** via `database.connect(connector)`. It never disconnects while the user is authenticated — this gives real-time bidirectional sync. The plan proposes replacing this with periodic 15-min batch sync + one-shot on reconnect. This is a **fundamental behavioral downgrade**: other household members' changes won't appear for up to 15 minutes.

Options (pick one and document the decision):
1. Accept the latency trade-off and state it explicitly in the plan.
2. Keep a persistent connection while the app is in foreground (via `ProcessLifecycleOwner`) and only use WorkManager for background catch-up sync.
3. Use WorkManager expedited work + observe connectivity to approximate real-time.

Option 2 is the pragmatic choice for a grocery app. The FGS was overkill, but killing real-time sync entirely is a regression.

**MINOR — Task 2 "monitors sync status and disconnects on completion":**

PowerSync's `connect()` doesn't have a "completion" — it's a persistent stream. If you go with batch sync, you need to define what "done" means: e.g., wait until `SyncStatusData.downloading == false && SyncStatusData.uploading == false` after initial connection, then disconnect. Specify this condition explicitly.

**MINOR — Task 3 scheduling:**

The plan mentions both "register a NetworkCallback" and "use WorkManager's built-in constraint." Pick one. WorkManager constraints are sufficient; a manual `NetworkCallback` is redundant complexity.

**MINOR — Task 4 "Cancel all sync work" on logout:**

Good, but also add: call `database.disconnectAndClear()` on logout (currently done in `SyncService`). Where does this responsibility move to? Likely `AuthViewModel` or a logout use case. State this.

**MINOR — Task 9 backup excludes:**

`sharedpref/` exclusion is correct but verify the actual file names. Supabase auth tokens may be in a specific shared prefs file (e.g., `supabase_session.xml`). A blanket `sharedpref/` exclude is fine if you have no user-preference prefs worth backing up — confirm this is intentional.

- **Round 1:** Reviewer flagged FGS→WorkManager as real-time→batch downgrade. Resolved with hybrid approach: `ProcessLifecycleOwner`-based live sync in foreground + periodic WorkManager for background catch-up. Added timeout, debounce, and auth-expiry edge cases.
- **Round 2:** APPROVED — All Round 1 issues resolved. Hybrid sync approach is technically sound; tasks are complete with actionable review criteria; edge cases adequately covered.
- **Round 3:** [N/A]

## Final Status (Code Review)

- **Round 1:** Task 1 APPROVED. Dependencies correct: WorkManager 2.10.1, hilt-work/compiler 1.2.0, proper `ksp` for annotation processor. Build compiles.
- **Round 2:** Task 2 APPROVED. Clean lifecycle management: debounce prevents rapid toggle thrashing, auth gating prevents unauthenticated connects, PowerSync connector handles token refresh internally. No leaks possible.
- **Round 3:** Task 3 NOT APPROVED — Missing timeout test. Criteria require coverage of success/retry/failure/timeout paths. Current tests cover success, failure (not-auth), and retry (network error), but no test verifies `TimeoutCancellationException` → `Result.retry()`. Add a test with a `statusFlow` that never emits idle status to trigger the 30s timeout path.
- **Round 4:** Task 3 APPROVED. Timeout test added (never-emitting `MutableSharedFlow`). All four paths covered. Worker implementation is correct: finite burst via `withTimeout`, guaranteed disconnect in `finally`, proper auth gating.
- **Round 5:** Task 4 NOT APPROVED — `SyncService` removal is complete (file deleted, manifest clean, no dangling references, `disconnectAndClear` in logout). However, `AuthViewModel.signOut()` does not cancel the periodic `BackgroundSyncWorker` via `WorkManager.cancelAllWorkByTag()` as required by the edge case checklist (line 76). The worker will keep firing post-logout. Fix: inject `WorkManager` into `AuthViewModel` and cancel by tag in `signOut()`. Consider re-enqueueing on login to ensure background sync resumes after re-authentication.
- **Round 6:** Task 4 APPROVED. `WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)` correctly called in `AuthViewModel.signOut()` before `connector.signOut()` and `database.disconnectAndClear()`. `@ApplicationContext` injected via Hilt. No `SyncService` references remain. All review criteria met.
- **Round 7:** Task 5 APPROVED. Implementation is correct and complete. isSystemInDarkTheme() imported from correct package (androidx.compose.foundation). context obtained via LocalContext.current before usage — valid. minSdk = 35 guarantees dynamic colors are always available — no fallback needed. XML themes use Theme.Material3.DayNight.NoActionBar — correct. Zero hardcoded colors found in Compose UI code
- **Round 8:** Task 6 APPROVED. `SoundEffect` enum correctly defines `resourceId`, `volume`, `rate`. `AudioFeedback` uses `Map<SoundEffect, Int>` / `Set<SoundEffect>` — no named fields for individual sounds. All call sites updated. `AudioFeedbackTest` verifies dispatch for both effects, no-op on unloaded, and load-error handling. Behavior preserved.
- **Round 9:** Task 7 APPROVED. ScanCommand sealed interface cleanly encapsulates the two repository mutations as data object commands — minimal, testable, no state leakage. toCommand() extension isolates the ScannerMode → ScanCommand mapping in a single location outside the processor. processScan() is now a clean three-branch when on product state (restored/hit/miss), with mode-specific logic delegated entirely to the command. 4 new unit tests independently verify command execution and mode-to-command conversion. No regressions: existing 14 ScannerProcessorTest tests remain valid since behavior is preserved.
- **Round 10:** Task 8 NOT APPROVED. (1) `DetailScreen.kt` is 363 lines — `GroupDropdown` and `CreateGroupDialog` must be extracted to a separate file. (2) `ShoppingProductCard.kt` has no `@Preview` for `ShoppingRow` or `SearchResultCard`. (3) `InventoryProductRow.kt` has no `@Preview` for `ProductRow`. All three must be fixed.
- **Round 11:** Task 8 APPROVED. All extracted composables are in separate files with @Preview functions, no file is unreasonably large, and no behavioral changes were introduced.
- **Round 12:** Task 9 APPROVED. Both `backup_rules.xml` and `data_extraction_rules.xml` correctly exclude `sharedpref` and `database` domains. Files are well-formed XML, properly referenced in manifest via `android:fullBackupContent` and `android:dataExtractionRules`. Post-restore UX is correct: re-auth + re-sync.
