package de.curlybracket.grocery.ui.screens.unloading

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnloadingScreen(
  viewModel: UnloadingViewModel = hiltViewModel(),
  onNavigateToDetail: (String) -> Unit = {}
) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  val showDialog by viewModel.showWarningDialog.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.snackbarMessage.collect { message ->
      coroutineScope.launch {
        snackbarHostState.showSnackbar(message)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Unloading") },
        actions = {
          TextButton(
            onClick = { viewModel.requestSubmit() },
            modifier = Modifier.padding(end = 16.dp)
          ) {
            Text("Submit Unloading")
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->
    if (items.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Text("No items to unload")
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        items(
          items = items,
          key = { it.id }
        ) { product ->
          UnloadingRow(
            product = product,
            onToggleUnloadOpen = { viewModel.setUnloadOpen(product, it) },
            onIncrement = { viewModel.incrementPending(product) },
            onDecrement = { viewModel.decrementPending(product) },
            onTap = { onNavigateToDetail(product.id) }
          )
        }
      }
    }
  }

  if (showDialog) {
    val uncheckedCount = items.count { it.unloadOpen }
    UnloadingWarningDialog(
      uncheckedCount = uncheckedCount,
      onDismiss = { viewModel.dismissDialog() },
      onConfirm = { viewModel.confirmSubmit() }
    )
  }
}

@Composable
private fun UnloadingRow(
  product: de.curlybracket.grocery.domain.model.ProductKind,
  onToggleUnloadOpen: (Boolean) -> Unit,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  onTap: () -> Unit = {}
) {
  val total = product.currentStock + product.pendingStock
  val isDisabled = !product.unloadOpen

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() })
      },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Checkbox(
      checked = !product.unloadOpen,
      onCheckedChange = { checked ->
        onToggleUnloadOpen(!checked)
      }
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 8.dp)
    ) {
      Text(
        text = product.name,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDisabled) Color.Gray else MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "${product.currentStock} + ${product.pendingStock} = $total",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      TextButton(
        onClick = onDecrement,
        enabled = product.unloadOpen
      ) {
        Text("[-]")
      }
      TextButton(
        onClick = onIncrement,
        enabled = product.unloadOpen
      ) {
        Text("[+]")
      }
    }
  }
}

@Composable
private fun UnloadingWarningDialog(
  uncheckedCount: Int,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Unsubmitted Items") },
    text = {
      Text("$uncheckedCount item(s) still have unchecked status. They will be submitted with current pending stock.")
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Submit Anyway")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Go Back")
      }
    }
  )
}
