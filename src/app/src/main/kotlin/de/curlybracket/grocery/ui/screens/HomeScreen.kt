package de.curlybracket.grocery.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.powersync.bucket.StreamPriority
import com.powersync.sync.SyncStatusData
import de.curlybracket.grocery.Screen
import de.curlybracket.grocery.powersync.ListItem
import de.curlybracket.grocery.ui.components.Input
import de.curlybracket.grocery.ui.components.ListContent
import de.curlybracket.grocery.ui.components.Menu
import de.curlybracket.grocery.ui.components.WifiIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    items: List<ListItem>,
    inputText: String,
    status: SyncStatusData,
    onSignOutSelected: () -> Unit,
    onItemClicked: (item: ListItem) -> Unit,
    onItemDeleteClicked: (item: ListItem) -> Unit,
    onAddItemClicked: () -> Unit,
    onInputTextChanged: (value: String) -> Unit,
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    "Todo Lists",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                )
            },
            navigationIcon = { Menu(true, onSignOutSelected) },
            actions = {
                WifiIcon(status.connected)
                Spacer(modifier = Modifier.width(16.dp))
            },
        )

        Input(
            text = inputText,
            onAddClicked = onAddItemClicked,
            onTextChanged = onInputTextChanged,
            screen = Screen.Home,
        )

        Box(Modifier.weight(1F)) {
            if (status.statusForPriority(StreamPriority(1)).hasSynced == true) {
                ListContent(
                    items = items,
                    onItemClicked = onItemClicked,
                    onItemDeleteClicked = onItemDeleteClicked,
                )
            } else {
                Text("Busy with sync...")
            }
        }
    }
}
