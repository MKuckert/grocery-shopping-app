package de.curlybracket.grocery.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.curlybracket.grocery.ui.screens.detail.util.getProductImageUri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
  onBack: () -> Unit
) {
  val viewModel = androidx.hilt.navigation.compose.hiltViewModel<de.curlybracket.grocery.ui.screens.detail.DetailViewModel>()

  val product by viewModel.product.collectAsStateWithLifecycle()
  val groups by viewModel.groups.collectAsStateWithLifecycle()
  val barcodes by viewModel.barcodes.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.saveError.collect { message ->
      coroutineScope.launch {
        snackbarHostState.showSnackbar(
          message = message,
          duration = SnackbarDuration.Short
        )
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Product Detail") },
        navigationIcon = {
          IconButton(
            onClick = onBack
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        actions = {
          androidx.compose.material3.TextButton(
            onClick = { viewModel.saveChanges() }
          ) {
            Text("Save")
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->
    if (product == null) {
      androidx.compose.foundation.layout.Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Text("Loading product...")
      }
    } else {
      val p = product!!
      DetailContent(
        product = p,
        groups = groups,
        barcodes = barcodes,
        onUpdateName = { viewModel.updateName(it) },
        onUpdateGroup = { viewModel.updateGroup(it) },
        onUpdateCurrentStock = { viewModel.updateCurrentStock(it) },
        onUpdateMinimumStock = { viewModel.updateMinimumStock(it) },
        onAddBarcode = { viewModel.addBarcode(it) },
        onDeleteBarcode = { viewModel.deleteBarcode(it) },
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun DetailContent(
  product: de.curlybracket.grocery.domain.model.ProductKind,
  groups: List<de.curlybracket.grocery.domain.model.ProductGroup>,
  barcodes: List<de.curlybracket.grocery.domain.model.Barcode>,
  onUpdateName: (String) -> Unit,
  onUpdateGroup: (String?) -> Unit,
  onUpdateCurrentStock: (Int) -> Unit,
  onUpdateMinimumStock: (Int) -> Unit,
  onAddBarcode: (String) -> Unit,
  onDeleteBarcode: (de.curlybracket.grocery.domain.model.Barcode) -> Unit,
  modifier: Modifier = Modifier
) {
  var name by remember { mutableStateOf(product.name) }
  var selectedGroup by remember {
    mutableStateOf(groups.find { it.id == product.groupId })
  }
  var currentStock by remember { mutableStateOf(product.currentStock) }
  var minimumStock by remember { mutableStateOf(product.minimumStock) }
  var groupExpanded by remember { mutableStateOf(false) }
  var showAddBarcodeDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    ProductImagePreview(imagePath = product.imagePath)

    OutlinedTextField(
      value = name,
      onValueChange = {
        name = it
        onUpdateName(it)
      },
      label = { Text("Product Name") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )

    ExposedDropdownMenuBox(
      expanded = groupExpanded,
      onExpandedChange = { groupExpanded = !groupExpanded },
      modifier = Modifier.fillMaxWidth()
    ) {
      OutlinedTextField(
        value = selectedGroup?.name ?: "Unsorted",
        onValueChange = {},
        label = { Text("Product Group") },
        readOnly = true,
        trailingIcon = {
          ExposedDropdownMenuDefaults.TrailingIcon(
            expanded = groupExpanded
          )
        },
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        modifier = Modifier
          .fillMaxWidth()
          .menuAnchor()
      )
      DropdownMenu(
        expanded = groupExpanded,
        onDismissRequest = { groupExpanded = false }
      ) {
        DropdownMenuItem(
          text = { Text("Unsorted") },
          onClick = {
            selectedGroup = null
            onUpdateGroup(null)
            groupExpanded = false
          }
        )
        groups.forEach { group ->
          DropdownMenuItem(
            text = { Text(group.name) },
            onClick = {
              selectedGroup = group
              onUpdateGroup(group.id)
              groupExpanded = false
            }
          )
        }
      }
    }

    IntStepper(
      label = "Current Stock",
      value = currentStock,
      onValueChange = {
        currentStock = it
        onUpdateCurrentStock(it)
      }
    )

    IntStepper(
      label = "Minimum Stock",
      value = minimumStock,
      onValueChange = {
        minimumStock = it
        onUpdateMinimumStock(it)
      }
    )

    Column {
      Text(
        text = "Barcodes",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      if (barcodes.isEmpty()) {
        Text(
          text = "No barcodes assigned",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          barcodes.forEach { barcode ->
            AssistChip(
              onClick = { onDeleteBarcode(barcode) },
              label = { Text(barcode.barcodeNumber) },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Delete barcode"
                )
              }
            )
          }
        }
      }

      androidx.compose.material3.TextButton(
        onClick = { showAddBarcodeDialog = true }
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Add barcode"
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text("Add Barcode")
      }
    }
  }

  if (showAddBarcodeDialog) {
    AddBarcodeDialog(
      onDismiss = { showAddBarcodeDialog = false },
      onAdd = { barcode ->
        onAddBarcode(barcode)
        showAddBarcodeDialog = false
      }
    )
  }
}

@Composable
private fun ProductImagePreview(
  imagePath: String?,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val imageUri = imagePath?.let {
    getProductImageUri(context, it)
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (imageUri != null) {
      AsyncImage(
        model = imageUri,
        contentDescription = "Product image",
        modifier = Modifier
          .fillMaxWidth()
          .height(200.dp)
      )
    } else {
      androidx.compose.foundation.layout.Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Image,
            contentDescription = "Product image placeholder",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "No image",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun IntStepper(
  label: String,
  value: Int,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f)
    )
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      androidx.compose.material3.IconButton(
        onClick = {
          val newValue = (value - 1).coerceAtLeast(0)
          onValueChange(newValue)
        }
      ) {
        Text("-", style = MaterialTheme.typography.titleLarge)
      }
      Text(
        text = value.toString(),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 8.dp)
      )
      androidx.compose.material3.IconButton(
        onClick = {
          val newValue = value + 1
          onValueChange(newValue)
        }
      ) {
        Text("+", style = MaterialTheme.typography.titleLarge)
      }
    }
  }
}

@Composable
private fun AddBarcodeDialog(
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit
) {
  var barcode by remember { mutableStateOf("") }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Barcode") },
    confirmButton = {
      androidx.compose.material3.TextButton(
        onClick = {
          if (barcode.isNotBlank()) onAdd(barcode.trim())
          onDismiss()
        }
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(
        onClick = onDismiss
      ) {
        Text("Cancel")
      }
    },
    text = {
      OutlinedTextField(
        value = barcode,
        onValueChange = { barcode = it },
        label = { Text("Barcode Number") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )
    }
  )
}
