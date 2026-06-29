package de.curlybracket.grocery.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicReference

class BarcodeAnalyzer(
  private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

  // Throttle timeouts (ms)
  companion object {
    private const val COOLDOWN_DURATION_MS = 3000L
    private const val CLEAR_FRAME_THRESHOLD_MS = 400L
  }

  // Thread-safe lock map: barcode -> last detection timestamp
  private val barcodeLocksRef = AtomicReference(mapOf<String, Long>())

  // Last timestamp a barcode was detected
  private var lastDetectionTime = System.currentTimeMillis()

  override fun analyze(imageProxy: ImageProxy) {
    val currentTime = System.currentTimeMillis()

    // Clear-frame gate: if no barcode detected for 400 ms, reset all locks
    if (currentTime - lastDetectionTime > CLEAR_FRAME_THRESHOLD_MS) {
      barcodeLocksRef.set(emptyMap())
    }

    try {
      val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
      val barcodes = BarcodeScanning.getClient().process(inputImage).result

      if (barcodes.isNotEmpty()) {
        val barcode = barcodes[0]
        val rawValue = barcode.rawValue ?: return

        val locks = barcodeLocksRef.get()
        val lastDetectedTime = locks[rawValue]

        // Cooldown gate: only fire if barcode not detected within cooldown window
        if (lastDetectedTime == null || currentTime - lastDetectedTime >= COOLDOWN_DURATION_MS) {
          // Update locks with new detection time
          val updatedLocks = locks.toMutableMap()
          updatedLocks[rawValue] = currentTime
          barcodeLocksRef.set(updatedLocks)

          // Fire the callback
          lastDetectionTime = currentTime
          onBarcodeDetected(rawValue)
        }
      }
    } finally {
      imageProxy.close()
    }
  }
}
