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
import de.curlybracket.grocery.powersync.ListItem

@Composable
internal fun ListContent(
    items: List<ListItem>,
    onItemClicked: (item: ListItem) -> Unit,
    onItemDeleteClicked: (item: ListItem) -> Unit,
) {
    Box {
        val listState = rememberLazyListState()

        LazyColumn(state = listState) {
            items(items) { item ->
                ListItemRow(
                    item = item,
                    onClicked = { onItemClicked(item) },
                    onDeleteClicked = { onItemDeleteClicked(item) },
                )
                HorizontalDivider()
            }
        }
    }
}
