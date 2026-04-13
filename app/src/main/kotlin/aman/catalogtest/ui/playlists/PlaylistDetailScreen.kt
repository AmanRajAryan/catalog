package aman.catalogtest.ui.playlists

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.ContextualSortOption
import aman.catalog.audio.models.Playlist
import aman.catalog.audio.models.PlaylistItem
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toRelativeTimeString
import aman.catalogtest.ui.tracks.TrackItemRow
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import aman.catalogtest.ui.components.MosaicArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: CatalogViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    onNavigateToEditor: (Long) -> Unit = {}
) {
    val currentSort  by viewModel.playlistTrackSort.collectAsState()
    val allItems by viewModel.getPlaylistItems(playlistId, currentSort).collectAsState(initial = emptyList())

    // Local visible list — we optimistically remove items here before committing
    // to the database, so the UI feels instant and undo is possible.
    var visibleItems by remember(allItems) { mutableStateOf(allItems) }

    var isEditMode by remember { mutableStateOf(false) }

    val isUserDefined = currentSort == ContextualSortOption.PlaylistTrack.USER_DEFINED
    var showSortMenu  by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val context           = LocalContext.current

    var pendingRemoveJob by remember { mutableStateOf<Job?>(null) }

    val playlist     by viewModel.getPlaylist(playlistId).collectAsState(initial = null)
    val playlistName = playlist?.name ?: "Playlist"

    val mosaicPaths by remember(playlistId) { viewModel.getMosaicForPlaylistFlow(playlistId) }
        .collectAsState(initial = emptyList())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val tempFile = java.io.File(context.cacheDir, "export_temp.m3u")
            val success = viewModel.exportPlaylist(playlistId, tempFile)
            if (success) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { it.copyTo(out) }
                }
                snackbarHostState.showSnackbar("Exported \"${playlistName}\"")
            } else {
                snackbarHostState.showSnackbar("Export failed")
            }
            tempFile.delete()
        }
    }

    fun onRemoveEntry(entryId: Long) {
        pendingRemoveJob?.cancel()

        val removedItem: PlaylistItem = visibleItems.find { it.entryId == entryId } ?: return
        val removedIndex: Int         = visibleItems.indexOfFirst { it.entryId == entryId }

        visibleItems = visibleItems.filter { it.entryId != entryId }

        pendingRemoveJob = scope.launch {
            val result = snackbarHostState.showSnackbar(
                message     = "\"${removedItem.track.title}\" removed",
                actionLabel = "Undo",
                duration    = SnackbarDuration.Short
            )

            when (result) {
                SnackbarResult.ActionPerformed -> {
                    visibleItems = visibleItems.toMutableList().also {
                        it.add(removedIndex.coerceAtMost(it.size), removedItem)
                    }
                }
                SnackbarResult.Dismissed -> {
                    viewModel.removeEntryFromPlaylist(entryId)
                }
            }
        }
    }

    Scaffold(
        topBar       = {
            TopAppBar(
                title          = {
                    Column {
                        Text(playlistName, maxLines = 1)
                        Text(
                            text  = "${visibleItems.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions        = {
                    if (visibleItems.isNotEmpty()) {
                        IconButton(onClick = {
                            val trackIdToClone = visibleItems.first().track.id
                            viewModel.stressTestPlaylist(playlistId, trackIdToClone, 1000)
                        }) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Stress Test")
                        }
                    }
                    IconButton(onClick = {
                        exportLauncher.launch("${playlistName}.m3u")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export playlist")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.List, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ContextualSortOption.PlaylistTrack.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = {
                                        viewModel.setPlaylistTrackSort(option)
                                        showSortMenu = false
                                    },
                                    trailingIcon = if (option == currentSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                    TextButton(onClick = {
                        if (isEditMode) viewModel.savePlaylistOrder(playlistId, visibleItems)
                        isEditMode = !isEditMode
                    }) {
                        Text(if (isEditMode) "Save" else "Edit")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            var selectedItem by remember { mutableStateOf<PlaylistItem?>(null) }

            if (visibleItems.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "This playlist is empty.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val lazyListState = rememberLazyListState()

                var dragFrom by remember { mutableStateOf(-1) }
                var dragTo   by remember { mutableStateOf(-1) }

                // Header item is at index 0, so track indices are offset by 1
                val headerOffset = 1

                val reorderableState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
                    val fromIndex = from.index - headerOffset
                    val toIndex   = to.index   - headerOffset
                    if (dragFrom == -1) dragFrom = fromIndex
                    dragTo   = toIndex
                    visibleItems = visibleItems.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                }

                LaunchedEffect(reorderableState.isAnyItemDragging) {
                    if (!reorderableState.isAnyItemDragging && dragFrom != -1 && dragTo != -1) {
                        if (!isEditMode) {
                            val entryId = visibleItems[dragTo].entryId
                            viewModel.moveEntryInPlaylist(playlistId, entryId, dragFrom, dragTo)
                        }
                        dragFrom = -1
                        dragTo   = -1
                    }
                }

                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                    item {
                        playlist?.let {
                            PlaylistDetailHeader(playlist = it, mosaicPaths = mosaicPaths)
                        }
                    }

                    items(items = visibleItems, key = { item -> item.entryId }) { item ->
                        ReorderableItem(reorderableState, key = item.entryId) { _ ->
                            TrackItemRow(
                                track           = item.track,
                                onFavoriteClick = { viewModel.toggleFavorite(item.track.id) },
                                onClick         = { selectedItem = item },
                                trailingContent = {
                                    if (isUserDefined) {
                                        IconButton(modifier = Modifier.draggableHandle(), onClick = {}) {
                                            Icon(
                                                imageVector        = Icons.Default.Menu,
                                                contentDescription = "Reorder",
                                                tint               = if (isEditMode) MaterialTheme.colorScheme.primary
                                                                     else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            selectedItem?.let { item ->
                TrackOptionsDialog(
                    track              = item.track,
                    viewModel          = viewModel,
                    onDismiss          = { selectedItem = null },
                    entryId            = item.entryId,
                    onNavigateToEditor = onNavigateToEditor
                )
            }
        }
    }
}

// === Playlist detail header ===================

@Composable
private fun PlaylistDetailHeader(playlist: Playlist, mosaicPaths: List<ArtPath>) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncImage(
            model              = playlist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
            contentDescription = "Single cover",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        MosaicArt(
            paths    = mosaicPaths,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text       = playlist.name,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines   = 2
            )
            Text(
                text  = "${playlist.trackCount} tracks · ${playlist.totalDurationMs.toHumanDuration()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "Created ${playlist.dateCreated.toRelativeTimeString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "Modified ${playlist.dateModified.toRelativeTimeString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
