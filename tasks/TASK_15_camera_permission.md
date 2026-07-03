# Task 15: Camera Permission Handling

> **Depends on:** Task 6 (Navigation)
> **Status:** [x]

---

## Description

The `CAMERA` permission is already declared in `AndroidManifest.xml`. Runtime permission must be requested before opening the `BarcodeScannerBottomSheet`. Implement a reusable `CameraPermissionHandler` composable:

```kotlin
@Composable
fun CameraPermissionHandler(
  onGranted: @Composable () -> Unit
) {
  val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
  var rationaleShown by remember { mutableStateOf(false) }

  when {
    permissionState.status.isGranted -> onGranted()

    permissionState.status.shouldShowRationale -> {
      rationaleShown = true
      AlertDialog(
        onDismissRequest = {},
        title = { Text("Camera Required") },
        text  = { Text("The camera is needed to scan barcodes. Please grant camera access.") },
        confirmButton = {
          TextButton(onClick = { permissionState.launchPermissionRequest() }) { Text("Grant") }
        },
        dismissButton = {
          TextButton(onClick = { /* close bottom sheet */ }) { Text("Cancel") }
        }
      )
    }

    rationaleShown -> {
      // shouldShowRationale is now false after having been true → permanently denied
      val context = LocalContext.current
      AlertDialog(
        onDismissRequest = {},
        title = { Text("Camera Permission Required") },
        text  = { Text("Camera access was denied. Open Settings to grant it.") },
        confirmButton = {
          TextButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
          }) { Text("Open Settings") }
        }
      )
    }

    else -> {
      // Never requested yet — launch request immediately
      LaunchedEffect(Unit) { permissionState.launchPermissionRequest() }
    }
  }
}
```

**Usage:** Wrap `BarcodeScannerBottomSheet` content with `CameraPermissionHandler { /* scanner content */ }`.

### Dependency

Add `accompanist-permissions` if not already in `libs.versions.toml`:

```toml
[versions]
accompanist = "0.36.0"

[libraries]
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }
```

Add `implementation(libs.accompanist.permissions)` to `app/build.gradle.kts`.

---

## Review Criteria

- Camera is only opened after permission is granted.
- First denial shows rationale dialog with "Grant" button.
- Permanent denial shows settings redirect dialog.
- No crash on cold start with permission denied.
