package de.curlybracket.grocery.scanner

import android.util.Log
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
    private const val TAG = "BarcodeAnalyzer"
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
      val image = imageProxy.image ?: return
      val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
      
      // Use addOnSuccessListener for async processing
      BarcodeScanning.getClient().process(inputImage)
        .addOnSuccessListener { barcodes ->
          val detectionTime = System.currentTimeMillis()
          Log.d(TAG, "Barcode detection result: ${barcodes.size} barcodes found")
          
          if (barcodes.isNotEmpty()) {
            val barcode = barcodes[0]
            val rawValue = barcode.rawValue
            Log.d(TAG, "Detected barcode: $rawValue")
            
            if (rawValue != null) {
              val locks = barcodeLocksRef.get()
              val lastDetectedTime = locks[rawValue]

              // Cooldown gate: only fire if barcode not detected within cooldown window
              if (lastDetectedTime == null || detectionTime - lastDetectedTime >= COOLDOWN_DURATION_MS) {
                // Update locks with new detection time
                val updatedLocks = locks.toMutableMap()
                updatedLocks[rawValue] = detectionTime
                barcodeLocksRef.set(updatedLocks)

                // Fire the callback
                lastDetectionTime = detectionTime
                Log.d(TAG, "Firing callback for barcode: $rawValue")
                onBarcodeDetected(rawValue)
              } else {
                Log.d(TAG, "Barcode in cooldown: $rawValue (${detectionTime - lastDetectedTime}ms ago)")
              }
            }
          } else {
            Log.d(TAG, "No barcodes detected in frame")
          }
        }
        .addOnFailureListener { exception ->
          Log.e(TAG, "Barcode detection failed", exception)
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error in analyze", e)
    } finally {
      imageProxy.close()
    }
  }
}
