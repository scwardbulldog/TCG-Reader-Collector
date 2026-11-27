package tcg.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The app's one "something is loading" cue: an indeterminate bar, shown while a lookup, search or load is in flight. */
@Composable
internal fun LoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(modifier.fillMaxWidth())
}
