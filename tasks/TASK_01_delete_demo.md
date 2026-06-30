# Task 1: Delete Demo Code & Establish Package Skeleton

> **Depends on:** Task 0
> **Status:** [/]

---

## Description

Remove all files specific to the demo todo-list feature that will not be reused:

- `powersync/TodoSchema.kt`, `powersync/ListContent.kt`, `powersync/Todo.kt`
- `ui/screens/HomeScreen.kt`, `ui/screens/TodosScreen.kt`, `ui/screens/SignUpScreen.kt`
- `ui/components/TodoList.kt`, `ui/components/EditDialog.kt`, `ui/components/ListContent.kt`, `ui/components/ListItemRow.kt`, `ui/components/TodoItemRow.kt`, `ui/components/Input.kt`
- `NavController.kt` (replaced by Compose Navigation)
- `GroceryApp.kt` (rewritten in Task 6)

Create the following empty package directories (stub `package` files are not needed; just ensure the directory exists for subsequent tasks):

```
de.curlybracket.grocery.domain.model
de.curlybracket.grocery.domain.repository
de.curlybracket.grocery.data.db
de.curlybracket.grocery.data.repository
de.curlybracket.grocery.ui.navigation
de.curlybracket.grocery.ui.screens.inventory
de.curlybracket.grocery.ui.screens.shopping
de.curlybracket.grocery.ui.screens.unloading
de.curlybracket.grocery.ui.screens.detail
de.curlybracket.grocery.ui.components
de.curlybracket.grocery.scanner
de.curlybracket.grocery.network
de.curlybracket.grocery.audio
```

---

## Review Criteria

- Deleted files compile-removed; project still builds (stubs OK).
- No references to `ListItem`, `TodoItem`, `LISTS_TABLE`, `TODOS_TABLE` remain.
