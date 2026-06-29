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

    val image = imageProxy.image
    if (image == null) {
      Log.w(TAG, "Image proxy image is null")
      imageProxy.close()
      return
    }

    try {
      val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
      
      // Use addOnSuccessListener for async processing
      BarcodeScanning.getClient().process(inputImage)
        .addOnSuccessListener { barcodes ->
          processDetectionResult(barcodes, currentTime)
        }
        .addOnFailureListener { exception ->
          Log.e(TAG, "Barcode detection failed", exception)
        }
        .addOnCompleteListener {
          // Close imageProxy only after ML Kit completes processing
          imageProxy.close()
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error creating InputImage", e)
      imageProxy.close()
    }
  }

  private fun processDetectionResult(barcodes: Any?, currentTime: Long) {
    try {
      val detectionTime = System.currentTimeMillis()
      
      // Use reflection to safely access the barcode list
      @Suppress("UNCHECKED_CAST")
      val barcodeList = barcodes as? List<Any> ?: return
      
      Log.d(TAG, "Barcode detection result: ${barcodeList.size} barcodes found")
      
      if (barcodeList.isNotEmpty()) {
        val barcode = barcodeList[0]
        
        // Use reflection to get rawValue property
        val rawValue = try {
          val method = barcode.javaClass.getMethod("getRawValue")
          method.invoke(barcode) as? String
        } catch (e: Exception) {
          Log.w(TAG, "Could not get rawValue from barcode", e)
          null
        }
        
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
    } catch (e: Exception) {
      Log.e(TAG, "Error processing detection result", e)
    }
  }
}
