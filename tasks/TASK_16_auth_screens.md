# Task 16: Auth Screens (Minimal Updates)

> **Depends on:** Task 6 (Navigation)
> **Status:** [ ]

---

## Description

The existing `SignInScreen.kt` and `SignUpScreen.kt` are functionally correct but reference the old `NavController`. Update them to use the new `NavHostController` from Task 6. No visual redesign required.

After successful sign-in, the `householdState` flow in `GroceryApp` automatically routes to `InventoryScreen` — the auth screens must NOT call `navController.navigate(...)` on success.

### `households` row creation on first sign-up

This is a backend concern (Supabase Postgres trigger on `auth.users` insert). The builder must verify — or document for the backend team — that the following trigger exists:

```sql
CREATE OR REPLACE FUNCTION create_household_for_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.households (id, current_state)
  VALUES ('00000000-0000-0000-0000-000000000001', 'IDLE')
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE create_household_for_new_user();
```

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
