---
name: jetpack-compose-ui-essentials
description: Use when building or reviewing Jetpack Compose Android UI code. Provides essential patterns for state management, performance optimization, accessibility, and architectural best practices to prevent common LLM mistakes.
metadata:
  created: 2026-06-21
  libraries: Android API 21+, Jetpack Compose 1.5.0+
  tags: android, jetpack-compose, ui
  verified: false
---

# Jetpack Compose UI Essentials & Best Practices

## Overview

This skill guides the generation of production-ready Jetpack Compose code. Jetpack Compose is a **declarative UI framework** where components describe _what_ the UI should look like for a given state, rather than imperative mutations of Views. The framework automatically recomposes (reruns) component functions when their state changes.

**Critical:** Most LLM failures in Compose code stem from misunderstanding the three execution phases (Composition, Layout, Drawing) and state lifecycle. This skill attempts to prevent those mistakes.

---

## Architectural Foundation: The Three Phases

Jetpack Compose processes UI updates in three distinct phases:

1. **Composition Phase:** Your `@Composable` functions execute. The framework determines _what_ to draw.
2. **Layout Phase:** The framework measures and positions elements. Parent-to-child dimensions flow down; measurements flow up.
3. **Drawing Phase:** The framework renders pixels.

**Key Implication:** A state read or calculation in the Composition phase triggers a full recomposition. A state read in the Layout phase does not. This distinction is critical for performance.

---

## State Management: Foundation Patterns

### Rule 1: Use `mutableStateOf` for Local Ephemeral State

Ephemeral state (e.g., "is this dialog open?") belongs in the Composable:

```kotlin
@Composable
fun MyDialog() {
    var isOpen by remember { mutableStateOf(false) }

    Button(onClick = { isOpen = true }) { Text("Open") }

    if (isOpen) {
        Dialog(onDismissRequest = { isOpen = false }) {
            Text("Dialog content")
        }
    }
}
```

**Why `remember { ... }`:** Without it, `isOpen` would be reset to `false` on every recomposition. `remember` caches the value across recompositions.

### Rule 2: Business State Lives in ViewModel + StateFlow

Any state that survives configuration changes, process death, or represents business logic must live in a ViewModel and expose `StateFlow`:

```kotlin
class MyViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _userName = savedStateHandle.getStateFlow("user_name", "")
    val userName: StateFlow<String> = _userName

    fun updateUserName(name: String) {
        savedStateHandle["user_name"] = name
    }
}

@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    // ✅ BEST PRACTICE: Use collectAsStateWithLifecycle()
    // This stops collecting when the app is backgrounded.
    val name by viewModel.userName.collectAsStateWithLifecycle()

    Column {
        Text(text = "Hello, $name")
        Button(onClick = { viewModel.updateUserName("Alice") }) {
            Text("Change")
        }
    }
}
```

**Why `SavedStateHandle`:** Survives process death (e.g. low-memory kill) automatically.

**Why `collectAsStateWithLifecycle()`:** Respects the Compose lifecycle. Prevents unnecessary collection when the app is in the background.

### Rule 3: State Hoisting for Reusability

Separate _logic_ from _presentation_ by extracting state as parameters. This makes components testable and reusable:

```kotlin
// ✅ STATELESS (reusable, testable)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text("Search") }
    )
}

// ✅ STATEFUL (owns the state, calls stateless)
@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    SearchBar(
        query = query,
        onQueryChange = { viewModel.updateQuery(it) }
    )
}
```

**Pattern:** _Events up, State down._ Child components receive state as parameters and communicate changes via lambdas.

---

## Performance Optimization: Critical Techniques

### Technique 1: Lambda Modifiers (Defer State Reads)

Reading state in the Composition phase forces recomposition. Reading in the Layout phase does not.

```kotlin
// ❌ BAD: Reads scrollState in COMPOSITION phase → full recomposition
@Composable
fun ParallaxHeader(scrollState: LazyListState) {
    Image(
        painter = painterResource(R.drawable.header),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .offset(0.dp, (scrollState.firstVisibleItemScrollOffset / 2).dp)
    )
}

// ✅ GOOD: Reads scrollState in LAYOUT phase → no recomposition
@Composable
fun ParallaxHeader(scrollState: LazyListState) {
    Image(
        painter = painterResource(R.drawable.header),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .offset {
                IntOffset(0, scrollState.firstVisibleItemScrollOffset / 2)
            }
    )
}
```

