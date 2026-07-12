package de.curlybracket.grocery.ui.screens.unloading

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.curlybracket.grocery.R
import de.curlybracket.grocery.domain.model.ProductKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnloadingScreen() {
    val viewModel: UnloadingViewModel = hiltViewModel()

    val items by viewModel.items.collectAsStateWithLifecycle()
    val showWarningDialog by viewModel.showWarningDialog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg.text)
        }
    }

    if (showWarningDialog) {
        val openCount = items.count { it.unloadOpen }
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = { Text(stringResource(R.string.unloading_dialog_title)) },
            text = { Text(stringResource(R.string.unloading_dialog_message, openCount)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSubmit() }) {
                    Text(stringResource(R.string.unloading_dialog_btn_submit_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialog() }) {
                    Text(stringResource(R.string.unloading_dialog_btn_go_back))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.unloading_title)) },
                actions = {
                    TextButton(onClick = { viewModel.requestSubmit() }) {
                        Text(stringResource(R.string.unloading_btn_submit_unloading))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(
                items = items,
                key = { it.id },
            ) { product ->
                UnloadingRow(
                    product = product,
                    onCheckedChange = { checked -> viewModel.toggleUnloadOpen(product, checked) },
                    onIncrement = { viewModel.incrementPending(product) },
                    onDecrement = { viewModel.decrementPending(product) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun UnloadingRow(
    product: ProductKind,
    onCheckedChange: (Boolean) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLocked = !product.unloadOpen
    val contentAlpha = if (isLocked) 0.38f else 1f

    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = contentAlpha)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isLocked,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${product.currentStock} + ${product.pendingStock} = ${product.currentStock + product.pendingStock}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = onDecrement,
                enabled = product.unloadOpen,
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.unloading_cd_decrease_item, product.name),
                )
            }
            IconButton(
                onClick = onIncrement,
                enabled = product.unloadOpen,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.unloading_cd_increase_item, product.name),
                )
            }
        }
    }
}
