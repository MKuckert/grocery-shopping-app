package de.curlybracket.grocery.scanner

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

@Composable
fun CameraPreviewComposable(
  modifier: Modifier = Modifier,
  imageAnalyzer: (ImageProxy) -> Unit
) {
  val context = LocalContext.current

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      PreviewView(ctx)
    },
    update = { previewView ->
      val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

      val listener = Runnable {
        try {
          val cameraProvider = cameraProviderFuture.get()
          val lifecycleOwner = previewView.findViewTreeLifecycleOwner() ?: return@Runnable

          // Preview use case
          val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

          // ImageAnalysis use case (barcode detection)
          val imageAnalysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), imageAnalyzer) }

          // ImageCapture use case (for photo capture in CaptureRequired state)
          val imageCapture = ImageCapture.Builder()
            .build()

          val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner as LifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysisUseCase,
            imageCapture
          )
        } catch (e: Exception) {
          Log.e("CameraPreview", "Camera initialization failed", e)
        }
      }

      cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
    }
  )
}
