package de.curlybracket.grocery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.powersync.TodoItem

@Composable
internal fun TodoItemRow(
    item: TodoItem,
    onClicked: () -> Unit,
    onDoneChanged: (Boolean) -> Unit,
    onDeleteClicked: () -> Unit,
) {
    Row(modifier = Modifier.clickable(onClick = onClicked)) {
        Spacer(modifier = Modifier.width(8.dp))

        Checkbox(
            checked = item.completed,
            modifier = Modifier.align(Alignment.CenterVertically),
            onCheckedChange = onDoneChanged,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = AnnotatedString(item.description),
            modifier = Modifier.weight(1F).align(Alignment.CenterVertically),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onDeleteClicked) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
        }
    }
}
