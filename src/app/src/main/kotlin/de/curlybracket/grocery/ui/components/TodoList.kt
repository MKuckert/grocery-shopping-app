package de.curlybracket.grocery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.curlybracket.grocery.powersync.TodoItem

@Composable
internal fun TodoList(
    items: List<TodoItem>,
    onItemClicked: (item: TodoItem) -> Unit,
    onItemDoneChanged: (item: TodoItem, isDone: Boolean) -> Unit,
    onItemDeleteClicked: (item: TodoItem) -> Unit,
) {
    Box {
        val listState = rememberLazyListState()

        LazyColumn(state = listState) {
            items(items) { item ->
                TodoItemRow(
                    item = item,
                    onClicked = { onItemClicked(item) },
                    onDoneChanged = { onItemDoneChanged(item, it) },
                    onDeleteClicked = { onItemDeleteClicked(item) },
                )
                HorizontalDivider()
            }
        }
    }
}
