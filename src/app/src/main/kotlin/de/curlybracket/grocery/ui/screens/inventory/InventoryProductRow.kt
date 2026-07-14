package de.curlybracket.grocery.ui.screens.inventory

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.curlybracket.grocery.domain.model.ProductKind
import kotlin.math.roundToInt

@Composable
internal fun ProductRow(
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
