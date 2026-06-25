package de.curlybracket.grocery.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.NavController
import de.curlybracket.grocery.Screen
import de.curlybracket.grocery.powersync.TodoItem
import de.curlybracket.grocery.ui.components.Input
import de.curlybracket.grocery.ui.components.TodoList
import de.curlybracket.grocery.ui.components.WifiIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodosScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    items: List<TodoItem>,
    inputText: String,
    isConnected: Boolean?,
    onItemClicked: (item: TodoItem) -> Unit,
    onItemDoneChanged: (item: TodoItem, isDone: Boolean) -> Unit,
    onItemDeleteClicked: (item: TodoItem) -> Unit,
    onAddItemClicked: () -> Unit,
    onInputTextChanged: (value: String) -> Unit,
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    "Todo List",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigate(Screen.Home) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                }
            },
            actions = {
                WifiIcon(isConnected ?: false)
                Spacer(modifier = Modifier.width(16.dp))
            },
        )

        Input(
            text = inputText,
            onAddClicked = onAddItemClicked,
            onTextChanged = onInputTextChanged,
            screen = Screen.Todos,
        )

        Box(Modifier.weight(1F)) {
            TodoList(
                items = items,
                onItemClicked = onItemClicked,
                onItemDoneChanged = onItemDoneChanged,
                onItemDeleteClicked = onItemDeleteClicked,
            )
        }
    }
}
