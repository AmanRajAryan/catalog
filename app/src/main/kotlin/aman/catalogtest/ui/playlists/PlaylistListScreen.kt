package aman.catalogtest.ui.playlists

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.Playlist
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.util.toHumanDuration
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import aman.catalogtest.ui.components.MosaicArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    playlists: List<Playlist>,
    viewModel: CatalogViewModel,
    onItemClick: (id: Long, name: String) -> Unit,
    onCreatePlaylist: (name: String) -> Unit,
    onDeletePlaylist: (id: Long) -> Unit,
    onRenamePlaylist: (id: Long, newName: String) -> Unit,
    onImportPlaylist: suspend (java.io.File) -> Long
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val context           = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "import_temp.m3u"
            val tempFile = java.io.File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { input.copyTo(it) }
            }
            val result = onImportPlaylist(tempFile)
            tempFile.delete()
            when {
                result > 0L  -> snackbarHostState.showSnackbar("Playlist imported")
                result == 0L -> snackbarHostState.showSnackbar("No matching tracks found")
                else         -> snackbarHostState.showSnackbar("Import failed")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("Playlists (${playlists.size})") },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Import playlist")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        Button(
            onClick  = { showCreateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("New Playlist")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = playlists, key = { it.id }) { playlist ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(playlist.id, playlist.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val mosaicPaths by produceState(emptyList<ArtPath>(), playlist.id) {
                            value = viewModel.getMosaicForPlaylist(playlist.id)
                        }
                        AsyncImage(
                            model              = playlist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                        )
                        MosaicArt(
                            paths    = mosaicPaths,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = playlist.name, style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text  = "${playlist.trackCount} tracks · ${playlist.totalDurationMs.toHumanDuration()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick        = { playlistToRename = playlist },
                            modifier       = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Rename", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick        = { playlistToDelete = playlist },
                            modifier       = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors         = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title            = { Text("New Playlist") },
            text             = {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton    = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = { onCreatePlaylist(name.trim()); showCreateDialog = false }
                ) { Text("Create") }
            },
            dismissButton    = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    playlistToRename?.let { playlist ->
        var name by remember(playlist.id) { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title            = { Text("Rename Playlist") },
            text             = {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("New name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton    = {
                Button(
                    enabled = name.isNotBlank() && name != playlist.name,
                    onClick = { onRenamePlaylist(playlist.id, name.trim()); playlistToRename = null }
                ) { Text("Rename") }
            },
            dismissButton    = {
                TextButton(onClick = { playlistToRename = null }) { Text("Cancel") }
            }
        )
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title            = { Text("Delete Playlist") },
            text             = { Text("Delete \"${playlist.name}\"? This cannot be undone.") },
            confirmButton    = {
                Button(
                    onClick = { onDeletePlaylist(playlist.id); playlistToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton    = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
            }
        )
    }
    }
}
