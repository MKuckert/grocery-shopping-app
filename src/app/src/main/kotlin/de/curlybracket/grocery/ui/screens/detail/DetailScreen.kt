package de.curlybracket.grocery.ui.screens.detail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import de.curlybracket.grocery.BuildConfig
import de.curlybracket.grocery.domain.model.Barcode
import de.curlybracket.grocery.domain.model.ProductGroup
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScreen(productId: String, navController: NavController) {
    val viewModel: DetailViewModel = hiltViewModel()
    val product by viewModel.product.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val barcodes by viewModel.barcodes.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val groupId by viewModel.groupId.collectAsStateWithLifecycle()
    val currentStock by viewModel.currentStock.collectAsStateWithLifecycle()
    val minimumStock by viewModel.minimumStock.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg.text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Detail") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.semantics { contentDescription = "Navigate back" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.saveChanges() },
                modifier = Modifier.semantics { contentDescription = "Save product" },
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Save")
                }
            }
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
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Product name field" },
                )

                GroupDropdown(
                    groups = groups,
                    selectedGroupId = groupId,
                    onGroupSelected = { viewModel.updateGroup(it) },
                )

                StepperField(
                    label = "Current stock",
                    value = currentStock,
                    onDecrement = { viewModel.updateCurrentStock(currentStock - 1) },
                    onIncrement = { viewModel.updateCurrentStock(currentStock + 1) },
                    onValueChange = { viewModel.updateCurrentStock(it) },
                    contentDescriptionDecrement = "Decrease current stock",
                    contentDescriptionIncrement = "Increase current stock",
                    contentDescriptionField = "Current stock field",
                )

                StepperField(
                    label = "Minimum stock",
                    value = minimumStock,
                    onDecrement = { viewModel.updateMinimumStock(minimumStock - 1) },
                    onIncrement = { viewModel.updateMinimumStock(minimumStock + 1) },
                    onValueChange = { viewModel.updateMinimumStock(it) },
                    contentDescriptionDecrement = "Decrease minimum stock",
                    contentDescriptionIncrement = "Increase minimum stock",
                    contentDescriptionField = "Minimum stock field",
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
            contentDescription = "Product image",
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
                contentDescription = "No product image",
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
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedGroup = groups.find { it.id == selectedGroupId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedGroup?.name ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .semantics { contentDescription = "Product group selector" },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("None") },
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
        }
    }
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Barcodes",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.semantics { contentDescription = "Add barcode" },
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add barcode")
            }
        }

        if (barcodes.isEmpty()) {
            Text(
                text = "No barcodes assigned",
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
                                        contentDescription = "Delete barcode ${barcode.barcodeNumber}"
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Barcode") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Barcode number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Barcode number input" },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onConfirm(input.trim()) },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