**Pattern:** Use the lambda form of `offset`, `clip`, `drawBehind`, etc., when the value depends on frequently-changing state.

### Technique 2: `derivedStateOf` for High-Frequency State

When you need to compute a derived value from a frequently-changing state, use `derivedStateOf` to prevent unnecessary recompositions:

```kotlin
@Composable
fun ScrollList(scrollState: LazyListState) {
    // ❌ BAD: The expression re-evaluates and recomposes on every scroll pixel
    val isScrolled = scrollState.firstVisibleItemScrollOffset > 0

    // ✅ GOOD: Only recomposes when the boolean result changes
    val isScrolled by remember {
        derivedStateOf { scrollState.firstVisibleItemScrollOffset > 0 }
    }

    Column {
        if (isScrolled) {
            Text("You are scrolled!")
        }
        LazyColumn(state = scrollState) {
            items(100) { index ->
                Text("Item $index")
            }
        }
    }
}
```

### Technique 3: Mark Stable Data Classes

Use `@Stable` or `@Immutable` annotations on data classes that don't change to help the Compose compiler skip recomposition:

```kotlin
@Immutable
data class User(
    val id: Long,
    val name: String,
    val email: String
)

@Composable
fun UserCard(user: User) { /* ... */ }
```

The compiler now knows that if the `User` instance is the same object reference, the `UserCard` will not need to recompose.

---

## Accessibility: Non-Negotiable Requirements

### Rule 1: Every Meaningful Image Must Have `contentDescription`

```kotlin
// ❌ BAD: No description for TalkBack
Image(painter = painterResource(R.drawable.icon), contentDescription = null)

// ✅ GOOD: Provides context for users with screen readers
Image(
    painter = painterResource(R.drawable.icon),
    contentDescription = "Home icon"
)

// ✅ GOOD: Decorative images use null (explicitly ignore them)
Image(
    painter = painterResource(R.drawable.decorative_bg),
    contentDescription = null,
    modifier = Modifier.alpha(0.5f)
)
```

### Rule 2: Use `semantics` for Complex Interactions

```kotlin
@Composable
fun SwipeCard(text: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .semantics {
                // Declare this as a dismissible item
                dismissDialog()
                // Announce the action to accessibility services
                customLabel = "Dismiss $text"
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 100) onDismiss()
                }
            }
    ) {
        Text(text)
    }
}
```

---

## Architecture: Unidirectional Data Flow (UDF)

The industry-standard pattern is **Unidirectional Data Flow** with ViewModel and StateFlow:

```
ViewModel (source of truth)
    ↓
StateFlow (observable state)
    ↓
@Composable (UI layer, receives state)
    ↓
User interaction (click, text change, etc.)
    ↓
ViewModel method (update source of truth)
    ↓
Cycle repeats
```

**Example: Complete UDF Stack**

```kotlin
// 1. ViewModel owns the state
class ProfileViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _profileState = savedStateHandle.getStateFlow("profile", ProfileState())
    val profileState: StateFlow<ProfileState> = _profileState

    fun updateBio(bio: String) {
        val current = _profileState.value
        savedStateHandle["profile"] = current.copy(bio = bio)
    }
}

// 2. Data class for the state
@Immutable
data class ProfileState(
    val name: String = "",
    val bio: String = ""
)

// 3. Stateless composable (highly reusable)
@Composable
fun ProfileForm(
    state: ProfileState,
    onBioChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(text = "Name: ${state.name}")
        TextField(
            value = state.bio,
            onValueChange = onBioChange,
            label = { Text("Bio") }
        )
    }
}

// 4. Stateful composable (integrates ViewModel)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel()) {
    val state by viewModel.profileState.collectAsStateWithLifecycle()

    ProfileForm(
        state = state,
        onBioChange = { viewModel.updateBio(it) }
    )
}
```

---

## Common Pitfalls & Anti-Patterns

### ❌ Anti-Pattern 1: Direct State Without `remember`

```kotlin
// WRONG: var userName = "" resets on every recomposition
@Composable
fun LoginForm() {
    var userName = ""  // ← Lost after recomposition!

    TextField(
        value = userName,
        onValueChange = { userName = it }
    )
}
```

**Fix:** Always use `remember { mutableStateOf(...) }` for local state.

### ❌ Anti-Pattern 2: Heavy Computations in the Composable Body

