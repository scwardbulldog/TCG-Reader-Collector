package tcg.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * The Document Scanner's filter mode for Card photos. FULL adds the scanner's clean-up filters, which can wash
 * out foil and holo; BASE only crops and straightens. Kept as one switch until it is checked on a foil card.
 */
private const val CARD_PHOTO_SCANNER_MODE = GmsDocumentScannerOptions.SCANNER_MODE_BASE

private val scannerOptions = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(false)
    .setPageLimit(1)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
    .setScannerMode(CARD_PHOTO_SCANNER_MODE)
    .build()

/**
 * Returns a function that opens the Document Scanner for one page and hands [onCaptured] the picture's location.
 * If the scanner is unavailable (no Play Services, say) it shows a short message and nothing else happens:
 * the collector carries on without a photo.
 */
@Composable
fun rememberCardPhotoCapture(onCaptured: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onCaptured)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri?.let { latest(it.toString()) }
        }
    }
    return {
        val activity = context.findActivity()
        if (activity == null) {
            context.tellScannerUnavailable()
        } else {
            GmsDocumentScanning.getClient(scannerOptions).getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { context.tellScannerUnavailable() }
        }
    }
}

/** An info icon that opens three tips for a good Card photo. */
@Composable
fun CardPhotoTips() {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.Info, "Photo tips") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Tips for a good photo", style = MaterialTheme.typography.titleSmall)
            Text("• Lay the card on a plain, dark, matte surface.")
            Text("• Avoid a direct overhead light, which glares off the card.")
            Text("• Tilt the phone slightly, so foil does not wash out.")
        }
    }
}

private fun Context.tellScannerUnavailable() =
    Toast.makeText(this, "The photo scanner is not available on this phone. Carrying on without a photo.", Toast.LENGTH_LONG).show()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
