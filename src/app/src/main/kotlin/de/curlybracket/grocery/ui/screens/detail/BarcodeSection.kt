package de.curlybracket.grocery.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.R
import de.curlybracket.grocery.domain.model.Barcode

@Composable
internal fun BarcodesSection(
    barcodes: List<Barcode>,
    onAddBarcode: (String) -> Unit,
    onDeleteBarcode: (Barcode) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.detail_section_barcodes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.semantics {
                    contentDescription = context.getString(R.string.detail_cd_add_barcode)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.detail_cd_add_barcode),
                )
            }
        }

        if (barcodes.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_no_barcodes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                barcodes.forEach { barcode ->
                    AssistChip(
                        onClick = {},
                        label = { Text(barcode.barcodeNumber) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onDeleteBarcode(barcode) },
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics {
                                        contentDescription = context.getString(
                                            R.string.detail_cd_delete_barcode,
                                            barcode.barcodeNumber,
                                        )
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBarcodeDialog(
            onConfirm = { barcodeNumber ->
                onAddBarcode(barcodeNumber)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
internal fun AddBarcodeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_dialog_add_barcode_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.detail_dialog_add_barcode_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = context.getString(R.string.detail_cd_barcode_number_input)
                    },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onConfirm(input.trim()) },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BarcodesSectionEmptyPreview() {
    BarcodesSection(barcodes = emptyList(), onAddBarcode = {}, onDeleteBarcode = {})
}
