package aman.catalogtest.ui.main

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import aman.catalog.audio.ConfigChangeResult
import aman.catalog.audio.ScanResult
import aman.catalogtest.CatalogViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DebugDrawerContent(viewModel: CatalogViewModel) {
    val config               by viewModel.config.collectAsState()
    val tracks               by viewModel.tracks.collectAsState()
    val folders              by viewModel.folders.collectAsState()
    val isScanning           by viewModel.isScanning.collectAsState()
    val lastScanResult       by viewModel.lastScanResult.collectAsState()
    val lastConfigChangeResult by viewModel.lastConfigChangeResult.collectAsState()
    val context = LocalContext.current

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                Toast.makeText(context, "SAF folder permission granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showIgnoreFolderPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Debug Tools", style = MaterialTheme.typography.titleLarge)
        Text(
            "Library: ${tracks.size} tracks indexed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        DrawerSection(title = "Library") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick  = { viewModel.scan(force = false) },
                    modifier = Modifier.weight(1f)
                ) { Text("Scan") }
                Button(
                    onClick  = { viewModel.scan(force = true) },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) { Text("Force Scan") }
            }
            Button(
                onClick  = { viewModel.clear() },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("Clear Database") }
        }

        HorizontalDivider()

        DrawerSection(title = "Scan Status") {
            if (isScanning) {
                Text(
                    "Scanning in progress...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val scanResult = lastScanResult
            if (scanResult != null) {
                Text(
                    "Last scan: ${scanResult.durationMs}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${scanResult.newTracks} added · ${scanResult.changedTracks} changed · " +
                    "${scanResult.movedTracks} moved · ${scanResult.deletedTracks} deleted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!isScanning) {
                EmptyHint("No scan recorded yet")
            }
        }

        HorizontalDivider()

        DrawerSection(title = "Config Change") {
            val configResult = lastConfigChangeResult
            if (configResult != null) {
                Text(
                    "Last config change: ${configResult.durationMs}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${configResult.tracksProcessed} tracks processed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                EmptyHint("No config change recorded yet")
            }
        }

        HorizontalDivider()

        DrawerSection(title = "Storage Access Framework") {
            Text(
                "Grant folder-level write permission for silent tag editing without per-file prompts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick  = { safLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Grant SAF Folder Permission") }
        }

        HorizontalDivider()

        DrawerSection(title = "Configuration") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Min track duration", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${config.minDurationMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = {
                    viewModel.updateMinDuration(
                        if (config.minDurationMs == 10_000L) 0L else 10_000L
                    )
                }) {
                    Text(if (config.minDurationMs == 0L) "Set 10s" else "Set 0s")
                }
            }
        }

        HorizontalDivider()

        DrawerSection(title = "Custom Splitters") {
            AddItemRow(
                placeholder = "e.g. '+', 'feat.'",
                onAdd       = { viewModel.addCustomSplitter(it) }
            )
            if (config.customSplitters.isEmpty()) {
                EmptyHint("No custom splitters")
            } else {
                config.customSplitters.forEach { splitter ->
                    RemovableItem(
                        label    = splitter,
                        onRemove = { viewModel.removeCustomSplitter(splitter) }
                    )
                }
            }
        }

        HorizontalDivider()

        DrawerSection(title = "Split Exceptions") {
            Text(
                "Artist names that should never be split, even if they contain a splitter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            AddItemRow(
                placeholder = "e.g. 'AC/DC'",
                onAdd       = { viewModel.addSplitException(it) }
            )
            if (config.splitExceptions.isEmpty()) {
                EmptyHint("No exceptions")
            } else {
                config.splitExceptions.forEach { exception ->
                    RemovableItem(
                        label    = exception,
                        onRemove = { viewModel.removeSplitException(exception) }
                    )
                }
            }
        }

        HorizontalDivider()

        DrawerSection(title = "Ignored Folders") {
            Text(
                "Tracks in ignored folders are excluded from the library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick  = { showIgnoreFolderPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Ignored Folder…") }

            if (config.scannerIgnores.isEmpty()) {
                EmptyHint("No folders ignored")
            } else {
                config.scannerIgnores.forEach { path ->
                    RemovableItem(
                        label    = path.substringAfterLast("/"),
                        sublabel = path,
                        onRemove = { viewModel.removeIgnoredFolder(path) }
                    )
                }
            }
        }

        HorizontalDivider()

        DrawerSection(title = "Test Logging") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick  = { Log.d("DebugDrawer", "Test debug log message") },
                    modifier = Modifier.weight(1f)
                ) { Text("Log.d") }
                Button(
                    onClick  = { Log.e("DebugDrawer", "Test error log message") },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor   = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("Log.e") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showIgnoreFolderPicker) {
        val alreadyIgnored = config.scannerIgnores.toSet()
        val available      = folders.filter { it.path !in alreadyIgnored }

        AlertDialog(
            onDismissRequest = { showIgnoreFolderPicker = false },
            title            = { Text("Pick a Folder to Ignore") },
            text             = {
                if (available.isEmpty()) {
                    Text(
                        "All known folders are already ignored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(available, key = { it.path }) { folder ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addIgnoredFolder(folder.path)
                                        showIgnoreFolderPicker = false
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    folder.name,
                                    style    = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    folder.path,
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIgnoreFolderPicker = false }) { Text("Cancel") }
            }
        )
    }
}
