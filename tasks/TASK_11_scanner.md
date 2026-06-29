# Task 11: Barcode Scanner Infrastructure (`scanner/`)

> **Depends on:** Task 4, Task 12 (Open Food Facts), Task 13 (Audio)
> **Status:** [x]

---

## Description

Create reusable scanner components consumed by InventoryScreen, ShoppingScreen, and the new-item onboarding pipeline.

---

### `scanner/CameraPreviewComposable.kt`

- Wraps `PreviewView` (CameraX) in an `AndroidView` composable.
- Sets up `ProcessCameraProvider`, `Preview` use case, `ImageAnalysis` use case, and `ImageCapture` use case (needed for photo capture in `CaptureRequired` state).
- The `ImageAnalysis` analyzer is injected as a lambda `(ImageProxy) -> Unit`.

**Lifecycle binding — exact pattern:**

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
DisposableEffect(lifecycleOwner) {
  val listener = Runnable {
    val cameraProvider = cameraProviderFuture.get()
    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis, imageCapture)
  }
  cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
  onDispose { cameraProviderFuture.get().unbindAll() }
}
```

`ProcessCameraProvider` is created once via `remember`; `DisposableEffect` binds/unbinds on lifecycle events. Never recreated on recomposition.

---

### `scanner/BarcodeAnalyzer.kt`

- `ImageAnalysis.Analyzer` implementation using `BarcodeScanning.getClient()`.
- On successful detection, invokes `onBarcodeDetected(rawValue: String)` callback.
- Implements the dual-gate throttle per spec §3.1:
  - **Cooldown Gate:** once a barcode `rawValue` is detected, lock that string for 3 seconds via a `HashMap<String, Long>`.
  - **Clear-Frame Gate:** if no barcode detected for 400 ms (tracked by timestamp), reset all locks immediately.
- Thread-safe via `AtomicReference` for the lock map + timestamps.
- Put those throttle timeouts in easily adjustable constants.

---

### `scanner/ScannerProcessor.kt`

Orchestrates what happens after a barcode is detected. Accepts a sealed `ScannerMode`:

```kotlin
sealed class ScannerMode {
  data class Inventory(val householdId: String) : ScannerMode()
  data class Shopping(val householdId: String) : ScannerMode()
}
```

**Flow:**

1. `repository.findByBarcode(rawValue, householdId)` — O(1) lookup.
2. **Hit:** play success beep; apply mode-specific mutation (decrement stock for Inventory / increment pending for Shopping); emit `ScanResult.Hit(product)`.
3. **Miss:** play failure boop; switch internal `ScannerState` to `CaptureRequired`; start Open Food Facts lookup (Task 12); emit `ScanResult.Miss(barcode)`.
4. **Soft-delete resurrection:** if found product has `deletedAt != null`, call `repository.restoreProductKind(id)`; play success beep; emit `ScanResult.Restored(product)`.

---

### `scanner/ScannerState.kt`

```kotlin
sealed class ScannerState {
  data object Scanning : ScannerState()
  data class CaptureRequired(
    val barcode: String,
    val prefillName: String,   // from Open Food Facts or "Unknown Item"
    val photoPath: String?
  ) : ScannerState()
}
```

---

### `scanner/BarcodeScannerBottomSheet.kt`

- Shared composable used by InventoryScreen and ShoppingScreen.
- Accepts `mode: ScannerMode` and `onResult: (ScanResult) -> Unit`.
- Internal `scannerState: MutableState<ScannerState>` managed via `remember { mutableStateOf(ScannerState.Scanning) }`.

**State machine (explicit transitions):**

```
Scanning
  ├─ barcode hit (local lookup) → play beep → apply DB mutation → show Snackbar → stay Scanning
  ├─ barcode miss (no local match) → play boop → switch to CaptureRequired(barcode, prefillName="…", photoPath=null)
  └─ barcode hit (soft-deleted) → restore → play beep → show "Restored" Snackbar → stay Scanning

CaptureRequired(barcode, prefillName, photoPath=null)
  ├─ user edits name field → update prefillName in state
  ├─ user taps shutter button → capture photo → CaptureRequired(barcode, prefillName, photoPath="/cache/…")
  ├─ user taps Save (photoPath required if prefillName=="Unknown Item") → commitNewProduct → Scanning
  └─ user taps Cancel → Scanning
```

---

### `filepaths.xml`

The manifest already declares a `FileProvider`. The existing `filepaths.xml` must be updated. The final file must be:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
  <external-files-path name="pictures" path="Pictures/" />
  <files-path name="product_images" path="product_images/" />
  <cache-path name="scanner_photos" path="scanner_photos/" />
</paths>
```

- Without `<files-path>`: `FileProvider.getUriForFile()` throws `IllegalArgumentException` for permanent product images.
- Without `<cache-path>`: scanner photo URIs similarly fail.

---

### Photo capture file path generation

```kotlin
val photoFile = File(
  context.cacheDir.resolve("scanner_photos").also { it.mkdirs() },
  "scan_${System.currentTimeMillis()}.jpg"
)
val photoUri = FileProvider.getUriForFile(
  context,
  "${BuildConfig.APPLICATION_ID}.fileprovider",
  photoFile
)
```

After successful capture, store `photoFile.absolutePath` in `ScannerState.CaptureRequired.photoPath`.

**Directory cleanup:** On bottom sheet dismiss, call `context.cacheDir.resolve("scanner_photos").deleteRecursively()`.

---

### `commitNewProduct` flow (inside `ScannerProcessor`)

1. Call `repository.ensureUnsortedGroup(householdId)` to get group id.
2. If `photoPath != null`, move file from `cacheDir/scanner_photos/` to `filesDir/product_images/` (permanent storage).
3. Call `repository.createProductKind(householdId, name, groupId, minimumStock=1, barcodeNumber)`.
4. Emit `ScanResult.Hit` with the new product.

---

## Review Criteria

- `BarcodeAnalyzer` correctly implements both throttle gates (cooldown + clear-frame).
- `ScannerProcessor` handles hit / miss / soft-delete resurrection in all branches.
- Bottom sheet does not recreate `ProcessCameraProvider` on recomposition (`remember` + `DisposableEffect`).
- `filepaths.xml` contains all three path entries (existing `<external-files-path>` preserved).
- Photo capture uses `FileProvider`; temp photos cleaned up on sheet dismiss.
