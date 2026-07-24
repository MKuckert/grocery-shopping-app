package de.curlybracket.grocery.ui.screens.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun StepperField(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueChange: (Int) -> Unit,
    contentDescriptionDecrement: String,
    contentDescriptionIncrement: String,
    contentDescriptionField: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = onDecrement,
            enabled = value > 0,
            modifier = Modifier.semantics { contentDescription = contentDescriptionDecrement },
        ) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
        }
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { onValueChange(it) } },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = contentDescriptionField },
        )
        IconButton(
            onClick = onIncrement,
            modifier = Modifier.semantics { contentDescription = contentDescriptionIncrement },
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StepperFieldPreview() {
    StepperField(
        label = "Current Stock",
        value = 3,
        onDecrement = {},
        onIncrement = {},
        onValueChange = {},
        contentDescriptionDecrement = "Decrease",
        contentDescriptionIncrement = "Increase",
        contentDescriptionField = "Stock field",
    )
}
