package tcg.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tcg.app.catalog.GitHubCatalogSource
import tcg.app.catalog.summary
import tcg.app.scanning.CachePhotoSource
import tcg.app.scanning.SharedPreferencesLanguageStore
import tcg.app.storage.AppDatabase
import tcg.app.ui.BackupViewModel
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
import tcg.domain.backup.CollectionBackup
import tcg.domain.collection.CollectionExport
import tcg.domain.collection.CollectionList
import tcg.domain.collection.PrintingDetail
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var updating by mutableStateOf(false)
    private var catalogRevision by mutableIntStateOf(0)
    private val catalogMessages = SnackbarHostState()

    private val exportTarget = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportCsv(uri)
    }
    private val backupTarget = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) saveBackup(uri)
    }
    private val restoreSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackupFile(uri)
    }
    private val database get() = (application as TcgApplication).database

    private val backupViewModel by viewModels<BackupViewModel> {
        viewModelFactory {
            initializer { BackupViewModel(CollectionBackup(database.collectionStore(), database.backupStatusStore())) }
        }
    }

    private val catalogUpdater by lazy {
        CatalogUpdater(
            source = GitHubCatalogSource(BuildConfig.CATALOG_REPO, BuildConfig.CATALOG_TOKEN),
            store = database.catalogStore(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) updateCatalog()
        setContent {
            MaterialTheme {
                CollectionApp(
                    database = database,
                    backup = backupViewModel,
                    updatingCatalog = updating,
                    catalogRevision = catalogRevision,
                    snackbarHostState = catalogMessages,
                    onUpdateCatalog = ::updateCatalog,
                    onExportCsv = { exportTarget.launch("tcg-collection-${LocalDate.now()}.csv") },
                    onBackup = { backupTarget.launch("tcg-collection-backup-${LocalDate.now()}.json") },
                    onRestore = { restoreSource.launch(arrayOf("*/*")) },
                )
            }
        }
    }

    private fun saveBackup(uri: Uri) {
        lifecycleScope.launch {
            val message = try {
                backupViewModel.saveBackup { content ->
                    withContext(Dispatchers.IO) {
                        val stream = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
                        stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                    }
                }
                "Backup saved"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Backup failed: ${e.message ?: "could not write the file"}"
            }
            catalogMessages.showSnackbar(message)
        }
    }

    private fun readBackupFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val stream = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
                    val bytes = stream.use { it.readNBytes(MAX_BACKUP_BYTES + 1) }
                    if (bytes.size > MAX_BACKUP_BYTES) throw IOException("The file is too large to be a backup")
                    // Strict decoding: a damaged file must fail here, not restore with substituted characters.
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                }
                backupViewModel.onFileRead(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CharacterCodingException) {
                backupViewModel.onFileUnreadable("The file is damaged: it is not valid text.")
            } catch (e: Exception) {
                backupViewModel.onFileUnreadable(e.message ?: "The file could not be read.")
            }
        }
    }

    private fun exportCsv(uri: Uri) {
        lifecycleScope.launch {
            val message = try {
                val csv = CollectionExport(database.collectionStore(), database.catalogStore()).csv()
                withContext(Dispatchers.IO) {
                    val stream = contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                }
                "Exported the Collection as CSV"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "CSV export failed: ${e.message ?: "could not write the file"}"
            }
            catalogMessages.showSnackbar(message)
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

private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

@Composable
private fun CollectionApp(
    database: AppDatabase,
    backup: BackupViewModel,
    updatingCatalog: Boolean,
    catalogRevision: Int,
    snackbarHostState: SnackbarHostState,
    onUpdateCatalog: () -> Unit,
    onExportCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var selected by rememberSaveable(stateSaver = printingSaver) { mutableStateOf<PrintingIdentifier?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalog = remember { database.catalogStore() }
    val collection = remember { database.collectionStore() }
    val context = LocalContext.current

    val photos = remember { CachePhotoSource(context.applicationContext) }
    val languages = remember { SharedPreferencesLanguageStore(context) }
    val debugLog = (context.applicationContext as TcgApplication).scanDebugLog

    if (scanning) {
        val scanViewModel = viewModel<ScanViewModel>(initializer = { ScanViewModel(catalog, collection, photos, languages, debugLog) })
        val close = {
            if (scanViewModel.requestEnd()) scanning = false
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
            backup = backup,
            actions = CollectionActions(
                onAdd = { scanning = true },
                onExportCsv = onExportCsv,
                onBackup = onBackup,
                onRestore = onRestore,
                onUpdateCatalog = onUpdateCatalog,
                onSeedSampleData = if (BuildConfig.DEBUG) {
                    {
                        scope.launch {
                            SampleData.seed(catalog, collection)
                            viewModel.refresh()
                            backup.refreshReminder()
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
