package de.curlybracket.grocery.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionHandler(
    onGranted: @Composable () -> Unit,
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
                text = { Text("The camera is needed to scan barcodes. Please grant camera access.") },
                confirmButton = {
                    TextButton(onClick = { permissionState.launchPermissionRequest() }) { Text("Grant") }
                },
                dismissButton = {
                    TextButton(onClick = { /* close bottom sheet */ }) { Text("Cancel") }
                },
            )
        }

        rationaleShown -> {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Camera Permission Required") },
                text = { Text("Camera access was denied. Open Settings to grant it.") },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) { Text("Open Settings") }
                },
            )
        }

        else -> {
            LaunchedEffect(Unit) { permissionState.launchPermissionRequest() }
        }
    }
}
