# Task 5: Hilt DI Wiring

> **Depends on:** Task 3, Task 4
> **Status:** [ ]

---

## Description

### `di/SyncModule.kt` updates
- Remove `listsTable` / `todosTable` imports (schema now clean).
- Add a `@Singleton @Provides` for `GroceryRepository` that binds `GroceryRepositoryImpl`.

### `di/AppModule.kt` (new)
Create with `@Provides` for:
- `OpenFoodFactsClient` singleton (see Task 12 for exact `HttpClient` config).
- `AudioFeedback` singleton (see Task 13).
- A dedicated `@Named("offs")` `HttpClient` singleton for Open Food Facts (see Task 12).

### `household_id` is NOT a Hilt binding
It is read lazily from `SupabaseConnector.sessionStatus` in `AuthViewModel` and passed as a parameter to repository functions. This avoids the session-not-ready-at-app-start problem.

### `AuthViewModel` refactor
The existing `AuthViewModel` is manually constructed (`internal class AuthViewModel(supabase, navController)`) and not Hilt-aware. It must be refactored:
- Add `@HiltViewModel` annotation and `@Inject constructor`.
- Remove the `navController` constructor parameter (navigation is now driven by `householdState` in `GroceryApp`, not by `AuthViewModel`).
- Remove all `navController.navigate(...)` calls from `AuthViewModel`; retain only auth-state tracking (`_authState`, `_userId`).
- `signIn` / `signUp` / `signOut` remain `suspend` functions called from the SignIn/SignUp screen ViewModels.

`AuthViewModel` must additionally expose:

```kotlin
val householdId: StateFlow<String?> = sessionStatus
  .map { status ->
    when (status) {
      is SessionStatus.Authenticated ->
        status.session.user?.appMetadata?.get("household_id")
          ?.jsonPrimitive?.contentOrNull
      else -> null
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

This `householdId` is then passed down to screen-level ViewModels via constructor or assisted injection.

---

## Review Criteria

- `GroceryRepository` is injectable anywhere.
- `householdId` string is never a Hilt `@Singleton` binding that reads the session eagerly.
- `AuthViewModel` is `@HiltViewModel`; no `navController` constructor parameter remains.
- No circular dependencies.
