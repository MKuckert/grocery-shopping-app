package de.curlybracket.grocery.ui.screens.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.curlybracket.grocery.domain.model.ProductKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingScreen(
  viewModel: ShoppingViewModel = hiltViewModel(),
  onNavigateToDetail: (String) -> Unit
) {
  val activeShopping by viewModel.activeShopping.collectAsStateWithLifecycle()
  val struckThrough by viewModel.struckThrough.collectAsStateWithLifecycle()
  val impulseBuys by viewModel.impulseBuys.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var showScannerSheet by remember { mutableStateOf(false) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }

  // Collect snackbar messages
  LaunchedEffect(Unit) {
    viewModel.snackbarMessage.collect { message ->
      coroutineScope.launch {
        snackbarHostState.showSnackbar(
          message = message.text,
          duration = SnackbarDuration.Short
        )
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Shopping List") },
        actions = {
          TextButton(
            onClick = { viewModel.finishShopping() },
            modifier = Modifier.padding(end = 16.dp)
          ) {
            Text("Finish Shopping")
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
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .semantics { isTraversalGroup = true }
    ) {
      // Material 3 SearchBar with proper API
      SearchBar(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .semantics { traversalIndex = 0f }
          .padding(horizontal = 8.dp, vertical = 8.dp),
        inputField = {
          SearchBarDefaults.InputField(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onSearch = {
              viewModel.updateSearchQuery(it)
              searchExpanded = false
            },
            expanded = searchExpanded,
            onExpandedChange = { searchExpanded = it },
            placeholder = { Text("Search products") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
              )
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = {
                  viewModel.updateSearchQuery("")
                }) {
                  Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear search"
                  )
                }
              }
            }
          )
        },
        expanded = searchExpanded,
        onExpandedChange = { searchExpanded = it }
      ) {
        // Search results content
        if (searchResults.isNotEmpty()) {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            searchResults.forEach { product ->
              ListItem(
                headlineContent = { Text(product.name) },
                supportingContent = {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Button(
                      onClick = {
                        viewModel.forceAddToCart(product)
                        searchExpanded = false
                      },
                      modifier = Modifier.height(32.dp)
                    ) {
                      Text("Force Add", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                      onClick = {
                        onNavigateToDetail(product.id)
                        searchExpanded = false
                      },
                      modifier = Modifier.height(32.dp)
                    ) {
                      Text("Details", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 4.dp)
              )
            }
          }
        }
      }

      // Main content (visible when SearchBar is not expanded)
      if (!searchExpanded) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp),
          contentPadding = PaddingValues(bottom = 16.dp)
        ) {
          // Active Shopping List section
          if (activeShopping.isNotEmpty()) {
            stickyHeader {
              Text(
                text = "▼ Active Shopping List",
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            items(
              items = activeShopping,
              key = { product -> product.id }
            ) { product ->
              ShoppingProductRow(
                product = product,
                isStruckThrough = false,
                isImpulse = false,
                onFullFill = { viewModel.fulfillFull(product) },
                onIncrement = { viewModel.incrementPending(product) },
                onDecrement = { viewModel.decrementPending(product) },
                onNavigateToDetail = { onNavigateToDetail(product.id) }
              )
            }
          }

          // Struck-Through Cart Items section
          if (struckThrough.isNotEmpty()) {
            stickyHeader {
              Text(
                text = "▼ Struck-Through Cart Items",
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            items(
              items = struckThrough,
              key = { product -> product.id }
            ) { product ->
              ShoppingProductRow(
                product = product,
                isStruckThrough = true,
                isImpulse = false,
                onFullFill = { viewModel.fulfillFull(product) },
                onIncrement = { viewModel.incrementPending(product) },
                onDecrement = { viewModel.decrementPending(product) },
                onNavigateToDetail = { onNavigateToDetail(product.id) }
              )
            }
          }

          // Impulse Buys section
          if (impulseBuys.isNotEmpty()) {
            stickyHeader {
              Text(
                text = "▼ Impulse Buys",
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            items(
              items = impulseBuys,
              key = { product -> product.id }
            ) { product ->
              ShoppingProductRow(
                product = product,
                isStruckThrough = false,
                isImpulse = true,
                onFullFill = { viewModel.fulfillFull(product) },
                onIncrement = { viewModel.incrementPending(product) },
                onDecrement = { viewModel.decrementPending(product) },
                onNavigateToDetail = { onNavigateToDetail(product.id) }
              )
            }
          }

          // Empty state
          if (activeShopping.isEmpty() && struckThrough.isEmpty() && impulseBuys.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(200.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("No items in shopping list")
              }
            }
          }
        }
      }
    }
  }

  if (showScannerSheet) {
    ShoppingScannerBottomSheet(
      onDismiss = { showScannerSheet = false }
    )
  }
}

@Composable
private fun ShoppingProductRow(
  product: ProductKind,
  isStruckThrough: Boolean,
  isImpulse: Boolean,
  onFullFill: () -> Unit,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  onNavigateToDetail: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .align(Alignment.CenterStart),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Checkbox (checked when struck through or impulse bought)
      Checkbox(
        checked = isStruckThrough || (isImpulse && product.pendingStock > 0),
        onCheckedChange = { if (it) onFullFill() else onDecrement() }
      )

      // Product name and quantity info (clickable area to fulfil)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) { onFullFill() }
          .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Column {
          Text(
            text = product.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textDecoration = if (isStruckThrough) TextDecoration.LineThrough else TextDecoration.None
          )
          Text(
            text = if (isImpulse) {
              "${product.pendingStock}"
            } else {
              "${product.pendingStock}/${product.quantityToBuy}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Stepper controls
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        IconButton(onClick = onDecrement, modifier = Modifier.padding(0.dp)) {
          Icon(
            imageVector = Icons.Filled.Remove,
            contentDescription = "Decrease",
            modifier = Modifier.padding(8.dp)
          )
        }
        IconButton(onClick = onIncrement, modifier = Modifier.padding(0.dp)) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Increase",
            modifier = Modifier.padding(8.dp)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingScannerBottomSheet(
  onDismiss: () -> Unit
) {
  // Placeholder for ShoppingScannerBottomSheet (Task 11)
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
