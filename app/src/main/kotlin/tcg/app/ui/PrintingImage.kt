package tcg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private const val CARD_ASPECT_RATIO = 63f / 88f

/**
 * A Printing's card image, loaded from its official URL on first view and cached on the phone.
 * Shows a plain placeholder while loading and when there is no URL or the fetch fails.
 * Size it with [modifier] (width or height; the card aspect ratio is applied here).
 */
@Composable
fun PrintingImage(imageUrl: String?, modifier: Modifier = Modifier) {
    val url = imageUrl?.takeIf { it.isNotBlank() }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Text(
                "No image",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