```kotlin
// WRONG: Runs on every recomposition
@Composable
fun UserList(users: List<User>) {
    val sortedUsers = users.sortedBy { it.name }  // ← Re-sorts every recomposition!

    LazyColumn {
        items(sortedUsers) { user -> Text(user.name) }
    }
}

// CORRECT: Caches the result
@Composable
fun UserList(users: List<User>) {
    val sortedUsers = remember(users) {
        users.sortedBy { it.name }
    }

    LazyColumn {
        items(sortedUsers) { user -> Text(user.name) }
    }
}
```

### ❌ Anti-Pattern 3: Using `mutableStateOf` in ViewModel for Persistent State

```kotlin
// WRONG: Transient state dies on process death
class UserViewModel : ViewModel() {
    val userName = mutableStateOf("")  // ← Lost if process is killed
}

// CORRECT: Use SavedStateHandle
class UserViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val userName = savedStateHandle.getStateFlow("user_name", "")  // ← Survives process death
}
```

### ❌ Anti-Pattern 4: Reading State in Composition When Layout Suffices

Always defer state reads to the Layout phase using lambda modifiers (`offset { ... }`, `drawBehind { ... }`, etc.) when applicable.

---

## Testing Compose Code

### Unit Test Pattern

```kotlin
class SearchViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun updateQuery_changesState() {
        val viewModel = SearchViewModel()
        viewModel.updateQuery("test")

        assertEquals("test", viewModel.searchQuery.value)
    }
}
```

### Composable UI Test Pattern

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun searchBar_callsOnQueryChange_whenTextChanges() {
    composeTestRule.setContent {
        SearchBar(
            query = "",
            onQueryChange = { /* ... */ }
        )
    }

    composeTestRule.onNodeWithTag("searchInput").performTextInput("hello")
    // Assert the callback was invoked
}
```

---

## Material Design 3 Integration

Always use Material 3 components (default in modern projects):

```kotlin
// Material 3 imports
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface

@Composable
fun MyScreen() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            TextField(
                value = "",
                onValueChange = {},
                label = { Text("Enter name") }
            )
            Button(onClick = {}) {
                Text("Submit")
            }
        }
    }
}
```

---

## Navigation in Compose

Use the official `navigation-compose` library or type-safe alternatives like **Compose Destinations**:

```kotlin
// Basic navigation-compose example
val navController = rememberNavController()

NavHost(navController, startDestination = "home") {
    composable("home") { HomeScreen(navController) }
    composable("details/{id}") { backStackEntry ->
        val id = backStackEntry.arguments?.getString("id")
        DetailsScreen(id, navController)
    }
}
```

---

## Code Generation for LLMs: Key Constraints

When prompting an AI agent to generate Compose code, explicitly request:

1. **State hoisting:** Separate stateless (reusable) from stateful (logic-aware) components.
2. **ViewModel integration:** Use `SavedStateHandle` and `StateFlow` for business state.
3. **Performance-conscious modifiers:** Use lambda forms of `offset`, `clip`, etc.
4. **Accessibility:** Every `Image` must have `contentDescription` unless explicitly decorative.
5. **`@Stable` annotations:** Mark immutable data classes to enable compiler optimizations.
6. **`collectAsStateWithLifecycle()`:** Always use this for Flow collection in Composables.
7. **No lazy module composition:** Avoid `if (condition) { SomeComposable() }` to conditionally include Composables; use conditional logic inside the Composable body instead.

---

## References & Ecosystem

- **Official Docs:** https://developer.android.com/jetpack/compose
- **Material 3:** https://m3.material.io/
- **Navigation:** https://developer.android.com/jetpack/compose/navigation
- **State Management:** https://developer.android.com/jetpack/compose/state
- **Performance:** https://developer.android.com/jetpack/compose/performance
- **Testing:** https://developer.android.com/jetpack/compose/testing
- **Third-Party Libraries:** Compose Destinations (type-safe navigation), Orbitmvi (MVI pattern), Hilt (DI)

---

## Summary Checklist for Code Review

- [ ] Local ephemeral state uses `remember { mutableStateOf(...) }`?
- [ ] Business logic state lives in ViewModel with `StateFlow`?
- [ ] State collection uses `collectAsStateWithLifecycle()`?
- [ ] Components are separated into stateless (reusable) and stateful (logic) variants?
- [ ] High-frequency state reads use lambda modifiers or `derivedStateOf`?
- [ ] All meaningful `Image` and `Icon` composables have `contentDescription`?
- [ ] Data classes representing state are marked `@Immutable` or `@Stable`?
- [ ] Navigation uses official or type-safe navigation library?
- [ ] Heavy computations are cached with `remember` or extracted to ViewModel?
