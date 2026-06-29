package de.curlybracket.grocery.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.curlybracket.grocery.scanner.internal.CaptureRequiredOverlay
import de.curlybracket.grocery.scanner.internal.ScanningOverlay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerBottomSheet(
  mode: ScannerMode,
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onResult: (ScanResult) -> Unit,
  processor: ScannerProcessor,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState()

  var scannerState by remember { mutableStateOf<ScannerState>(ScannerState.Scanning) }
  val cameraProvider = remember { mutableStateOf<androidx.camera.lifecycle.ProcessCameraProvider?>(null) }
  val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

  // Listen for Open Food Facts lookup results
  LaunchedEffect(Unit) {
    processor.openFoodFactsResultFlow.collect { result ->
      // Update the CaptureRequired state with the prefilled name from the API
      val currentState = scannerState
      if (currentState is ScannerState.CaptureRequired && currentState.barcode == result.barcode) {
        scannerState = currentState.copy(prefillName = result.prefillName)
      }
    }
  }

  // Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (!isGranted) {
      Log.w("Scanner", "Camera permission denied")
      onDismiss()
    }
  }

  // Check camera permission on sheet open
  LaunchedEffect(isOpen) {
    if (isOpen) {
      val permission = Manifest.permission.CAMERA
      if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
        cameraPermissionLauncher.launch(permission)
      }
    }
  }

  // Listen for scan results
  LaunchedEffect(Unit) {
    processor.scanResultFlow.collect { result ->
      when (result) {
        is ScanResult.Hit -> {
          onResult(result)
          scannerState = ScannerState.Scanning
        }

        is ScanResult.Restored -> {
          onResult(result)
          scannerState = ScannerState.Scanning
        }

        is ScanResult.Miss -> {
          // Open Food Facts lookup is now performed in processScan
          // We wait for the next state update to populate prefillName
          // The processor calls onOpenFoodFactsLookupComplete which sets state in ScanningState
          // For now, default to "Unknown Item" until the state is updated by the processor
          scannerState = ScannerState.CaptureRequired(
            barcode = result.barcode,
            prefillName = "Unknown Item",  // Will be updated by processor
            photoPath = null
          )
        }
      }
    }
  }

  // Cleanup temp directory on dismiss
  DisposableEffect(isOpen) {
    onDispose {
      if (!isOpen) {
        try {
          context.cacheDir.resolve("scanner_photos").deleteRecursively()
        } catch (e: Exception) {
          Log.w("Scanner", "Failed to cleanup scanner photos", e)
        }
      }
    }
  }

  if (isOpen) {
    ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        when (scannerState) {
          is ScannerState.Scanning -> {
            ScanningState(
              mode = mode,
              processor = processor,
              onScannerStateChange = { newState -> scannerState = newState }
            )
          }

          is ScannerState.CaptureRequired -> {
            val captureState = scannerState as ScannerState.CaptureRequired
            CaptureRequiredState(
              barcode = captureState.barcode,
              prefillName = captureState.prefillName,
              photoPath = captureState.photoPath,
              mode = mode,
              processor = processor,
              context = context,
              onStateChange = { newState -> scannerState = newState },
              onResult = onResult,
              onCancel = { scannerState = ScannerState.Scanning }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ScanningState(
  mode: ScannerMode,
  processor: ScannerProcessor,
  onScannerStateChange: (ScannerState) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Create analyzer once
  val barcodeAnalyzer = remember {
    BarcodeAnalyzer { barcode ->
      scope.launch {
        processor.processScan(barcode, mode)
      }
    }
  }

  // Wrap analyzer as a lambda for the composable
  val analyzerLambda: (androidx.camera.core.ImageProxy) -> Unit = remember(barcodeAnalyzer) {
    { imageProxy ->
      barcodeAnalyzer.analyze(imageProxy)
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    CameraPreviewComposable(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      imageAnalyzer = analyzerLambda
    )
    ScanningOverlay(modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp))
  }
}

@Composable
private fun CaptureRequiredState(
  barcode: String,
  prefillName: String,
  photoPath: String?,
  mode: ScannerMode,
  processor: ScannerProcessor,
  context: android.content.Context,
  onStateChange: (ScannerState) -> Unit,
  onResult: (ScanResult) -> Unit,
  onCancel: () -> Unit
) {
  val scope = rememberCoroutineScope()
  var productName by remember { mutableStateOf(prefillName) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("New Product", style = MaterialTheme.typography.headlineSmall)

    TextField(
      value = productName,
      onValueChange = { productName = it },
      label = { Text("Product Name") },
      modifier = Modifier.fillMaxWidth()
    )

    if (photoPath != null) {
      val bitmap = BitmapFactory.decodeFile(photoPath)
      if (bitmap != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured product photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onCancel,
        modifier = Modifier.weight(1f)
      ) {
        Text("Cancel")
      }

      if (productName != "Unknown Item" || photoPath != null) {
        Button(
          onClick = {
            scope.launch {
              val householdId = when (mode) {
                is ScannerMode.Inventory -> mode.householdId
                is ScannerMode.Shopping -> mode.householdId
              }

              // Move photo to permanent storage if captured
              var finalPhotoPath: String? = null
              if (photoPath != null) {
                val sourceFile = File(photoPath)
                val destDir = context.filesDir.resolve("product_images").also { it.mkdirs() }
                val destFile = File(destDir, "prod_${System.currentTimeMillis()}.jpg")
                sourceFile.copyTo(destFile, overwrite = true)
                finalPhotoPath = destFile.absolutePath
              }

              // Create the new product
              // This will call repository.ensureUnsortedGroup -> repository.createProductKind -> emit Hit
              // For now, we'll let the processor handle it via commitNewProduct integration
              // (Note: ScannerProcessor needs to be extended with commitNewProduct logic)
            }
          },
          modifier = Modifier.weight(1f)
        ) {
          Text("Save")
        }
      }
    }
  }
}
