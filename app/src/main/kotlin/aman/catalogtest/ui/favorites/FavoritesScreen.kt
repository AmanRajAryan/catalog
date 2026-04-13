package aman.catalogtest.ui.favorites

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.ContextualSortOption
import aman.catalog.audio.models.Track
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.tracks.TrackItemRow
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import aman.catalogtest.ui.components.MosaicArt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

// === Helpers ============================

private fun formatDuration(totalMs: Long): String {
    val totalSeconds = totalMs / 1000
    val hours        = totalSeconds / 3600
    val minutes      = (totalSeconds % 3600) / 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else        -> "<1m"
    }
}

// === Screen =============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    onNavigateToEditor: (Long) -> Unit = {}
) {
    val favorites     by viewModel.favorites.collectAsState()
    val favoritesInfo by viewModel.favoritesInfo.collectAsState()
    val mosaicPaths   by viewModel.favoritesMosaicPaths.collectAsState()
    val currentSort   by viewModel.favoritesSort.collectAsState()

    // Local list for optimistic drag reordering
    var visibleItems by remember(favorites) { mutableStateOf(favorites) }

    var showSortMenu  by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val isUserDefined = currentSort == ContextualSortOption.FavoriteTrack.USER_DEFINED

    var dragFrom by remember { mutableStateOf(-1) }
    var dragTo   by remember { mutableStateOf(-1) }

    val lazyListState    = rememberLazyListState()
    // The LazyColumn has one header item before the tracks, so the reorderable
    // library's indices are offset by 1 relative to visibleItems. Subtract 1.
    val headerOffset = 1

    val reorderableState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
        if (isUserDefined) {
            val fromIndex = from.index - headerOffset
            val toIndex   = to.index   - headerOffset
            if (dragFrom == -1) dragFrom = fromIndex
            dragTo       = toIndex
            visibleItems = visibleItems.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    // Commit drag to DB once gesture ends
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && dragFrom != -1 && dragTo != -1) {
            val movedTrack = visibleItems[dragTo]
            viewModel.moveFavoriteEntry(movedTrack.id, dragFrom, dragTo)
            dragFrom = -1
            dragTo   = -1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Favourites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions        = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.List, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ContextualSortOption.FavoriteTrack.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = {
                                        viewModel.setFavoritesSort(option)
                                        showSortMenu = false
                                    },
                                    trailingIcon = if (option == currentSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (visibleItems.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier           = Modifier.size(48.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text  = "No favourites yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = "Tap the heart on any track to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state    = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        FavoritesHeader(
                            trackCount    = favoritesInfo.trackCount,
                            totalDuration = favoritesInfo.totalDurationMs,
                            coverArtPath  = favoritesInfo.coverArtPath,
                            coverArtPaths = mosaicPaths
                        )
                    }

                    items(
                        items = visibleItems,
                        key   = { track -> track.id }
                    ) { track ->
                        ReorderableItem(reorderableState, key = track.id) { _ ->
                            TrackItemRow(
                                track           = track,
                                onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                                onClick         = { selectedTrack = track },
                                trailingContent = if (isUserDefined) {
                                    {
                                        Row {
                                            IconButton(onClick = { viewModel.toggleFavorite(track.id) }) {
                                                Icon(
                                                    imageVector        = Icons.Filled.Favorite,
                                                    contentDescription = "Unfavourite",
                                                    tint               = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                modifier = Modifier.draggableHandle(),
                                                onClick  = {}
                                            ) {
                                                Icon(
                                                    imageVector        = Icons.Default.Menu,
                                                    contentDescription = "Reorder",
                                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            selectedTrack?.let { track ->
                TrackOptionsDialog(
                    track              = track,
                    viewModel          = viewModel,
                    onDismiss          = { selectedTrack = null },
                    onNavigateToEditor = onNavigateToEditor
                )
            }
        }
    }
}

// === Header ========================

@Composable
private fun FavoritesHeader(
    trackCount:    Int,
    totalDuration: Long,
    coverArtPath:  ArtPath?,
    coverArtPaths: List<ArtPath>
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // single image
        Box(
            modifier         = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (coverArtPath != null) {
                AsyncImage(
                    model              = "${coverArtPath.path}?t=${coverArtPath.dateModified}",
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector        = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // mosaic
        MosaicArt(
            paths    = coverArtPaths,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text       = "Favourites",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "$trackCount tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (totalDuration > 0L) {
                Text(
                    text  = formatDuration(totalDuration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
