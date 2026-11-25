package tcg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tcg.app.catalog.GitHubCatalogSource
import tcg.app.catalog.summary
import tcg.domain.catalog.CatalogUpdater

class MainActivity : ComponentActivity() {
    private var catalogStatus by mutableStateOf("")
    private var updating by mutableStateOf(false)

    private val catalogUpdater by lazy {
        CatalogUpdater(
            source = GitHubCatalogSource(BuildConfig.CATALOG_REPO, BuildConfig.CATALOG_TOKEN),
            store = (application as TcgApplication).database.catalogStore(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) updateCatalog()
        setContent {
            MaterialTheme {
                CatalogHome(catalogStatus, updating, onUpdateCatalog = ::updateCatalog)
            }
        }
    }

    private fun updateCatalog() {
        if (updating) return
        updating = true
        catalogStatus = "Checking for a newer catalog…"
        lifecycleScope.launch {
            try {
                catalogStatus = catalogUpdater.update().summary()
            } finally {
                updating = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogHome(status: String, updating: Boolean, onUpdateCatalog: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TCG Collector") },
                actions = {
                    TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Update catalog") },
                            enabled = !updating,
                            onClick = {
                                menuOpen = false
                                onUpdateCatalog()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
            Text(status)
        }
    }
}
