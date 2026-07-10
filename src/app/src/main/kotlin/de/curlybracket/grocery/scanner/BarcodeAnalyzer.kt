package de.curlybracket.grocery.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicReference

private const val COOLDOWN_MS = 3_000L
private const val CLEAR_FRAME_MS = 400L

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private data class ThrottleState(
        val cooldowns: Map<String, Long> = emptyMap(),
        val lastDetectionMs: Long = 0L,
    )

    private val state = AtomicReference(ThrottleState())
    private val scanner = BarcodeScanning.getClient()

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val nowMs = System.currentTimeMillis()
                val rawValue = barcodes.firstOrNull()?.rawValue

                state.getAndUpdate { current ->
                    if (rawValue == null) {
                        // Clear-frame gate: no barcode detected — reset cooldowns if gap exceeded
                        if (nowMs - current.lastDetectionMs >= CLEAR_FRAME_MS) {
                            current.copy(cooldowns = emptyMap())
                        } else {
                            current
                        }
                    } else {
                        val lastSeen = current.cooldowns[rawValue] ?: 0L
                        val expired = nowMs - lastSeen >= COOLDOWN_MS
                        if (expired) {
                            onBarcodeDetected(rawValue)
                            current.copy(
                                cooldowns = current.cooldowns + (rawValue to nowMs),
                                lastDetectionMs = nowMs,
                            )
                        } else {
                            current.copy(lastDetectionMs = nowMs)
                        }
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
