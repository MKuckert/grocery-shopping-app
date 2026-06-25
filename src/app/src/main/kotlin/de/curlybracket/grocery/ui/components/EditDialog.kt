package de.curlybracket.grocery.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.curlybracket.grocery.powersync.TodoItem

@Composable
internal fun EditDialog(
    item: TodoItem,
    onCloseClicked: () -> Unit,
    onTextChanged: (String) -> Unit,
    onDoneChanged: (Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onCloseClicked) {
        Card(elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .height(IntrinsicSize.Min),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    Text(text = "Edit todo")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1F)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextField(
                            value = item.description,
                            modifier = Modifier.weight(1F).fillMaxWidth().sizeIn(minHeight = 192.dp),
                            label = { Text("Todo text") },
                            onValueChange = onTextChanged,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(text = "Completed", Modifier.padding(15.dp))
                            Checkbox(
                                checked = item.completed,
                                onCheckedChange = onDoneChanged,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onCloseClicked,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = "Done")
                }
            }
        }
    }
}
