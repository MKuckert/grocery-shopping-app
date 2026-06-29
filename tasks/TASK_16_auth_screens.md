# Task 16: Auth Screens (Minimal Updates)

> **Depends on:** Task 6 (Navigation)
> **Status:** [ ]

---

## Description

The existing `SignInScreen.kt` is functionally correct but reference the old `NavController`. Update them to use the new `NavHostController` from Task 6. No visual redesign required.

After successful sign-in, the `householdState` flow in `GroceryApp` automatically routes to `InventoryScreen` — the auth screens must NOT call `navController.navigate(...)` on success.

### Null-household guard in `GroceryApp`

If the trigger is not present, `watchHousehold()` returns `null` and the user gets stuck on the SignIn screen. Implement this guard:

```kotlin
val authState by authViewModel.authState.collectAsStateWithLifecycle()
LaunchedEffect(authState, householdState) {
  if (authState == AuthState.SignedIn && householdState == null) {
    delay(5_000)
    if (householdState == null) {
      snackbarHostState.showSnackbar("Setup incomplete: contact support")
    }
  }
}
```

`SnackbarHostState` is provided via `remember { SnackbarHostState() }` in `GroceryApp` and passed to the `Scaffold`'s `snackbarHost` slot.

---

## Review Criteria

- No references to the old `NavController` / `Screen` sealed class.
- Successful auth does not programmatically call `navController.navigate`; routing is entirely driven by the `householdState` `LaunchedEffect` in `GroceryApp`.
- Snackbar shown when household state is null after 5 s post-login.
