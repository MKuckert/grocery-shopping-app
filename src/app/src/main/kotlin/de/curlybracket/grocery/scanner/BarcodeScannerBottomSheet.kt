package de.curlybracket.grocery.scanner

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.curlybracket.grocery.BuildConfig
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerBottomSheet(
    mode: ScannerMode,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onResult: (ScanResult) -> Unit,
    processor: ScannerProcessor,
) {
    if (!isOpen) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var scannerState by remember { mutableStateOf<ScannerState>(ScannerState.Scanning) }
    var isProcessing by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
        processor.scanResultFlow.collect { result ->
            isProcessing = false
            onResult(result)
            when (result) {
                is ScanResult.Hit, is ScanResult.Restored -> {
                    scannerState = ScannerState.Scanning
                }
                is ScanResult.Miss -> {
                    // State transition handled by openFoodFactsResultFlow
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        processor.openFoodFactsResultFlow.collect { lookupResult ->
            scannerState = ScannerState.CaptureRequired(
                barcode = lookupResult.barcode,
                prefillName = lookupResult.prefillName,
                photoPath = null,
            )
        }
    }

    val analyzer = remember {
        BarcodeAnalyzer { barcode ->
            if (!isProcessing) {
                isProcessing = true
                scope.launch { processor.processScan(barcode, mode) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            cleanupScannerPhotos(context)
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        CameraPermissionHandler {
            when (val state = scannerState) {
                is ScannerState.Scanning -> {
                    ScanningContent(
                        analyzer = analyzer,
                        imageCapture = imageCapture,
                        isProcessing = isProcessing,
                        onDismiss = {
                            cleanupScannerPhotos(context)
                            onDismiss()
                        },
                    )
                }

                is ScannerState.CaptureRequired -> {
                    CaptureRequiredContent(
                        state = state,
                        imageCapture = imageCapture,
                        onNameChange = { scannerState = state.copy(prefillName = it) },
                        onPhotoCapture = { path -> scannerState = state.copy(photoPath = path) },
                        onSave = {
                            scope.launch {
                                isProcessing = true
                                val newProduct = processor.createNewProduct(
                                    context = context,
                                    barcode = state.barcode,
                                    productName = state.prefillName,
                                    householdId = mode.householdId,
                                    photoPath = state.photoPath,
                                )
                                isProcessing = false
                                if (newProduct != null) {
                                    onResult(ScanResult.Hit(newProduct))
                                }
                                scannerState = ScannerState.Scanning
                            }
                        },
                        onCancel = { scannerState = ScannerState.Scanning },
                        isProcessing = isProcessing,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningContent(
    analyzer: BarcodeAnalyzer,
    imageCapture: ImageCapture,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Scan Barcode", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close scanner")
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview(
                analyzer = analyzer,
                imageCapture = imageCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            if (isProcessing) {
                CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CaptureRequiredContent(
    state: ScannerState.CaptureRequired,
    imageCapture: ImageCapture,
    onNameChange: (String) -> Unit,
    onPhotoCapture: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    isProcessing: Boolean,
) {
    val context = LocalContext.current
    val saveEnabled = state.prefillName.isNotBlank() &&
        (state.prefillName != "Unknown Item" || state.photoPath != null) &&
        !isProcessing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New Product", style = MaterialTheme.typography.titleMedium)
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        Text(
            text = "Barcode: ${state.barcode}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.prefillName,
            onValueChange = onNameChange,
            label = { Text("Product name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        val noopAnalyzer = remember { BarcodeAnalyzer { } }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview(
                analyzer = noopAnalyzer,
                imageCapture = imageCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            )

            IconButton(
                onClick = { capturePhoto(context, imageCapture, onPhotoCapture) },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Icon(
                    imageVector = Icons.Filled.Camera,
                    contentDescription = "Capture photo",
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        if (state.photoPath != null) {
            Text(
                text = "Photo captured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
            ) {
                Text("Save")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoCapture: (String) -> Unit,
) {
    val photoFile = File(
        context.cacheDir.resolve("scanner_photos").also { it.mkdirs() },
        "scan_${System.currentTimeMillis()}.jpg",
    )
    FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        photoFile,
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onPhotoCapture(photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                // Silently ignore; user can retry
            }
        },
    )
}

private fun cleanupScannerPhotos(context: Context) {
    context.cacheDir.resolve("scanner_photos").deleteRecursively()
}
