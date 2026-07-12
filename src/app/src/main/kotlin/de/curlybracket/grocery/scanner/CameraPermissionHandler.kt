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
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import de.curlybracket.grocery.R

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
                title = { Text(stringResource(R.string.permission_dialog_camera_required_title)) },
                text = { Text(stringResource(R.string.permission_dialog_camera_required_message)) },
                confirmButton = {
                    TextButton(onClick = { permissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.permission_btn_grant))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { /* close bottom sheet */ }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        rationaleShown -> {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.permission_dialog_camera_denied_title)) },
                text = { Text(stringResource(R.string.permission_dialog_camera_denied_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) { Text(stringResource(R.string.permission_btn_open_settings)) }
                },
            )
        }

        else -> {
            LaunchedEffect(Unit) { permissionState.launchPermissionRequest() }
        }
    }
}
