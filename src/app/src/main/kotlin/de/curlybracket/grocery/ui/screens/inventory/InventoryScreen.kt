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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.curlybracket.grocery.domain.model.GroupWithProducts
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.ui.navigation.Route
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryScreen(navController: NavController) {
    val viewModel: InventoryViewModel = hiltViewModel()
    val groupsWithProducts by viewModel.groupsWithProducts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showScanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { productId ->
            navController.navigate(Route.Detail(productId).path)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            if (msg.productId.isEmpty()) {
                snackbarHostState.showSnackbar(msg.productName)
            } else {
                val result = snackbarHostState.showSnackbar(
                    message = "${msg.productName}: ${msg.newStock} remaining",
                    actionLabel = "Details",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    navController.navigate(Route.Detail(msg.productId).path)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grocery") },
                actions = {
                    TextButton(onClick = { viewModel.startShopping() }) {
                        Text("Start Shopping")
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

    if (showScanner) {
        InventoryScannerBottomSheet(
            onDismiss = { showScanner = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryScannerBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Scanner — available in Task 11",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
