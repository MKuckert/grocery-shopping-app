package de.curlybracket.grocery.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.Screen

@Composable
internal fun Input(
    text: String,
    onTextChanged: (String) -> Unit,
    onAddClicked: () -> Unit,
    screen: Screen,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        OutlinedTextField(
            value = text,
            modifier = Modifier.weight(weight = 1F),
            onValueChange = onTextChanged,
            label = { Text(text = if (screen == Screen.Home) "Add a list" else "Add a todo") },
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onAddClicked) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}
