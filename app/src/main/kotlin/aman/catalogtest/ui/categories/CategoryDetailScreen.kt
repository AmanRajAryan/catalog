package aman.catalogtest.ui.categories

import aman.catalog.audio.models.Track
import aman.catalog.audio.models.ContextualSortOption
import aman.catalog.audio.models.Folder
import aman.catalog.audio.models.Genre
import aman.catalog.audio.models.Composer
import aman.catalog.audio.models.Lyricist
import aman.catalog.audio.models.Year
import aman.catalogtest.CatalogViewModel
import aman.catalog.audio.models.ArtPath
import aman.catalogtest.ui.components.MatchedAlbumRow
import aman.catalogtest.ui.components.MatchedGenreChip
import aman.catalogtest.ui.tracks.TrackItemRow
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import aman.catalogtest.ui.components.MosaicArt
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    viewModel: CatalogViewModel,
    category: String,
    numericId: Long,
    stringId: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (id: Long) -> Unit = {},
    onNavigateToAlbumArtist: (id: Long) -> Unit = {},
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onNavigateToEditor: (Long) -> Unit = {}
) {
    // Default track sort: TRACK_NUMBER_ASC for folder (album-like view), TITLE_ASC for others
    val defaultTrackSort = if (category == "folder") ContextualSortOption.Track.TRACK_NUMBER_ASC
                           else ContextualSortOption.Track.TITLE_ASC
    var trackSort by remember(category) { mutableStateOf(defaultTrackSort) }
    var albumSort by remember { mutableStateOf(ContextualSortOption.Album.TITLE_ASC) }
    var genreSort by remember { mutableStateOf(ContextualSortOption.Genre.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val tracksFlow = remember(category, numericId, stringId, trackSort) {
        viewModel.getTracksForCategory(category, numericId, stringId, trackSort)
    }
    val tracks by tracksFlow.collectAsState(initial = emptyList())

    val statsFlow = remember(category, numericId, stringId) {
        when (category) {
            "genre"    -> viewModel.getGenreById(numericId)
            "composer" -> viewModel.getComposerById(numericId)
            "lyricist" -> viewModel.getLyricistById(numericId)
            "year"     -> viewModel.getYearByValue(numericId.toInt())
            "folder"   -> viewModel.getFolderByPath(stringId)
            else       -> flowOf(null)
        }
    }
    val statsEntity by statsFlow.collectAsState(initial = null)

    data class Stats(val playCount: Int, val lastPlayed: Long, val totalPlayTimeMs: Long, val extra: String = "")

    val stats: Stats? = remember(statsEntity) {
        when (val e = statsEntity) {
            is Genre    ->
                Stats(e.playCount, e.lastPlayed, e.totalPlayTimeMs,
                    "${e.trackCount} tracks · ${e.albumCount} albums · ${e.totalDurationMs.toHumanDuration()}")
            is Composer ->
                Stats(e.playCount, e.lastPlayed, e.totalPlayTimeMs,
                    "${e.trackCount} tracks · ${e.albumCount} albums · ${e.totalDurationMs.toHumanDuration()}")
            is Lyricist ->
                Stats(e.playCount, e.lastPlayed, e.totalPlayTimeMs,
                    "${e.trackCount} tracks · ${e.albumCount} albums · ${e.totalDurationMs.toHumanDuration()}")
            is Year     ->
                Stats(e.playCount, e.lastPlayed, e.totalPlayTimeMs,
                    "${e.trackCount} tracks · ${e.albumCount} albums · ${e.totalDurationMs.toHumanDuration()}")
            is Folder   ->
                Stats(e.playCount, e.lastPlayed, e.totalPlayTimeMs,
                    "${e.trackCount} tracks · ${e.albumCount} albums · ${e.totalDurationMs.toHumanDuration()}")
            else -> null
        }
    }

    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val mosaicFlow = remember(category, numericId, stringId) {
        when (category) {
            "genre"    -> viewModel.getMosaicForGenreFlow(numericId)
            "composer" -> viewModel.getMosaicForComposerFlow(numericId)
            "lyricist" -> viewModel.getMosaicForLyricistFlow(numericId)
            "year"     -> viewModel.getMosaicForYearFlow(numericId.toInt())
            "folder"   -> viewModel.getMosaicForFolderFlow(stringId)
            else       -> flowOf(emptyList<ArtPath>())
        }
    }
    val mosaicPaths by mosaicFlow.collectAsState(initial = emptyList())

    val categoryGenresFlow = remember(category, numericId, stringId, genreSort) {
        when (category) {
            "composer" -> viewModel.getGenresForComposer(numericId, genreSort)
            "lyricist" -> viewModel.getGenresForLyricist(numericId, genreSort)
            "year"     -> viewModel.getGenresForYear(numericId.toInt(), genreSort)
            "folder"   -> viewModel.getGenresForFolder(stringId, genreSort)
            else       -> flowOf(emptyList())
        }
    }
    val categoryGenres by categoryGenresFlow.collectAsState(initial = emptyList())

    val matchedAlbumsFlow = remember(category, numericId, stringId, albumSort) {
        when (category) {
            "genre"    -> viewModel.getAlbumsForGenre(numericId, albumSort)
            "composer" -> viewModel.getAlbumsForComposer(numericId, albumSort)
            "lyricist" -> viewModel.getAlbumsForLyricist(numericId, albumSort)
            "year"     -> viewModel.getAlbumsForYear(numericId.toInt(), albumSort)
            "folder"   -> viewModel.getAlbumsForFolder(stringId, albumSort)
            else       -> flowOf(emptyList())
        }
    }
    val matchedAlbums by matchedAlbumsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title          = {
                    Column {
                        Text(title, maxLines = 1)
                        Text(
                            text  = "${tracks.size} tracks",
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
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.List, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text    = { Text("— Tracks —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                onClick = {},
                                enabled = false
                            )
                            ContextualSortOption.Track.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = { trackSort = option; showSortMenu = false },
                                    trailingIcon = if (option == trackSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text    = { Text("— Albums —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                onClick = {},
                                enabled = false
                            )
                            ContextualSortOption.Album.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = { albumSort = option; showSortMenu = false },
                                    trailingIcon = if (option == albumSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text    = { Text("— Genres —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                onClick = {},
                                enabled = false
                            )
                            ContextualSortOption.Genre.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = { genreSort = option; showSortMenu = false },
                                    trailingIcon = if (option == genreSort) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (stats != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val coverArtPath = when (val e = statsEntity) {
                        is Folder   -> e.coverArtPath
                        is Year     -> e.coverArtPath
                        is Genre    -> e.coverArtPath
                        is Composer -> e.coverArtPath
                        is Lyricist -> e.coverArtPath
                        else -> null
                    }
                    if (coverArtPath != null || mosaicPaths.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            AsyncImage(
                                model              = coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                                contentDescription = "Single cover",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp))
                            )
                            MosaicArt(
                                paths    = mosaicPaths,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                    if (stats.extra.isNotBlank()) {
                        Text(text = stats.extra, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (category == "folder") {
                        Text(text = stringId, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (categoryGenres.isNotEmpty()) {
                        LazyRow(
                            contentPadding        = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(categoryGenres, key = { "cg_${it.genre.id}" }) { matchedGenre ->
                                MatchedGenreChip(
                                    matchedGenre = matchedGenre,
                                    getMosaic    = { viewModel.getMosaicForGenre(matchedGenre.genre.id) },
                                    onClick      = { onNavigateToCategory("genre", matchedGenre.genre.id.toString(), matchedGenre.genre.name.ifBlank { "Unknown Genre" }) }
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Text(
                        text  = "${stats.playCount.toPlayCountString() ?: "Never played"} · ${stats.totalPlayTimeMs.toHumanDuration()} listened · Last: ${stats.lastPlayed.toRelativeTimeString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider()
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {

                if (matchedAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text     = "Albums (${matchedAlbums.size})",
                            style    = MaterialTheme.typography.titleSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(items = matchedAlbums, key = { "matched_album_${it.album.id}" }) { matchedAlbum ->
                        MatchedAlbumRow(
                            matchedAlbum = matchedAlbum,
                            onClick      = { onNavigateToAlbum(matchedAlbum.album.id) }
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    item {
                        Text(
                            text     = "Tracks (${tracks.size})",
                            style    = MaterialTheme.typography.titleSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (tracks.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = "No tracks found for this $category.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(items = tracks, key = { it.id }) { track ->
                        TrackItemRow(
                            track           = track,
                            onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                            onClick         = { selectedTrack = it }
                        )
                    }
                }
            }
        }
    }

    selectedTrack?.let { track ->
        TrackOptionsDialog(
            track                   = track,
            viewModel               = viewModel,
            onDismiss               = { selectedTrack = null },
            onNavigateToEditor      = onNavigateToEditor,
            onNavigateToArtist      = onNavigateToArtist,
            onNavigateToAlbumArtist = onNavigateToAlbumArtist,
            onNavigateToAlbum       = onNavigateToAlbum,
            onNavigateToCategory    = onNavigateToCategory
        )
    }
}

