package de.curlybracket.grocery.ui.screens.detail.util

import android.content.Context
import androidx.core.content.FileProvider
import de.curlybracket.grocery.BuildConfig
import java.io.File

internal fun getProductImageUri(context: Context, imagePath: String): android.net.Uri? {
  val file = File(imagePath)
  if (!file.exists()) return null
  return FileProvider.getUriForFile(
    context,
    "${BuildConfig.APPLICATION_ID}.fileprovider",
    file
  )
}
