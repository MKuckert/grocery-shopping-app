package de.curlybracket.grocery.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.R
import de.curlybracket.grocery.domain.model.ProductGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDropdown(
    groups: List<ProductGroup>,
    selectedGroupId: String?,
    onGroupSelected: (String?) -> Unit,
    onCreateGroup: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val selectedGroup = groups.find { it.id == selectedGroupId }
    val cdGroupSelector = stringResource(R.string.detail_cd_group_selector)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedGroup?.name ?: stringResource(R.string.detail_group_option_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.detail_label_group)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .semantics {
                    contentDescription = cdGroupSelector
                },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_group_option_none)) },
                onClick = {
                    onGroupSelected(null)
                    expanded = false
                },
            )
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onGroupSelected(group.id)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.detail_group_option_create_new),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                },
            )
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            existingGroups = groups,
            onConfirm = { name ->
                onCreateGroup(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
internal fun CreateGroupDialog(
    existingGroups: List<ProductGroup>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val trimmed = input.trim()
    val isDuplicate = remember(trimmed, existingGroups) {
        trimmed.isNotBlank() && existingGroups.any { it.name.equals(trimmed, ignoreCase = true) }
    }
    val isConfirmEnabled = trimmed.isNotBlank() && !isDuplicate
    val cdNewGroupNameInput = stringResource(R.string.detail_cd_new_group_name_input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_dialog_create_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.detail_dialog_create_group_label)) },
                    singleLine = true,
                    isError = isDuplicate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = cdNewGroupNameInput
                        },
                )
                if (isDuplicate) {
                    Text(
                        text = stringResource(R.string.detail_dialog_create_group_error_duplicate),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isConfirmEnabled) onConfirm(trimmed) },
                enabled = isConfirmEnabled,
            ) {
                Text(stringResource(R.string.action_create))
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
private fun GroupDropdownPreview() {
    GroupDropdown(
        groups = emptyList(),
        selectedGroupId = null,
        onGroupSelected = {},
        onCreateGroup = {},
    )
}
