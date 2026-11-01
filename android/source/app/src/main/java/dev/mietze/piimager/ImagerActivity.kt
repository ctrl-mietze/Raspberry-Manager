package dev.mietze.piimager

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.mietze.piimager.ui.PiImagerApp

/** Original native Pi Imager v0.2 wizard; kept intact inside the manager. */
class ImagerActivity : ComponentActivity() {
    private val viewModel: ImagerViewModel by viewModels()
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "custom-image.img"
        viewModel.selectLocal(uri, name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) {
            intent.getStringExtra(LibraryWire.EXTRA_IMAGE)?.let { metadata ->
                runCatching { LibraryWire.decode(metadata) }.onSuccess(viewModel::importLibraryItem)
                    .onFailure { android.widget.Toast.makeText(this, it.message, android.widget.Toast.LENGTH_LONG).show() }
            }
        }
        setContent {
            PiImagerApp(
                viewModel = viewModel,
                onPickLocalImage = {
                    pickImage.launch(arrayOf("application/octet-stream", "application/zip", "application/x-xz", "application/gzip", "*/*"))
                }
            )
        }
    }
}
