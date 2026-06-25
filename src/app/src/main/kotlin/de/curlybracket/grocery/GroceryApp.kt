package de.curlybracket.grocery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.powersync.PowerSyncDatabase
import com.powersync.compose.rememberDatabaseDriverFactory
import com.powersync.connector.supabase.SupabaseConnector
import de.curlybracket.grocery.auth.AuthState
import de.curlybracket.grocery.auth.AuthViewModel
import de.curlybracket.grocery.data.db.AppSchema
import de.curlybracket.grocery.powersync.ListContent
import de.curlybracket.grocery.powersync.ListItem
import de.curlybracket.grocery.powersync.Todo
import de.curlybracket.grocery.ui.components.EditDialog
import de.curlybracket.grocery.ui.screens.HomeScreen
import de.curlybracket.grocery.ui.screens.SignInScreen
import de.curlybracket.grocery.ui.screens.SignUpScreen
import de.curlybracket.grocery.ui.screens.TodosScreen
import kotlinx.coroutines.runBlocking

@Composable
fun GroceryApp(
    supabaseUrl: String,
    supabaseKey: String,
    powerSyncEndpoint: String,
) {
    val driverFactory = rememberDatabaseDriverFactory()
    val supabase = remember {
        SupabaseConnector(
            powerSyncEndpoint = powerSyncEndpoint,
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey,
        )
    }
    val db = remember { PowerSyncDatabase(driverFactory, AppSchema) }

    val syncStatus = db.currentStatus
    val status by syncStatus.asFlow().collectAsState(syncStatus)

    val navController = remember { NavController(Screen.Home) }
    val authViewModel = remember { AuthViewModel(supabase, db, navController) }

    val authState by authViewModel.authState.collectAsState()
    val currentScreen by navController.currentScreen.collectAsState()
    val userId by authViewModel.userId.collectAsState()
    val currentUserId = rememberUpdatedState(userId)

    val lists = remember { mutableStateOf(ListContent(db, userId)) }
    LaunchedEffect(currentUserId.value) {
        lists.value = ListContent(db, currentUserId.value)
    }
    val selectedListId by lists.value.selectedListId.collectAsState()
    val listItems by lists.value.watchItems().collectAsState(initial = emptyList())
    val listsInputText by lists.value.inputText.collectAsState()

    val todos = remember { mutableStateOf(Todo(db, userId)) }
    LaunchedEffect(currentUserId.value) {
        todos.value = Todo(db, currentUserId.value)
    }
    val todoItems by todos.value.watchItems(selectedListId).collectAsState(initial = emptyList())
    val editingItem by todos.value.editingItem.collectAsState()
    val todosInputText by todos.value.inputText.collectAsState()

    fun handleSignOut() {
        runBlocking { authViewModel.signOut() }
    }

    when (currentScreen) {
        is Screen.Home -> {
            if (authState == AuthState.SignedOut) navController.navigate(Screen.SignIn)

            HomeScreen(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                items = listItems,
                status = status,
                onSignOutSelected = { handleSignOut() },
                inputText = listsInputText,
                onItemClicked = { item: ListItem ->
                    lists.value.onItemClicked(item)
                    navController.navigate(Screen.Todos)
                },
                onItemDeleteClicked = lists.value::onItemDeleteClicked,
                onAddItemClicked = lists.value::onAddItemClicked,
                onInputTextChanged = lists.value::onInputTextChanged,
            )
        }

        is Screen.Todos -> {
            TodosScreen(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                navController = navController,
                items = todoItems,
                isConnected = status.connected,
                inputText = todosInputText,
                onItemClicked = todos.value::onItemClicked,
                onItemDoneChanged = todos.value::onItemDoneChanged,
                onItemDeleteClicked = todos.value::onItemDeleteClicked,
                onAddItemClicked = { todos.value.onAddItemClicked(userId, selectedListId) },
                onInputTextChanged = todos.value::onInputTextChanged,
            )

            editingItem?.also {
                EditDialog(
                    item = it,
                    onCloseClicked = todos.value::onEditorCloseClicked,
                    onTextChanged = todos.value::onEditorTextChanged,
                    onDoneChanged = todos.value::onEditorDoneChanged,
                )
            }
        }

        is Screen.SignIn -> {
            if (authState == AuthState.SignedIn) navController.navigate(Screen.Home)
            SignInScreen(navController, authViewModel)
        }

        is Screen.SignUp -> {
            if (authState == AuthState.SignedIn) navController.navigate(Screen.Home)
            SignUpScreen(navController, authViewModel)
        }
    }
}
