package de.curlybracket.grocery.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.scanner.BarcodeScannerBottomSheet
import de.curlybracket.grocery.scanner.ScanResult
import de.curlybracket.grocery.scanner.ScannerMode
import de.curlybracket.grocery.scanner.ScannerProcessor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingScreen(
    onNavigateToDetail: (String) -> Unit,
    scannerProcessor: ScannerProcessor,
) {
    val viewModel: ShoppingViewModel = hiltViewModel()

    val activeShopping by viewModel.activeShopping.collectAsStateWithLifecycle()
    val struckThrough by viewModel.struckThrough.collectAsStateWithLifecycle()
    val impulseBuys by viewModel.impulseBuys.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val householdId by viewModel.householdIdFlow.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg.text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                actions = {
                    TextButton(onClick = { viewModel.finishShopping() }) {
                        Text("Finish Shopping")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showScanner = true }) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Scan barcode",
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearch = { },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        placeholder = { Text("Search products…") },
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (searchExpanded) 0.dp else 16.dp),
            ) {
                LazyColumn {
                    items(
                        items = searchResults,
                        key = { it.id },
                    ) { product ->
                        SearchResultCard(
                            product = product,
                            onForceAdd = { viewModel.forceAddToCart(product) },
                            onDetails = {
                                searchExpanded = false
                                onNavigateToDetail(product.id)
                            },
                        )
                    }
                }
            }

            if (!searchExpanded) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (activeShopping.isNotEmpty()) {
                        stickyHeader(key = "header_active") {
                            SectionHeader("▼ Active Shopping List")
                        }
                        items(
                            items = activeShopping,
                            key = { it.id },
                        ) { product ->
                            ShoppingRow(
                                product = product,
                                isStruckThrough = false,
                                onRowTap = { viewModel.fulfillFull(product) },
                                onIncrement = { viewModel.incrementPending(product) },
                                onDecrement = { viewModel.decrementPending(product) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (struckThrough.isNotEmpty()) {
                        stickyHeader(key = "header_struck") {
                            SectionHeader("▼ Struck-Through Cart Items")
                        }
                        items(
                            items = struckThrough,
                            key = { it.id },
                        ) { product ->
                            ShoppingRow(
                                product = product,
                                isStruckThrough = true,
                                onRowTap = { viewModel.fulfillFull(product) },
                                onIncrement = { viewModel.incrementPending(product) },
                                onDecrement = { viewModel.decrementPending(product) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (impulseBuys.isNotEmpty()) {
                        stickyHeader(key = "header_impulse") {
                            SectionHeader("▼ Impulse Buys")
                        }
                        items(
                            items = impulseBuys,
                            key = { it.id },
                        ) { product ->
                            ShoppingRow(
                                product = product,
                                isStruckThrough = true,
                                onRowTap = { viewModel.fulfillFull(product) },
                                onIncrement = { viewModel.incrementPending(product) },
                                onDecrement = { viewModel.decrementPending(product) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    val hid = householdId
    if (showScanner && hid != null) {
        BarcodeScannerBottomSheet(
            mode = ScannerMode.IncrementPendingStock(hid),
            isOpen = showScanner,
            onDismiss = { showScanner = false },
            onResult = { result ->
                when (result) {
                    is ScanResult.Hit -> scope.launch {
                        snackbarHostState.showSnackbar("Added: ${result.product.name}")
                    }
                    is ScanResult.Restored -> scope.launch {
                        snackbarHostState.showSnackbar("Restored: ${result.product.name}")
                    }
                    is ScanResult.Linked -> scope.launch {
                        snackbarHostState.showSnackbar("Barcode linked to ${result.product.name}")
                    }
                    is ScanResult.Miss -> { /* CaptureRequired overlay handles this in-sheet */ }
                }
            },
            processor = scannerProcessor,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ShoppingRow(
    product: ProductKind,
    isStruckThrough: Boolean,
    onRowTap: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (product.quantityToBuy > 0) {
        "${product.pendingStock}/${product.quantityToBuy}"
    } else {
        "${product.pendingStock}"
    }

    Surface(
        onClick = onRowTap,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isStruckThrough,
                onCheckedChange = null,
            )
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (isStruckThrough) TextDecoration.LineThrough else TextDecoration.None,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = fraction,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onDecrement) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease ${product.name}",
                )
            }
            IconButton(onClick = onIncrement) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase ${product.name}",
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    product: ProductKind,
    onForceAdd: () -> Unit,
    onDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onForceAdd) {
            Text("Force Add")
        }
        TextButton(onClick = onDetails) {
            Text("Details")
        }
    }
}
