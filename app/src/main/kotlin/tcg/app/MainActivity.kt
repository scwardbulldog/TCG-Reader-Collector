package tcg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tcg.app.catalog.GitHubCatalogSource
import tcg.app.catalog.summary
import tcg.app.storage.AppDatabase
import tcg.app.ui.CollectionActions
import tcg.app.ui.CollectionScreen
import tcg.app.ui.CollectionViewModel
import tcg.app.ui.PrintingDetailScreen
import tcg.app.ui.PrintingDetailViewModel
import tcg.app.ui.ScanSessionScreen
import tcg.app.ui.ScanViewModel
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.CatalogUpdater
import tcg.domain.collection.CollectionList
import tcg.domain.collection.LastKnownPrintings
import tcg.domain.collection.PrintingDetail

class MainActivity : ComponentActivity() {
    private var updating by mutableStateOf(false)
    private var catalogRevision by mutableIntStateOf(0)
    private val catalogMessages = SnackbarHostState()

    private val database get() = (application as TcgApplication).database

    private val catalogUpdater by lazy {
        CatalogUpdater(
            source = GitHubCatalogSource(BuildConfig.CATALOG_REPO, BuildConfig.CATALOG_TOKEN),
            store = database.catalogStore(),
            beforeReplace = LastKnownPrintings(database.collectionStore(), database.catalogStore())::remember,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) updateCatalog()
        setContent {
            MaterialTheme {
                CollectionApp(
                    database = database,
                    updatingCatalog = updating,
                    catalogRevision = catalogRevision,
                    snackbarHostState = catalogMessages,
                    onUpdateCatalog = ::updateCatalog,
                )
            }
        }
    }

    private fun updateCatalog() {
        if (updating) return
        updating = true
        lifecycleScope.launch {
            val summary = try {
                catalogUpdater.update().summary()
            } finally {
                updating = false
            }
            catalogRevision++
            catalogMessages.showSnackbar(summary)
        }
    }
}

private val printingSaver = listSaver<PrintingIdentifier?, String>(
    save = { it?.let { p -> listOf(p.game.name, p.language.name, p.sourceId) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else PrintingIdentifier(Game.valueOf(it[0]), Language.valueOf(it[1]), it[2]) },
)

@Composable
private fun CollectionApp(
    database: AppDatabase,
    updatingCatalog: Boolean,
    catalogRevision: Int,
    snackbarHostState: SnackbarHostState,
    onUpdateCatalog: () -> Unit,
) {
    var selected by rememberSaveable(stateSaver = printingSaver) { mutableStateOf<PrintingIdentifier?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalog = remember { database.catalogStore() }
    val collection = remember { database.collectionStore() }

    // Each stub is where a later slice (CSV, backup, restore) plugs in.
    fun comingSoon(feature: String) {
        scope.launch { snackbarHostState.showSnackbar("$feature is not available yet") }
    }

    if (scanning) {
        val scanViewModel = viewModel<ScanViewModel>(initializer = { ScanViewModel(catalog, collection) })
        val close = {
            scanViewModel.endSession()
            scanning = false
        }
        BackHandler(onBack = close)
        ScanSessionScreen(scanViewModel, onClose = close)
        return
    }

    val current = selected
    val viewModel = viewModel<CollectionViewModel>(initializer = { CollectionViewModel(CollectionList(collection, catalog)) })
    if (current == null) {
        CollectionScreen(
            viewModel = viewModel,
            actions = CollectionActions(
                onAdd = { scanning = true },
                onExportCsv = { comingSoon("CSV export") },
                onBackup = { comingSoon("Backup") },
                onRestore = { comingSoon("Restore") },
                onUpdateCatalog = onUpdateCatalog,
                onSeedSampleData = if (BuildConfig.DEBUG) {
                    {
                        scope.launch {
                            SampleData.seed(catalog, collection)
                            viewModel.refresh()
                        }
                    }
                } else null,
            ),
            updatingCatalog = updatingCatalog,
            catalogRevision = catalogRevision,
            snackbarHostState = snackbarHostState,
            onOpen = { selected = it },
        )
    } else {
        BackHandler { selected = null }
        val detailViewModel = viewModel<PrintingDetailViewModel>(
            initializer = { PrintingDetailViewModel { printing, location -> PrintingDetail(printing, collection, catalog, location) } },
        )
        PrintingDetailScreen(detailViewModel, current, viewModel.filter.location, onBack = { selected = null })
    }
}
