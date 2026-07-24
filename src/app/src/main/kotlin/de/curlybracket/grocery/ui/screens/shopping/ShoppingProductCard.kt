package de.curlybracket.grocery.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.R
import de.curlybracket.grocery.domain.model.ProductKind

@Composable
internal fun ShoppingRow(
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
                    contentDescription = stringResource(R.string.shopping_cd_decrease_item, product.name),
                )
            }
            IconButton(onClick = onIncrement) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.shopping_cd_increase_item, product.name),
                )
            }
        }
    }
}

@Composable
internal fun SearchResultCard(
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
            Text(stringResource(R.string.shopping_btn_force_add))
        }
        TextButton(onClick = onDetails) {
            Text(stringResource(R.string.shopping_btn_details))
        }
    }
}

private val previewProduct = ProductKind(
    id = "1",
    householdId = "h1",
    groupId = null,
    name = "Milk",
    currentStock = 1,
    minimumStock = 2,
    quantityToBuy = 2,
    pendingStock = 1,
    imagePath = null,
    unloadOpen = false,
    deletedAt = null,
)

@Preview(showBackground = true)
@Composable
private fun ShoppingRowPreview() {
    ShoppingRow(
        product = previewProduct,
        isStruckThrough = false,
        onRowTap = {},
        onIncrement = {},
        onDecrement = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchResultCardPreview() {
    SearchResultCard(
        product = previewProduct,
        onForceAdd = {},
        onDetails = {},
    )
}
