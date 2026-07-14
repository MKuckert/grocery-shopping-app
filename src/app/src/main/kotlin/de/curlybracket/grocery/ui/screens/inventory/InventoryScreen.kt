package de.curlybracket.grocery.ui.screens.inventory

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.curlybracket.grocery.R
import de.curlybracket.grocery.domain.model.GroupWithProducts
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.scanner.BarcodeScannerBottomSheet
import de.curlybracket.grocery.scanner.ScanResult
import de.curlybracket.grocery.scanner.ScannerMode
import de.curlybracket.grocery.scanner.ScannerProcessor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryScreen(
    onNavigateToDetail: (String) -> Unit,
    scannerProcessor: ScannerProcessor,
    deletedProductIdFlow: StateFlow<String?>,
    onDeletedProductConsumed: () -> Unit,
) {
    val viewModel: InventoryViewModel = hiltViewModel()
    val groupsWithProducts by viewModel.groupsWithProducts.collectAsStateWithLifecycle()
    val householdId by viewModel.householdIdFlow.collectAsStateWithLifecycle()
    val deletedProductId by deletedProductIdFlow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showScanner by remember { mutableStateOf(false) }

    val undoLabel = stringResource(R.string.action_undo)
    val productNameFallback = stringResource(R.string.inventory_product_name_fallback)
    val msgRemaining = stringResource(R.string.inventory_snackbar_remaining)
    val msgRestored = stringResource(R.string.inventory_snackbar_restored)
    val msgBarcodeLinked = stringResource(R.string.inventory_snackbar_barcode_linked)

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { productId ->
            onNavigateToDetail(productId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && msg.actionLabel == undoLabel) {
                viewModel.restoreProduct(msg.productId)
            } else if (result == SnackbarResult.ActionPerformed) {
                onNavigateToDetail(msg.productId)
            }
        }
    }

    LaunchedEffect(deletedProductId) {
        val pid = deletedProductId ?: return@LaunchedEffect
        onDeletedProductConsumed()
        val groupsSnapshot = viewModel.groupsWithProducts.value
        val productName = groupsSnapshot.flatMap { it.products }.find { it.id == pid }?.name ?: productNameFallback
        viewModel.showDeletedProductUndo(pid, productName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                actions = {
                    TextButton(onClick = { viewModel.startShopping() }) {
                        Text(stringResource(R.string.inventory_btn_start_shopping))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showScanner = true }) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.inventory_fab_cd_scan_barcode),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            groupsWithProducts.forEach { group ->
                stickyHeader(key = group.groupName) {
                    GroupHeader(group = group)
                }
                items(
                    items = group.products,
                    key = { it.id },
                ) { product ->
                    ProductRow(
                        product = product,
                        modifier = Modifier.animateItem(),
                        onSwipe = { viewModel.decrementStock(product) },
                        onTap = { viewModel.navigateToDetail(product) },
                    )
                }
            }
        }
    }

    val hid = householdId
    if (showScanner && hid != null) {
        BarcodeScannerBottomSheet(
            mode = ScannerMode.DecrementStock(hid),
            isOpen = showScanner,
            onDismiss = { showScanner = false },
            onResult = { result ->
                when (result) {
                    is ScanResult.Hit -> scope.launch {
                        snackbarHostState.showSnackbar(
                            msgRemaining.format(result.product.name, result.product.currentStock),
                        )
                    }
                    is ScanResult.Restored -> scope.launch {
                        snackbarHostState.showSnackbar(msgRestored.format(result.product.name))
                    }
                    is ScanResult.Linked -> scope.launch {
                        snackbarHostState.showSnackbar(msgBarcodeLinked.format(result.product.name))
                    }
                    is ScanResult.Miss -> { /* CaptureRequired overlay handles this in-sheet */ }
                }
            },
            processor = scannerProcessor,
        )
    }
}

@Composable
private fun GroupHeader(group: GroupWithProducts) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = group.groupName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ProductRow(
    product: ProductKind,
    modifier: Modifier = Modifier,
    onSwipe: () -> Unit,
    onTap: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var offsetX by remember(product.id) { mutableFloatStateOf(0f) }
    val swipeThreshold = -150f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(product.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX <= swipeThreshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSwipe()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(swipeThreshold, 0f)
                    },
                )
            },
    ) {
        Surface(
            onClick = onTap,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = product.currentStock.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (product.currentStock == 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
