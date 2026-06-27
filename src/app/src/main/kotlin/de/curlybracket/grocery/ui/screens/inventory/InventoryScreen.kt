package de.curlybracket.grocery.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.curlybracket.grocery.domain.model.GroupWithProducts
import de.curlybracket.grocery.domain.model.ProductKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryScreen(
  viewModel: InventoryViewModel = hiltViewModel(),
  onNavigateToDetail: (String) -> Unit
) {
  val groupsWithProducts by viewModel.groupsWithProducts.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var showScannerSheet by remember { mutableStateOf(false) }

  // Collect snackbar messages
  LaunchedEffect(Unit) {
    viewModel.snackbarMessage.collect { message ->
      if (message.text.startsWith("navigate:")) {
        onNavigateToDetail(message.productId)
      } else {
        coroutineScope.launch {
          val result = snackbarHostState.showSnackbar(
            message = message.text,
            actionLabel = message.actionLabel,
            duration = SnackbarDuration.Short
          )
          // If "Details" action was clicked
          if (result.name == "ActionPerformed") {
            onNavigateToDetail(message.productId)
          }
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Grocery Inventory") },
        actions = {
          TextButton(
            onClick = { viewModel.startShopping() },
            modifier = Modifier.padding(end = 16.dp)
          ) {
            Text("Start Shopping")
          }
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showScannerSheet = true },
        modifier = Modifier.padding(16.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.CameraAlt,
          contentDescription = "Scan barcode"
        )
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->
    if (groupsWithProducts.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Text("No products in inventory")
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        groupsWithProducts.forEach { groupWithProducts ->
          stickyHeader {
            Text(
              text = groupWithProducts.groupName,
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          items(
            items = groupWithProducts.products,
            key = { product -> product.id }
          ) { product ->
            InventoryProductRow(
              product = product,
              onSwipeRight = { viewModel.decrementStock(product) },
              onTap = { viewModel.navigateToDetail(product) }
            )
          }
        }
      }
    }
  }

  if (showScannerSheet) {
    InventoryScannerBottomSheet(
      onDismiss = { showScannerSheet = false },
      onProductScanned = { product ->
        viewModel.decrementStock(product)
        showScannerSheet = false
      }
    )
  }
}

@Composable
private fun InventoryProductRow(
  product: ProductKind,
  onSwipeRight: () -> Unit,
  onTap: () -> Unit
) {
  val hapticFeedback = LocalHapticFeedback.current
  var swipeOffset by remember { mutableStateOf(0f) }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .pointerInput(Unit) {
        detectHorizontalDragGestures(
          onDragEnd = {
            if (swipeOffset > 100) {
              hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onSwipeRight()
            }
            swipeOffset = 0f
          },
          onHorizontalDrag = { _: PointerInputChange, dragAmount: Float ->
            swipeOffset += dragAmount
          }
        )
      }
      .pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() })
      }
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = product.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "Stock: ${product.currentStock}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryScannerBottomSheet(
  onDismiss: () -> Unit,
  onProductScanned: (ProductKind) -> Unit
) {
  // Placeholder for InventoryScannerBottomSheet (Task 11)
  // For now, display a message that it will be implemented
  ModalBottomSheet(
    onDismissRequest = onDismiss
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
      contentAlignment = Alignment.Center
    ) {
      Text("Scanner to be implemented in Task 11")
    }
  }
}
