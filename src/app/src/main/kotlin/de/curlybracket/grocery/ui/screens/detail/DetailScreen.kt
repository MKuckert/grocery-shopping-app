package de.curlybracket.grocery.ui.screens.detail

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.curlybracket.grocery.BuildConfig
import de.curlybracket.grocery.R
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.ProductGroup
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScreen(onBack: (deletedProductId: String?) -> Unit) {
    val viewModel: DetailViewModel = hiltViewModel()
    val product by viewModel.product.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val barcodes by viewModel.barcodes.collectAsStateWithLifecycle()
    val savedIndicator by viewModel.savedIndicator.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val groupId by viewModel.groupId.collectAsStateWithLifecycle()
    val currentStock by viewModel.currentStock.collectAsStateWithLifecycle()
    val minimumStock by viewModel.minimumStock.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg.text)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteEvent.collect { deletedId ->
            onBack(deletedId)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    stringResource(
                        R.string.detail_dialog_delete_title,
                        product?.name ?: stringResource(R.string.detail_label_product_name),
                    ),
                )
            },
            text = { Text(stringResource(R.string.detail_dialog_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteProduct()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack(null) },
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.detail_cd_navigate_back)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_cd_back),
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = savedIndicator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.detail_cd_saved),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.detail_cd_delete_product)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.detail_cd_delete_product),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ImagePreview(
                    imagePath = product?.imagePath,
                    context = context,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text(stringResource(R.string.detail_label_product_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = context.getString(R.string.detail_cd_product_name_field)
                        },
                )

                GroupDropdown(
                    groups = groups,
                    selectedGroupId = groupId,
                    onGroupSelected = { viewModel.updateGroup(it) },
                    onCreateGroup = { viewModel.createGroup(it) },
                )

                StepperField(
                    label = stringResource(R.string.detail_label_current_stock),
                    value = currentStock,
                    onDecrement = { viewModel.updateCurrentStock(currentStock - 1) },
                    onIncrement = { viewModel.updateCurrentStock(currentStock + 1) },
                    onValueChange = { viewModel.updateCurrentStock(it) },
                    contentDescriptionDecrement = stringResource(R.string.detail_cd_decrease_current_stock),
                    contentDescriptionIncrement = stringResource(R.string.detail_cd_increase_current_stock),
                    contentDescriptionField = stringResource(R.string.detail_cd_current_stock_field),
                )

                StepperField(
                    label = stringResource(R.string.detail_label_minimum_stock),
                    value = minimumStock,
                    onDecrement = { viewModel.updateMinimumStock(minimumStock - 1) },
                    onIncrement = { viewModel.updateMinimumStock(minimumStock + 1) },
                    onValueChange = { viewModel.updateMinimumStock(it) },
                    contentDescriptionDecrement = stringResource(R.string.detail_cd_decrease_minimum_stock),
                    contentDescriptionIncrement = stringResource(R.string.detail_cd_increase_minimum_stock),
                    contentDescriptionField = stringResource(R.string.detail_cd_minimum_stock_field),
                )

                BarcodesSection(
                    barcodes = barcodes,
                    onAddBarcode = { viewModel.addBarcode(it) },
                    onDeleteBarcode = { viewModel.deleteBarcode(it) },
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(imagePath: String?, context: Context) {
    val imageUri = remember(imagePath) {
        imagePath?.let { path ->
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    File(path),
                )
            }.getOrNull()
        }
    }

    if (imageUri != null) {
        AsyncImage(
            model = imageUri,
            contentDescription = stringResource(R.string.detail_cd_product_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = stringResource(R.string.detail_cd_no_product_image),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdown(
    groups: List<ProductGroup>,
    selectedGroupId: String?,
    onGroupSelected: (String?) -> Unit,
    onCreateGroup: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val selectedGroup = groups.find { it.id == selectedGroupId }
    val context = LocalContext.current

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
                    contentDescription = context.getString(R.string.detail_cd_group_selector)
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
private fun CreateGroupDialog(
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
    val context = LocalContext.current

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
                            contentDescription = context.getString(R.string.detail_cd_new_group_name_input)
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

@Composable
private fun StepperField(
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

@Composable
private fun BarcodesSection(
    barcodes: List<Barcode>,
    onAddBarcode: (String) -> Unit,
    onDeleteBarcode: (Barcode) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun AddBarcodeDialog(
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
